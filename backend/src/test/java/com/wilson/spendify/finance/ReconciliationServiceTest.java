package com.wilson.spendify.finance;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationServiceTest {
    @Test
    void proposesHighConfidenceBankToWalletPair() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);

        UUID userId = UUID.randomUUID();
        FinancialAccount bank = new FinancialAccount(userId, "PUBLIC_BANK", "Public Bank", AccountType.BANK);
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        Instant at = Instant.parse("2026-07-10T06:30:00Z");
        FinanceTransaction debit = transaction(userId, bank.getId(), Direction.DEBIT, at, "PB-TNG-7812");
        FinanceTransaction credit = transaction(userId, wallet.getId(), Direction.CREDIT, at.plusSeconds(120), "TNG-7812");
        debit.confirm();
        credit.confirm();

        when(transactions.findMatchCandidates(userId, new BigDecimal("100.00"), Direction.CREDIT,
                at.minusSeconds(86400), at.plusSeconds(86400))).thenReturn(List.of(credit));
        when(accounts.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(candidates.existsByDebitTransactionIdAndCreditTransactionId(debit.getId(), credit.getId()))
                .thenReturn(false);

        service.proposeFor(debit);

        ArgumentCaptor<TransferCandidate> captor = ArgumentCaptor.forClass(TransferCandidate.class);
        verify(candidates).save(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualTo(100);
        assertThat(captor.getValue().getEvidence()).contains("bank-to-wallet", "within 30 minutes");
    }

    @Test
    void proposesDateOnlyTopUpWithinTwoCalendarDays() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        FinancialAccount bank = new FinancialAccount(userId, "PUBLIC_BANK", "Public Bank", AccountType.BANK);
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        Instant walletAt = Instant.parse("2026-07-10T04:00:00Z");
        FinanceTransaction credit = transaction(userId, wallet.getId(), Direction.CREDIT, walletAt, null);
        FinanceTransaction debit = transaction(userId, bank.getId(), Direction.DEBIT,
                walletAt.plusSeconds(2 * 86400), null);
        debit.attachStatementMetadata(1, 1, DatePrecision.DATE_ONLY, null, "Possible TNG top-up");
        credit.attachStatementMetadata(1, 2, DatePrecision.DATE_ONLY, null, "Possible wallet top-up");
        debit.confirm();
        credit.confirm();
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.DEBIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(debit));
        when(accounts.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(candidates.existsByDebitTransactionIdAndCreditTransactionId(debit.getId(), credit.getId()))
                .thenReturn(false);

        service.proposeFor(credit);

        ArgumentCaptor<TransferCandidate> captor = ArgumentCaptor.forClass(TransferCandidate.class);
        verify(candidates).save(captor.capture());
        assertThat(captor.getValue().getEvidence())
                .contains("bank posted 2 days after wallet reload", "top-up evidence");
    }

    @Test
    void doesNotProposeABankPostingBeforeTheWalletReload() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        FinancialAccount bank = new FinancialAccount(userId, "PUBLIC_BANK", "Public Bank", AccountType.BANK);
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        Instant walletAt = Instant.parse("2026-07-12T04:00:00Z");
        FinanceTransaction credit = transaction(userId, wallet.getId(), Direction.CREDIT, walletAt, null);
        FinanceTransaction earlierDebit = transaction(userId, bank.getId(), Direction.DEBIT,
                walletAt.minusSeconds(86400), null);
        credit.attachStatementMetadata(1, 1, DatePrecision.DATE_ONLY, null, "Wallet reload");
        earlierDebit.attachStatementMetadata(1, 2, DatePrecision.DATE_ONLY, null, "TNG top-up");
        credit.markTransferPending();
        earlierDebit.markTransferPending();
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.DEBIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(earlierDebit));
        when(accounts.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));

        service.proposeFor(credit);

        verify(candidates, never()).save(any(TransferCandidate.class));
    }

    @Test
    void automaticallyChoosesTheMutuallyUniqueNearestLaterBankPosting() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        FinancialAccount bank = new FinancialAccount(userId, "PUBLIC_BANK", "Public Bank", AccountType.BANK);
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        Instant walletAt = Instant.parse("2026-07-10T04:00:00Z");
        FinanceTransaction credit = transaction(userId, wallet.getId(), Direction.CREDIT, walletAt, null);
        FinanceTransaction nearestDebit = transaction(userId, bank.getId(), Direction.DEBIT,
                walletAt.plusSeconds(86400), null);
        FinanceTransaction fartherDebit = transaction(userId, bank.getId(), Direction.DEBIT,
                walletAt.plusSeconds(2 * 86400), null);
        for (FinanceTransaction transaction : List.of(credit, nearestDebit, fartherDebit)) {
            transaction.attachStatementMetadata(1, 1, DatePrecision.DATE_ONLY, null, "TNG reload");
            transaction.markTransferPending();
        }
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.DEBIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(fartherDebit, nearestDebit));
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.CREDIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(credit));
        when(accounts.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));

        boolean reconciled = service.autoReconcile(credit);

        assertThat(reconciled).isTrue();
        assertThat(nearestDebit.getKind()).isEqualTo(TransactionKind.TRANSFER);
        assertThat(credit.getKind()).isEqualTo(TransactionKind.TRANSFER);
        assertThat(fartherDebit.getKind()).isEqualTo(TransactionKind.TRANSFER_PENDING);
    }

    @Test
    void leavesTiedNearestBankPostingsForOptionalReview() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        FinancialAccount bank = new FinancialAccount(userId, "PUBLIC_BANK", "Public Bank", AccountType.BANK);
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        Instant walletAt = Instant.parse("2026-07-10T04:00:00Z");
        FinanceTransaction credit = transaction(userId, wallet.getId(), Direction.CREDIT, walletAt, null);
        FinanceTransaction firstDebit = transaction(userId, bank.getId(), Direction.DEBIT,
                walletAt.plusSeconds(86400), null);
        FinanceTransaction secondDebit = transaction(userId, bank.getId(), Direction.DEBIT,
                walletAt.plusSeconds(86400), null);
        for (FinanceTransaction transaction : List.of(credit, firstDebit, secondDebit)) {
            transaction.attachStatementMetadata(1, 1, DatePrecision.DATE_ONLY, null, "TNG reload");
            transaction.markTransferPending();
        }
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.DEBIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(firstDebit, secondDebit));
        when(accounts.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));

        boolean reconciled = service.autoReconcile(credit);

        assertThat(reconciled).isFalse();
        assertThat(credit.getKind()).isEqualTo(TransactionKind.TRANSFER_PENDING);
        verify(candidates, times(2)).save(any(TransferCandidate.class));
    }

    @Test
    void automaticallyConfirmsAUniquePendingBankToWalletTopUp() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        FinancialAccount bank = new FinancialAccount(userId, "PUBLIC_BANK", "Public Bank", AccountType.BANK);
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        Instant at = Instant.parse("2026-07-10T04:00:00Z");
        FinanceTransaction debit = transaction(userId, bank.getId(), Direction.DEBIT, at, null);
        FinanceTransaction credit = transaction(userId, wallet.getId(), Direction.CREDIT, at, null);
        debit.attachStatementMetadata(1, 1, DatePrecision.DATE_ONLY, null, "Recognized bank-to-wallet top-up");
        credit.attachStatementMetadata(1, 2, DatePrecision.DATE_ONLY, null, "Possible wallet top-up");
        debit.markTransferPending();
        credit.markTransferPending();
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.DEBIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(debit));
        when(transactions.findMatchCandidates(eq(userId), eq(new BigDecimal("100.00")), eq(Direction.CREDIT),
                any(Instant.class), any(Instant.class))).thenReturn(List.of(credit));
        when(accounts.findById(bank.getId())).thenReturn(Optional.of(bank));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));

        boolean reconciled = service.autoReconcile(credit);

        assertThat(reconciled).isTrue();
        assertThat(debit.getKind()).isEqualTo(TransactionKind.TRANSFER);
        assertThat(credit.getKind()).isEqualTo(TransactionKind.TRANSFER);
        assertThat(debit.getResolutionMethod()).isEqualTo(ResolutionMethod.AUTO_TRANSFER);
        ArgumentCaptor<TransferCandidate> captor = ArgumentCaptor.forClass(TransferCandidate.class);
        verify(candidates).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransferStatus.CONFIRMED);
    }

    @Test
    void listingTransfersRejectsProposalsThatReuseAConfirmedTransaction() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        UUID sharedDebit = UUID.randomUUID();
        TransferCandidate confirmed = new TransferCandidate(userId, sharedDebit, UUID.randomUUID(), 85, "confirmed");
        confirmed.confirm();
        TransferCandidate stale = new TransferCandidate(userId, sharedDebit, UUID.randomUUID(), 78, "stale");
        when(candidates.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(stale, confirmed));

        var result = service.list(userId);

        assertThat(stale.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result).containsExactly(confirmed);
    }

    @Test
    void confirmingATransferRejectsEveryCompetingProposal() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        TransferCandidateRepository candidates = mock(TransferCandidateRepository.class);
        ReconciliationService service = new ReconciliationService(transactions, accounts, candidates);
        UUID userId = UUID.randomUUID();
        FinanceTransaction debit = transaction(userId, UUID.randomUUID(), Direction.DEBIT,
                Instant.parse("2026-07-10T04:00:00Z"), null);
        FinanceTransaction credit = transaction(userId, UUID.randomUUID(), Direction.CREDIT,
                Instant.parse("2026-07-10T04:00:00Z"), null);
        TransferCandidate winner = new TransferCandidate(userId, debit.getId(), credit.getId(), 85, "winner");
        TransferCandidate competingDebit = new TransferCandidate(userId, debit.getId(), UUID.randomUUID(), 78,
                "competing debit");
        TransferCandidate competingCredit = new TransferCandidate(userId, UUID.randomUUID(), credit.getId(), 78,
                "competing credit");
        when(candidates.findByIdAndUserId(winner.getId(), userId)).thenReturn(Optional.of(winner));
        when(candidates.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(competingDebit, competingCredit, winner));
        when(transactions.findByIdAndUserId(debit.getId(), userId)).thenReturn(Optional.of(debit));
        when(transactions.findByIdAndUserId(credit.getId(), userId)).thenReturn(Optional.of(credit));

        service.confirm(userId, winner.getId());

        assertThat(winner.getStatus()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(competingDebit.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(competingCredit.getStatus()).isEqualTo(TransferStatus.REJECTED);
    }

    private FinanceTransaction transaction(UUID userId, UUID accountId, Direction direction,
                                           Instant at, String reference) {
        return new FinanceTransaction(userId, accountId, null, direction,
                direction == Direction.DEBIT ? TransactionKind.EXPENSE : TransactionKind.INCOME,
                new BigDecimal("100.00"), at, "TNG eWallet", reference, BigDecimal.ONE, "synthetic");
    }
}
