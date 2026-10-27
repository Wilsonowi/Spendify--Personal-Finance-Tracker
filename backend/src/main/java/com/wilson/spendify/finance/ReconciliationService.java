package com.wilson.spendify.finance;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReconciliationService {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    private static final int DATE_ONLY_SETTLEMENT_DAYS = 2;

    private final FinanceTransactionRepository transactions;
    private final FinancialAccountRepository accounts;
    private final TransferCandidateRepository candidates;

    public ReconciliationService(FinanceTransactionRepository transactions,
                                 FinancialAccountRepository accounts,
                                 TransferCandidateRepository candidates) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.candidates = candidates;
    }

    @Transactional
    public void proposeFor(FinanceTransaction transaction) {
        candidateMatches(transaction, false).forEach(other -> propose(transaction, other));
    }

    @Transactional
    public java.util.List<TransferCandidate> list(UUID userId) {
        java.util.List<TransferCandidate> all = candidates.findAllByUserIdOrderByCreatedAtDesc(userId);
        rejectStaleProposals(all);
        rejectInvalidDateOnlyProposals(userId, all);
        refreshPendingTransfers(userId);
        java.util.List<TransferCandidate> refreshed = candidates.findAllByUserIdOrderByCreatedAtDesc(userId);
        rejectStaleProposals(refreshed);
        rejectInvalidDateOnlyProposals(userId, refreshed);
        return refreshed.stream().filter(item -> item.getStatus() != TransferStatus.REJECTED).toList();
    }

    @Transactional
    public TransferCandidate confirm(UUID userId, UUID candidateId) {
        TransferCandidate candidate = requireOwned(userId, candidateId);
        if (candidate.getStatus() != TransferStatus.PROPOSED) {
            throw new IllegalStateException("Only proposed transfers can be confirmed.");
        }
        java.util.List<TransferCandidate> all = candidates.findAllByUserIdOrderByCreatedAtDesc(userId);
        rejectStaleProposals(all);
        if (candidate.getStatus() == TransferStatus.REJECTED) return candidate;
        FinanceTransaction debit = transactions.findByIdAndUserId(candidate.getDebitTransactionId(), userId)
                .orElseThrow();
        FinanceTransaction credit = transactions.findByIdAndUserId(candidate.getCreditTransactionId(), userId)
                .orElseThrow();
        debit.markTransfer(ResolutionMethod.USER_TRANSFER);
        credit.markTransfer(ResolutionMethod.USER_TRANSFER);
        candidate.confirm();
        rejectCompetingProposals(all, candidate);
        return candidate;
    }

    @Transactional
    public TransferCandidate reject(UUID userId, UUID candidateId) {
        TransferCandidate candidate = requireOwned(userId, candidateId);
        if (candidate.getStatus() != TransferStatus.PROPOSED) {
            throw new IllegalStateException("Only proposed transfers can be rejected.");
        }
        candidate.reject();
        return candidate;
    }

    @Transactional
    public boolean autoReconcile(FinanceTransaction transaction) {
        if (transaction.getKind() != TransactionKind.TRANSFER_PENDING) return false;
        java.util.List<FinanceTransaction> matches = eligiblePendingMatches(transaction);
        if (matches.size() != 1) {
            matches.forEach(other -> propose(transaction, other));
            return false;
        }
        FinanceTransaction other = matches.getFirst();
        java.util.List<FinanceTransaction> reverse = eligiblePendingMatches(other);
        if (reverse.size() != 1 || !reverse.getFirst().getId().equals(transaction.getId())) {
            propose(transaction, other);
            return false;
        }
        FinanceTransaction debit = transaction.getDirection() == Direction.DEBIT ? transaction : other;
        FinanceTransaction credit = transaction.getDirection() == Direction.CREDIT ? transaction : other;
        TransferCandidate candidate = candidates
                .findByDebitTransactionIdAndCreditTransactionId(debit.getId(), credit.getId())
                .orElseGet(() -> createCandidate(transaction, other));
        if (candidate == null || candidate.getStatus() != TransferStatus.PROPOSED) return false;
        candidate.confirm();
        candidates.save(candidate);
        debit.markTransfer();
        credit.markTransfer();
        rejectCompetingProposals(candidates.findAllByUserIdOrderByCreatedAtDesc(transaction.getUserId()), candidate);
        return true;
    }

    private void propose(FinanceTransaction first, FinanceTransaction second) {
        FinanceTransaction debit = first.getDirection() == Direction.DEBIT ? first : second;
        FinanceTransaction credit = first.getDirection() == Direction.CREDIT ? first : second;
        if (candidates.existsByDebitTransactionIdAndCreditTransactionId(debit.getId(), credit.getId())) return;

        TransferCandidate candidate = createCandidate(first, second);
        if (candidate != null) candidates.save(candidate);
    }

    private TransferCandidate createCandidate(FinanceTransaction first, FinanceTransaction second) {
        FinanceTransaction debit = first.getDirection() == Direction.DEBIT ? first : second;
        FinanceTransaction credit = first.getDirection() == Direction.CREDIT ? first : second;
        FinancialAccount debitAccount = accounts.findById(debit.getAccountId()).orElseThrow();
        FinancialAccount creditAccount = accounts.findById(credit.getAccountId()).orElseThrow();
        int score = 55; // Exact amount and opposite directions are guaranteed by the query.
        StringBuilder evidence = new StringBuilder("Exact amount; opposite directions");

        boolean bankWalletPair = debitAccount.getAccountType() == AccountType.BANK
                && creditAccount.getAccountType() == AccountType.EWALLET;
        if (bankWalletPair) {
            score += 15;
            evidence.append("; bank-to-wallet accounts");
        }
        boolean dateOnly = debit.getDatePrecision() == DatePrecision.DATE_ONLY
                || credit.getDatePrecision() == DatePrecision.DATE_ONLY;
        boolean topUpEvidence = topUpEvidence(debit) || topUpEvidence(credit);
        if (dateOnly && (!bankWalletPair || !topUpEvidence)) return null;
        long minutes = Math.abs(Duration.between(debit.getOccurredAt(), credit.getOccurredAt()).toMinutes());
        if (!dateOnly && minutes <= 30) {
            score += 15;
            evidence.append("; within 30 minutes");
        } else if (dateOnly) {
            long settlementDays = settlementDays(debit, credit);
            if (settlementDays < 0 || settlementDays > DATE_ONLY_SETTLEMENT_DAYS) return null;
            score += settlementDays == 0 ? 15 : settlementDays == 1 ? 12 : 8;
            if (settlementDays == 0) {
                evidence.append("; bank posted same day as wallet reload");
            } else {
                evidence.append("; bank posted ").append(settlementDays)
                        .append(settlementDays == 1 ? " day" : " days")
                        .append(" after wallet reload");
            }
            evidence.append("; top-up evidence");
        } else {
            score += 8;
            evidence.append("; within 24 hours");
        }
        if (referencesOverlap(debit.getReference(), credit.getReference())) {
            score += 15;
            evidence.append("; related references");
        }
        if (score >= 70) {
            return new TransferCandidate(first.getUserId(), debit.getId(), credit.getId(),
                    Math.min(100, score), evidence.toString());
        }
        return null;
    }

    private java.util.List<FinanceTransaction> eligiblePendingMatches(FinanceTransaction transaction) {
        return candidateMatches(transaction, true);
    }

    private java.util.List<FinanceTransaction> candidateMatches(FinanceTransaction transaction,
                                                                 boolean pendingOnly) {
        Direction opposite = transaction.getDirection() == Direction.DEBIT ? Direction.CREDIT : Direction.DEBIT;
        MatchWindow window = matchWindow(transaction);
        java.util.List<FinanceTransaction> matches = transactions.findMatchCandidates(
                        transaction.getUserId(), transaction.getAmount(), opposite, window.from(), window.to()).stream()
                .filter(other -> !other.getId().equals(transaction.getId()))
                .filter(other -> !pendingOnly || other.getKind() == TransactionKind.TRANSFER_PENDING)
                .toList();
        boolean dateOnly = transaction.getDatePrecision() == DatePrecision.DATE_ONLY
                || matches.stream().anyMatch(other -> other.getDatePrecision() == DatePrecision.DATE_ONLY);
        if (!dateOnly) return matches;

        java.util.List<FinanceTransaction> eligible = matches.stream()
                .filter(other -> isBankWalletPair(transaction, other))
                .filter(other -> topUpEvidence(transaction) || topUpEvidence(other))
                .filter(other -> validDateOnlySettlement(transaction, other))
                .toList();
        long nearest = eligible.stream().mapToLong(other -> settlementDays(transaction, other)).min()
                .orElse(Long.MAX_VALUE);
        return eligible.stream().filter(other -> settlementDays(transaction, other) == nearest).toList();
    }

    private MatchWindow matchWindow(FinanceTransaction transaction) {
        if (transaction.getDatePrecision() != DatePrecision.DATE_ONLY) {
            Duration window = Duration.ofHours(24);
            return new MatchWindow(transaction.getOccurredAt().minus(window),
                    transaction.getOccurredAt().plus(window));
        }
        LocalDate date = localDate(transaction);
        if (transaction.getDirection() == Direction.CREDIT) {
            return new MatchWindow(date.atStartOfDay(MALAYSIA).toInstant(),
                    date.plusDays(DATE_ONLY_SETTLEMENT_DAYS + 1L).atStartOfDay(MALAYSIA).toInstant().minusNanos(1));
        }
        return new MatchWindow(date.minusDays(DATE_ONLY_SETTLEMENT_DAYS).atStartOfDay(MALAYSIA).toInstant(),
                date.plusDays(1).atStartOfDay(MALAYSIA).toInstant().minusNanos(1));
    }

    private boolean validDateOnlySettlement(FinanceTransaction first, FinanceTransaction second) {
        long days = settlementDays(first, second);
        return days >= 0 && days <= DATE_ONLY_SETTLEMENT_DAYS;
    }

    private long settlementDays(FinanceTransaction first, FinanceTransaction second) {
        FinanceTransaction debit = first.getDirection() == Direction.DEBIT ? first : second;
        FinanceTransaction credit = first.getDirection() == Direction.CREDIT ? first : second;
        return ChronoUnit.DAYS.between(localDate(credit), localDate(debit));
    }

    private LocalDate localDate(FinanceTransaction transaction) {
        return transaction.getOccurredAt().atZone(MALAYSIA).toLocalDate();
    }

    private boolean isBankWalletPair(FinanceTransaction first, FinanceTransaction second) {
        FinanceTransaction debit = first.getDirection() == Direction.DEBIT ? first : second;
        FinanceTransaction credit = first.getDirection() == Direction.CREDIT ? first : second;
        FinancialAccount debitAccount = accounts.findById(debit.getAccountId()).orElseThrow();
        FinancialAccount creditAccount = accounts.findById(credit.getAccountId()).orElseThrow();
        return debitAccount.getAccountType() == AccountType.BANK
                && creditAccount.getAccountType() == AccountType.EWALLET;
    }

    private void rejectStaleProposals(java.util.List<TransferCandidate> all) {
        Set<UUID> matched = new HashSet<>();
        all.stream().filter(item -> item.getStatus() == TransferStatus.CONFIRMED).forEach(item -> {
            matched.add(item.getDebitTransactionId());
            matched.add(item.getCreditTransactionId());
        });
        all.stream().filter(item -> item.getStatus() == TransferStatus.PROPOSED)
                .filter(item -> matched.contains(item.getDebitTransactionId())
                        || matched.contains(item.getCreditTransactionId()))
                .forEach(TransferCandidate::reject);
    }

    private void rejectInvalidDateOnlyProposals(UUID userId, java.util.List<TransferCandidate> all) {
        all.stream().filter(item -> item.getStatus() == TransferStatus.PROPOSED)
                .filter(item -> !isBestPendingDateOnlyPair(userId, item))
                .forEach(TransferCandidate::reject);
    }

    private boolean isBestPendingDateOnlyPair(UUID userId, TransferCandidate candidate) {
        FinanceTransaction debit = transactions.findByIdAndUserId(candidate.getDebitTransactionId(), userId)
                .orElse(null);
        FinanceTransaction credit = transactions.findByIdAndUserId(candidate.getCreditTransactionId(), userId)
                .orElse(null);
        if (debit == null || credit == null) return false;
        boolean dateOnly = debit.getDatePrecision() == DatePrecision.DATE_ONLY
                || credit.getDatePrecision() == DatePrecision.DATE_ONLY;
        boolean pending = debit.getKind() == TransactionKind.TRANSFER_PENDING
                && credit.getKind() == TransactionKind.TRANSFER_PENDING;
        if (!dateOnly || !pending) return true;
        return eligiblePendingMatches(debit).stream().anyMatch(item -> item.getId().equals(credit.getId()))
                && eligiblePendingMatches(credit).stream().anyMatch(item -> item.getId().equals(debit.getId()));
    }

    private void refreshPendingTransfers(UUID userId) {
        transactions.findAllByUserIdOrderByOccurredAtDesc(userId).stream()
                .filter(item -> item.getKind() == TransactionKind.TRANSFER_PENDING)
                .forEach(this::autoReconcile);
    }

    private void rejectCompetingProposals(java.util.List<TransferCandidate> all, TransferCandidate winner) {
        all.stream().filter(item -> !item.getId().equals(winner.getId()))
                .filter(item -> item.getStatus() == TransferStatus.PROPOSED)
                .filter(item -> sharesTransaction(item, winner))
                .forEach(TransferCandidate::reject);
    }

    private boolean sharesTransaction(TransferCandidate left, TransferCandidate right) {
        return left.getDebitTransactionId().equals(right.getDebitTransactionId())
                || left.getDebitTransactionId().equals(right.getCreditTransactionId())
                || left.getCreditTransactionId().equals(right.getDebitTransactionId())
                || left.getCreditTransactionId().equals(right.getCreditTransactionId());
    }

    private boolean topUpEvidence(FinanceTransaction transaction) {
        String value = String.join(" ", transaction.getMerchant() == null ? "" : transaction.getMerchant(),
                transaction.getReviewNote() == null ? "" : transaction.getReviewNote()).toLowerCase(Locale.ROOT);
        return value.contains("top up") || value.contains("top-up") || value.contains("tng")
                || value.contains("wallet") || value.contains("reload");
    }

    private boolean referencesOverlap(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        leftTokens.retainAll(rightTokens);
        return !leftTokens.isEmpty();
    }

    private Set<String> tokens(String value) {
        if (value == null) return new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (String token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 4) tokens.add(token);
        }
        return tokens;
    }

    private TransferCandidate requireOwned(UUID userId, UUID candidateId) {
        return candidates.findByIdAndUserId(candidateId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transfer candidate not found."));
    }

    private record MatchWindow(Instant from, Instant to) { }
}
