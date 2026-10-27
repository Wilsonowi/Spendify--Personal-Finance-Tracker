package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.CategorizationService;
import com.wilson.spendify.finance.FinanceTransaction;
import com.wilson.spendify.finance.FinanceTransactionRepository;
import com.wilson.spendify.finance.FinancialAccount;
import com.wilson.spendify.finance.FinancialAccountRepository;
import com.wilson.spendify.finance.SourceDocument;
import com.wilson.spendify.finance.SourceDocumentRepository;
import com.wilson.spendify.finance.StatementValidationStatus;
import com.wilson.spendify.finance.ReviewReason;
import com.wilson.spendify.finance.ReconciliationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class StatementIngestionService {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    private final PdfStatementTextExtractor extractor;
    private final List<StatementProviderParser> parsers;
    private final FinancialAccountRepository accounts;
    private final FinanceTransactionRepository transactions;
    private final SourceDocumentRepository documents;
    private final CategorizationService categorization;
    private final ReconciliationService reconciliation;

    public StatementIngestionService(PdfStatementTextExtractor extractor, List<StatementProviderParser> parsers,
                                     FinancialAccountRepository accounts,
                                     FinanceTransactionRepository transactions,
                                     SourceDocumentRepository documents,
                                     CategorizationService categorization,
                                     ReconciliationService reconciliation) {
        this.extractor = extractor;
        this.parsers = parsers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.documents = documents;
        this.categorization = categorization;
        this.reconciliation = reconciliation;
    }

    public List<FinanceTransaction> ingest(SourceDocument document, byte[] plaintext) {
        List<String> pages = extractor.extract(plaintext);
        StatementProviderParser parser = parsers.stream().filter(candidate -> candidate.supports(pages)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The PDF is not recognized as a Public Bank/Public Islamic Bank or TNG statement."));
        StatementParseResult result = parser.parse(pages, document.getPeriodStart(), document.getPeriodEnd());
        if (result.rows().isEmpty()) {
            throw new IllegalArgumentException("No transactions were found in the selected statement period.");
        }
        FinancialAccount account = accounts.findByUserIdAndProvider(document.getUserId(), result.provider())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account is configured for " + result.provider() + "."));
        document.configureStatement(result.periodStart(), result.periodEnd());

        List<FinanceTransaction> imported = new ArrayList<>();
        for (StatementRow row : result.rows()) {
            CategorizationService.Suggestion suggestion = categorization.suggest(document.getUserId(), row.merchant());
            FinanceTransaction transaction = new FinanceTransaction(document.getUserId(), account.getId(), document.getId(),
                    row.direction(), row.kind(), row.amount(),
                    row.occurredOn().atTime(LocalTime.NOON).atZone(MALAYSIA).toInstant(),
                    suggestion.canonicalMerchant(), row.reference(), row.confidence(),
                    "statement row=" + row.sequence() + "; provider=" + result.provider());
            transaction.revise(account.getId(), suggestion.categoryId(), row.direction(), row.kind(), row.amount(),
                    transaction.getOccurredAt(), suggestion.canonicalMerchant(), row.reference());
            transaction.attachStatementMetadata(row.sourcePage(), row.sequence(), row.datePrecision(),
                    row.balance(), row.reviewNote());
            transaction.applyCategorization(BigDecimal.valueOf(suggestion.confidence()), suggestion.source());
            transaction.applyImportAudit(row.providerTransactionType(), ReferenceFingerprint.of(row.reference()));
            ReviewReason reason = structuralReviewReason(result.validationStatus(), row);
            if (reason == null) {
                FinanceTransaction duplicate = strongDuplicate(transaction);
                if (duplicate != null) transaction.markDuplicate(duplicate.getId());
                else if (transaction.getReferenceFingerprint() == null && hasPotentialDuplicate(transaction)) {
                    transaction.requireReview(ReviewReason.POSSIBLE_DUPLICATE);
                } else if (row.kind() == com.wilson.spendify.finance.TransactionKind.TRANSFER_PENDING) {
                    transaction.markTransferPending();
                } else transaction.autoPost();
            } else {
                transaction.requireReview(reason);
            }
            imported.add(transaction);
        }
        List<FinanceTransaction> saved = transactions.saveAll(imported);
        boolean overlaps = documents.findAllByUserIdOrderByCreatedAtDesc(document.getUserId()).stream()
                .filter(other -> !other.getId().equals(document.getId()))
                .filter(other -> other.getDocumentKind() == com.wilson.spendify.finance.DocumentKind.STATEMENT)
                .filter(other -> result.provider().equals(other.getProvider()))
                .filter(other -> other.getPeriodStart() != null && other.getPeriodEnd() != null)
                .anyMatch(other -> !result.periodEnd().isBefore(other.getPeriodStart())
                        && !result.periodStart().isAfter(other.getPeriodEnd()));
        StatementValidationStatus status = result.validationStatus();
        String validationMessage = overlaps
                ? result.validationMessage() + " This period overlaps another statement; review probable duplicates."
                : result.validationMessage();
        int autoPosted = (int) saved.stream()
                .filter(item -> item.getResolutionMethod() == com.wilson.spendify.finance.ResolutionMethod.AUTO_POSTED)
                .count();
        int duplicates = (int) saved.stream()
                .filter(item -> item.getStatus() == com.wilson.spendify.finance.TransactionStatus.DUPLICATE)
                .count();
        int transferRows = (int) saved.stream()
                .filter(item -> item.getStatus() == com.wilson.spendify.finance.TransactionStatus.CONFIRMED)
                .filter(item -> item.getKind() == com.wilson.spendify.finance.TransactionKind.TRANSFER_PENDING
                        || item.getKind() == com.wilson.spendify.finance.TransactionKind.TRANSFER)
                .count();
        autoPosted -= transferRows;
        int exceptions = saved.size() - autoPosted - duplicates - transferRows;
        document.recordStatementOutcome(result.provider(), saved.size(), status, validationMessage,
                autoPosted, duplicates, transferRows, exceptions);
        saved.stream().filter(item -> item.getKind()
                        == com.wilson.spendify.finance.TransactionKind.TRANSFER_PENDING)
                .forEach(reconciliation::autoReconcile);
        return saved;
    }

    private FinanceTransaction strongDuplicate(FinanceTransaction transaction) {
        if (transaction.getReferenceFingerprint() == null) return null;
        return transactions.findStrongDuplicate(transaction.getUserId(), transaction.getAccountId(),
                transaction.getOccurredAt(), transaction.getAmount(), transaction.getDirection(),
                transaction.getKind(), transaction.getReferenceFingerprint()).orElse(null);
    }

    private boolean hasPotentialDuplicate(FinanceTransaction transaction) {
        Instant from;
        Instant to;
        if (transaction.getDatePrecision() == com.wilson.spendify.finance.DatePrecision.DATE_ONLY) {
            LocalDate date = transaction.getOccurredAt().atZone(MALAYSIA).toLocalDate();
            from = date.atStartOfDay(MALAYSIA).toInstant();
            to = date.plusDays(1).atStartOfDay(MALAYSIA).toInstant();
        } else {
            from = transaction.getOccurredAt().minusSeconds(120);
            to = transaction.getOccurredAt().plusSeconds(120);
        }
        return !transactions.findPotentialDuplicates(transaction.getUserId(), transaction.getAccountId(),
                transaction.getAmount(), transaction.getDirection(), transaction.getKind(), from, to).isEmpty();
    }

    private ReviewReason structuralReviewReason(StatementValidationStatus validationStatus, StatementRow row) {
        if (validationStatus != StatementValidationStatus.VALID) return ReviewReason.DOCUMENT_VALIDATION_FAILED;
        if (row.reviewNote() != null && row.reviewNote().startsWith("Warning:")) return ReviewReason.PARSER_WARNING;
        if (row.confidence().compareTo(new java.math.BigDecimal("0.950")) < 0) {
            return ReviewReason.LOW_EXTRACTION_CONFIDENCE;
        }
        return null;
    }

}
