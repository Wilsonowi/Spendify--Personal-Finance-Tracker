package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.AccountType;
import com.wilson.spendify.finance.CategorizationService;
import com.wilson.spendify.finance.DatePrecision;
import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.FinanceTransactionRepository;
import com.wilson.spendify.finance.FinanceTransaction;
import com.wilson.spendify.finance.FinancialAccount;
import com.wilson.spendify.finance.FinancialAccountRepository;
import com.wilson.spendify.finance.SourceDocument;
import com.wilson.spendify.finance.StatementValidationStatus;
import com.wilson.spendify.finance.SourceDocumentRepository;
import com.wilson.spendify.finance.TransactionKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatementIngestionServiceTest {
    @Test
    void automaticallyPostsCleanRowsFromAValidStatement() {
        PdfStatementTextExtractor extractor = mock(PdfStatementTextExtractor.class);
        StatementProviderParser parser = mock(StatementProviderParser.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CategorizationService categorization = mock(CategorizationService.class);
        StatementIngestionService service = new StatementIngestionService(
                extractor, List.of(parser), accounts, transactions, documents, categorization,
                mock(com.wilson.spendify.finance.ReconciliationService.class));

        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument document = new SourceDocument(userId, "synthetic.pdf", "application/pdf", 200,
                "a".repeat(64), "objects/statement", "iv", "wrapped");
        document.configureStatement(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        StatementRow first = row(1, Direction.CREDIT, "100.00", "100.00", "Top Up");
        StatementRow second = row(2, Direction.DEBIT, "12.50", "87.50", "Lunch");
        StatementParseResult result = new StatementParseResult("TNG", LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3), List.of(first, second), StatementValidationStatus.VALID, "valid");

        when(extractor.extract(any())).thenReturn(List.of("TNG WALLET TRANSACTION HISTORY"));
        when(parser.supports(any())).thenReturn(true);
        when(parser.parse(any(), any(), any())).thenReturn(result);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Top Up"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Top Up", 0.85, "keyword"));
        when(categorization.suggest(userId, "Lunch"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Lunch", 0.85, "keyword"));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(document));

        var imported = service.ingest(document, new byte[]{1, 2, 3});

        assertThat(imported).hasSize(2);
        assertThat(imported).extracting(item -> item.getSourceSequence()).containsExactly(1, 2);
        assertThat(imported).allMatch(item -> item.getDatePrecision() == DatePrecision.DATE_ONLY);
        assertThat(imported).allMatch(item -> item.getStatus() == com.wilson.spendify.finance.TransactionStatus.CONFIRMED);
        assertThat(imported).allMatch(item -> item.getResolutionMethod()
                == com.wilson.spendify.finance.ResolutionMethod.AUTO_POSTED);
        assertThat(document.getRowCount()).isEqualTo(2);
        assertThat(document.getAutoPostedCount()).isEqualTo(2);
        assertThat(document.getExceptionCount()).isZero();
        assertThat(document.getStatus()).isEqualTo(com.wilson.spendify.finance.DocumentStatus.CONFIRMED);
        assertThat(document.getValidationStatus()).isEqualTo(StatementValidationStatus.VALID);
        verify(transactions).saveAll(imported);
    }

    @Test
    void automaticallyPostsValidRowsWithAProvisionalUncategorizedCategory() {
        PdfStatementTextExtractor extractor = mock(PdfStatementTextExtractor.class);
        StatementProviderParser parser = mock(StatementProviderParser.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CategorizationService categorization = mock(CategorizationService.class);
        StatementIngestionService service = new StatementIngestionService(
                extractor, List.of(parser), accounts, transactions, documents, categorization,
                mock(com.wilson.spendify.finance.ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument document = new SourceDocument(userId, "synthetic.pdf", "application/pdf", 200,
                "c".repeat(64), "objects/mixed", "iv", "wrapped");
        document.configureStatement(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        StatementRow clean = row(1, Direction.DEBIT, "12.50", "87.50", "Lunch");
        StatementRow unknown = row(2, Direction.DEBIT, "8.00", "79.50", "Unknown code");
        StatementParseResult result = new StatementParseResult("TNG", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31), List.of(clean, unknown), StatementValidationStatus.VALID, "valid");
        when(extractor.extract(any())).thenReturn(List.of("TNG WALLET TRANSACTION HISTORY"));
        when(parser.supports(any())).thenReturn(true);
        when(parser.parse(any(), any(), any())).thenReturn(result);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Lunch"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Lunch", 0.85, "keyword"));
        when(categorization.suggest(userId, "Unknown code"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Unknown code", 0.0, "fallback"));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(document));

        var imported = service.ingest(document, new byte[]{1});

        assertThat(imported.getFirst().getStatus())
                .isEqualTo(com.wilson.spendify.finance.TransactionStatus.CONFIRMED);
        assertThat(imported.get(1).getStatus())
                .isEqualTo(com.wilson.spendify.finance.TransactionStatus.CONFIRMED);
        assertThat(imported.get(1).getReviewReason()).isNull();
        assertThat(imported.get(1).getCategorizationConfidence()).isEqualByComparingTo("0.0");
        assertThat(document.getAutoPostedCount()).isEqualTo(2);
        assertThat(document.getExceptionCount()).isZero();
        assertThat(document.getStatus())
                .isEqualTo(com.wilson.spendify.finance.DocumentStatus.CONFIRMED);
    }

    @Test
    void treatsAnUnmatchedRecognizedReloadAsAResolvedNonExpense() {
        PdfStatementTextExtractor extractor = mock(PdfStatementTextExtractor.class);
        StatementProviderParser parser = mock(StatementProviderParser.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CategorizationService categorization = mock(CategorizationService.class);
        StatementIngestionService service = new StatementIngestionService(
                extractor, List.of(parser), accounts, transactions, documents, categorization,
                mock(com.wilson.spendify.finance.ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument document = new SourceDocument(userId, "reload.pdf", "application/pdf", 200,
                "f".repeat(64), "objects/reload", "iv", "wrapped");
        document.configureStatement(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        StatementRow reload = new StatementRow(1, 1, LocalDate.of(2026, 7, 2), DatePrecision.DATE_ONLY,
                Direction.CREDIT, TransactionKind.TRANSFER_PENDING, new BigDecimal("100.00"),
                new BigDecimal("100.00"), "Reload", "SYN-RELOAD", new BigDecimal("0.980"),
                "Possible wallet top-up", "Reload");
        StatementParseResult result = new StatementParseResult("TNG", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31), List.of(reload), StatementValidationStatus.VALID, "valid");
        when(extractor.extract(any())).thenReturn(List.of("TNG WALLET TRANSACTION HISTORY"));
        when(parser.supports(any())).thenReturn(true);
        when(parser.parse(any(), any(), any())).thenReturn(result);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Reload"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Reload", 0.0, "fallback"));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(document));

        var imported = service.ingest(document, new byte[]{1});

        assertThat(imported.getFirst().getStatus())
                .isEqualTo(com.wilson.spendify.finance.TransactionStatus.CONFIRMED);
        assertThat(imported.getFirst().getKind()).isEqualTo(TransactionKind.TRANSFER_PENDING);
        assertThat(imported.getFirst().getReviewReason()).isNull();
        assertThat(document.getTransferCount()).isEqualTo(1);
        assertThat(document.getExceptionCount()).isZero();
        assertThat(document.getStatus()).isEqualTo(com.wilson.spendify.finance.DocumentStatus.CONFIRMED);
    }

    @Test
    void invalidDocumentValidationPostsNothing() {
        PdfStatementTextExtractor extractor = mock(PdfStatementTextExtractor.class);
        StatementProviderParser parser = mock(StatementProviderParser.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CategorizationService categorization = mock(CategorizationService.class);
        StatementIngestionService service = new StatementIngestionService(
                extractor, List.of(parser), accounts, transactions, documents, categorization,
                mock(com.wilson.spendify.finance.ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument document = new SourceDocument(userId, "invalid.pdf", "application/pdf", 200,
                "d".repeat(64), "objects/invalid", "iv", "wrapped");
        document.configureStatement(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        StatementParseResult result = new StatementParseResult("TNG", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31), List.of(row(1, Direction.DEBIT, "12.50", "99.99", "Lunch")),
                StatementValidationStatus.WARNING, "Wallet balance continuity failed.");
        when(extractor.extract(any())).thenReturn(List.of("TNG WALLET TRANSACTION HISTORY"));
        when(parser.supports(any())).thenReturn(true);
        when(parser.parse(any(), any(), any())).thenReturn(result);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Lunch"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Lunch", 0.85, "keyword"));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(document));

        var imported = service.ingest(document, new byte[]{1});

        assertThat(imported).allMatch(item -> item.getStatus()
                == com.wilson.spendify.finance.TransactionStatus.DRAFT);
        assertThat(imported).allMatch(item -> item.getReviewReason()
                == com.wilson.spendify.finance.ReviewReason.DOCUMENT_VALIDATION_FAILED);
        assertThat(document.getAutoPostedCount()).isZero();
        assertThat(document.getExceptionCount()).isEqualTo(1);
        assertThat(document.getStatus()).isEqualTo(com.wilson.spendify.finance.DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void automaticallyMarksOnlyAnExactProviderReferenceAsDuplicate() {
        PdfStatementTextExtractor extractor = mock(PdfStatementTextExtractor.class);
        StatementProviderParser parser = mock(StatementProviderParser.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CategorizationService categorization = mock(CategorizationService.class);
        StatementIngestionService service = new StatementIngestionService(
                extractor, List.of(parser), accounts, transactions, documents, categorization,
                mock(com.wilson.spendify.finance.ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument document = new SourceDocument(userId, "overlap.pdf", "application/pdf", 200,
                "e".repeat(64), "objects/overlap", "iv", "wrapped");
        document.configureStatement(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        StatementRow row = row(1, Direction.DEBIT, "12.50", "87.50", "Lunch");
        StatementParseResult result = new StatementParseResult("TNG", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31), List.of(row), StatementValidationStatus.VALID, "valid");
        FinanceTransaction original = new FinanceTransaction(userId, account.getId(), UUID.randomUUID(),
                Direction.DEBIT, TransactionKind.EXPENSE, new BigDecimal("12.50"),
                LocalDate.of(2026, 1, 2).atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Kuala_Lumpur")).toInstant(),
                "Lunch", "SYN-1", BigDecimal.ONE, "existing");
        original.confirm();
        when(extractor.extract(any())).thenReturn(List.of("TNG WALLET TRANSACTION HISTORY"));
        when(parser.supports(any())).thenReturn(true);
        when(parser.parse(any(), any(), any())).thenReturn(result);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Lunch"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Lunch", 0.85, "keyword"));
        when(transactions.findStrongDuplicate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(original));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(document));

        var imported = service.ingest(document, new byte[]{1});

        assertThat(imported.getFirst().getStatus())
                .isEqualTo(com.wilson.spendify.finance.TransactionStatus.DUPLICATE);
        assertThat(imported.getFirst().getDuplicateOf()).isEqualTo(original.getId());
        assertThat(document.getDuplicateCount()).isEqualTo(1);
        assertThat(document.getExceptionCount()).isZero();
    }

    @Test
    void warnsWhenAProviderStatementPeriodOverlapsExistingCoverage() {
        PdfStatementTextExtractor extractor = mock(PdfStatementTextExtractor.class);
        StatementProviderParser parser = mock(StatementProviderParser.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CategorizationService categorization = mock(CategorizationService.class);
        StatementIngestionService service = new StatementIngestionService(
                extractor, List.of(parser), accounts, transactions, documents, categorization,
                mock(com.wilson.spendify.finance.ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument current = new SourceDocument(userId, "current.pdf", "application/pdf", 200,
                "a".repeat(64), "objects/current", "iv", "wrapped");
        current.configureStatement(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        SourceDocument existing = new SourceDocument(userId, "existing.pdf", "application/pdf", 200,
                "b".repeat(64), "objects/existing", "iv", "wrapped");
        existing.configureStatement(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30));
        existing.needsStatementReview("TNG", 1, StatementValidationStatus.VALID, "valid");
        StatementRow row = row(1, Direction.DEBIT, "12.50", "87.50", "Lunch");
        StatementParseResult result = new StatementParseResult("TNG", LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 3, 31), List.of(row), StatementValidationStatus.VALID, "valid");
        when(extractor.extract(any())).thenReturn(List.of("TNG WALLET TRANSACTION HISTORY"));
        when(parser.supports(any())).thenReturn(true);
        when(parser.parse(any(), any(), any())).thenReturn(result);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Lunch"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Lunch", 0.85, "keyword"));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(current, existing));

        service.ingest(current, new byte[]{1});

        assertThat(current.getValidationStatus()).isEqualTo(StatementValidationStatus.VALID);
        assertThat(current.getValidationMessage()).contains("overlaps");
    }

    private StatementRow row(int sequence, Direction direction, String amount, String balance, String merchant) {
        return new StatementRow(sequence, 1, LocalDate.of(2026, 1, sequence + 1), DatePrecision.DATE_ONLY,
                direction, direction == Direction.DEBIT ? TransactionKind.EXPENSE : TransactionKind.INCOME,
                new BigDecimal(amount), new BigDecimal(balance), merchant, "SYN-" + sequence,
                new BigDecimal("0.980"), null, direction == Direction.DEBIT ? "PAYMENT" : "CREDIT");
    }
}
