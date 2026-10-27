package com.wilson.spendify.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

class FinanceServiceStatementTest {
    @Test
    void excludingLastDraftCompletesStatementReview() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        FinanceService service = new FinanceService(transactions, accounts, categories, documents,
                mock(MerchantAliasRuleRepository.class), mock(BudgetRepository.class),
                mock(CategorizationService.class), mock(ReconciliationService.class));

        UUID userId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        SourceDocument document = new SourceDocument(userId, "statement.pdf", "application/pdf", 200,
                "a".repeat(64), "objects/statement", "iv", "wrapped");
        document.configureStatement(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        document.needsStatementReview("TNG", 1, StatementValidationStatus.VALID, "valid");
        FinanceTransaction draft = new FinanceTransaction(userId, account.getId(), document.getId(),
                Direction.DEBIT, TransactionKind.EXPENSE, new BigDecimal("12.50"),
                Instant.parse("2026-01-02T04:00:00Z"), "Merchant", "SYN-1", BigDecimal.ONE, "statement");

        when(transactions.findByIdAndUserId(draft.getId(), userId)).thenReturn(Optional.of(draft));
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));

        service.exclude(userId, draft.getId(), "Not a financial transaction");

        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.EXCLUDED);
        assertThat(draft.getExclusionReason()).isEqualTo("Not a financial transaction");
        assertThat(document.getResolvedCount()).isEqualTo(1);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.CONFIRMED);
    }

    @Test
    void dateOnlyDuplicateSearchCoversTheMalaysiaCalendarDayWithoutMatchingItself() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        ReconciliationService reconciliation = mock(ReconciliationService.class);
        FinanceService service = new FinanceService(transactions, accounts, categories, documents,
                mock(MerchantAliasRuleRepository.class), mock(BudgetRepository.class),
                mock(CategorizationService.class), reconciliation);
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        FinanceTransaction draft = new FinanceTransaction(userId, accountId, null, Direction.DEBIT,
                TransactionKind.EXPENSE, new BigDecimal("12.50"), Instant.parse("2026-01-02T04:00:00Z"),
                "Synthetic", "SYN", BigDecimal.ONE, "statement");
        draft.attachStatementMetadata(1, 1, DatePrecision.DATE_ONLY, new BigDecimal("87.50"), null);
        when(transactions.findByIdAndUserId(draft.getId(), userId)).thenReturn(Optional.of(draft));
        when(transactions.findPotentialDuplicates(eq(userId), eq(accountId), eq(new BigDecimal("12.50")),
                eq(Direction.DEBIT), eq(TransactionKind.EXPENSE), any(), any())).thenReturn(List.of(draft));

        service.confirm(userId, draft.getId(), false);

        assertThat(draft.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        verify(transactions).findPotentialDuplicates(userId, accountId, new BigDecimal("12.50"),
                Direction.DEBIT, TransactionKind.EXPENSE, Instant.parse("2026-01-01T16:00:00Z"),
                Instant.parse("2026-01-02T16:00:00Z"));
    }

    @Test
    void monthlyAnalyticsSubtractsRefundsFromCategoryAndMerchantSpending() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        BudgetRepository budgets = mock(BudgetRepository.class);
        FinanceService service = new FinanceService(transactions, accounts, categories, documents,
                mock(MerchantAliasRuleRepository.class), budgets,
                mock(CategorizationService.class), mock(ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        FinancialAccount wallet = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        UUID accountId = wallet.getId();
        Category food = new Category(userId, "Food & Dining", null);
        Category uncategorized = new Category(userId, "Uncategorized", null);
        Instant at = Instant.parse("2026-07-10T04:00:00Z");
        FinanceTransaction purchase = new FinanceTransaction(userId, accountId, null, Direction.DEBIT,
                TransactionKind.EXPENSE, new BigDecimal("12.50"), at, "Lunch Merchant", "PAY-1",
                BigDecimal.ONE, "synthetic");
        purchase.revise(accountId, food.getId(), Direction.DEBIT, TransactionKind.EXPENSE,
                new BigDecimal("12.50"), at, "Lunch Merchant", "PAY-1");
        purchase.confirm();
        FinanceTransaction refund = new FinanceTransaction(userId, accountId, null, Direction.CREDIT,
                TransactionKind.REFUND, new BigDecimal("2.50"), at.plusSeconds(60), "Lunch Merchant", "REF-1",
                BigDecimal.ONE, "synthetic");
        refund.revise(accountId, food.getId(), Direction.CREDIT, TransactionKind.REFUND,
                new BigDecimal("2.50"), at.plusSeconds(60), "Lunch Merchant", "REF-1");
        refund.confirm();
        FinanceTransaction unknown = new FinanceTransaction(userId, accountId, null, Direction.DEBIT,
                TransactionKind.EXPENSE, new BigDecimal("5.00"), at.plusSeconds(120), "Unknown Merchant", "PAY-2",
                BigDecimal.ONE, "synthetic");
        unknown.revise(accountId, uncategorized.getId(), Direction.DEBIT, TransactionKind.EXPENSE,
                new BigDecimal("5.00"), at.plusSeconds(120), "Unknown Merchant", "PAY-2");
        unknown.autoPost();
        when(categories.availableTo(userId)).thenReturn(List.of(food, uncategorized));
        when(accounts.findAllByUserIdOrderByDisplayName(userId)).thenReturn(List.of(wallet));
        when(transactions.confirmedInPeriod(eq(userId), any(), any()))
                .thenReturn(List.of(purchase, refund, unknown), List.of());
        when(budgets.findAllByUserIdAndMonthStart(userId, LocalDate.of(2026, 7, 1))).thenReturn(List.of());

        FinanceService.MonthlyAnalytics analytics = service.analytics(userId, java.time.YearMonth.of(2026, 7));

        assertThat(analytics.grossExpense()).isEqualByComparingTo("17.50");
        assertThat(analytics.refunds()).isEqualByComparingTo("2.50");
        assertThat(analytics.expense()).isEqualByComparingTo("15.00");
        assertThat(analytics.categories()).containsEntry("Food & Dining", new BigDecimal("10.00"));
        assertThat(analytics.categories()).containsEntry("Uncategorized", new BigDecimal("5.00"));
        assertThat(analytics.merchants()).containsEntry("Lunch Merchant", new BigDecimal("10.00"));
        assertThat(analytics.transactionCount()).isEqualTo(3);
        assertThat(analytics.expenseCount()).isEqualTo(2);
        assertThat(analytics.refundCount()).isEqualTo(1);
        assertThat(analytics.categorizedExpense()).isEqualByComparingTo("12.50");
        assertThat(analytics.uncategorizedExpense()).isEqualByComparingTo("5.00");
        assertThat(analytics.categorizationPercent()).isEqualTo(71);
        assertThat(analytics.providerActivity()).containsEntry("TNG", 3);
    }

    @Test
    void recommendsLatestClosedMonthWhenNewestCoverageIsPartial() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        FinanceService service = new FinanceService(transactions, mock(FinancialAccountRepository.class),
                mock(CategoryRepository.class), documents, mock(MerchantAliasRuleRepository.class),
                mock(BudgetRepository.class), mock(CategorizationService.class), mock(ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        SourceDocument july = new SourceDocument(userId, "july.pdf", "application/pdf", 200,
                "1".repeat(64), "objects/july", "iv", "wrapped");
        july.configureStatement(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        july.recordStatementOutcome("PUBLIC_BANK", 10, StatementValidationStatus.VALID,
                "valid", 10, 0, 0, 0);
        SourceDocument august = new SourceDocument(userId, "august.pdf", "application/pdf", 200,
                "2".repeat(64), "objects/august", "iv", "wrapped");
        august.configureStatement(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));
        august.recordStatementOutcome("TNG", 4, StatementValidationStatus.VALID,
                "valid", 3, 0, 0, 1);
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(august, july));
        when(transactions.findAllByUserIdOrderByOccurredAtDesc(userId)).thenReturn(List.of());

        FinanceService.MonthCatalog catalog = service.months(userId, LocalDate.of(2026, 8, 7));

        assertThat(catalog.recommendedMonth()).isEqualTo("2026-07");
        assertThat(catalog.months()).extracting(FinanceService.MonthOption::month)
                .containsExactly("2026-08", "2026-07");
        assertThat(catalog.months().getFirst().partial()).isTrue();
        assertThat(catalog.months().get(1).partial()).isFalse();
    }

    @Test
    void includesConfirmedSlipTransactionMonthAsPartialCoverage() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        FinanceService service = new FinanceService(transactions, mock(FinancialAccountRepository.class),
                mock(CategoryRepository.class), documents, mock(MerchantAliasRuleRepository.class),
                mock(BudgetRepository.class), mock(CategorizationService.class), mock(ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        FinanceTransaction slip = new FinanceTransaction(userId, UUID.randomUUID(), UUID.randomUUID(),
                Direction.DEBIT, TransactionKind.EXPENSE, new BigDecimal("12.50"),
                Instant.parse("2026-07-10T04:00:00Z"), "Synthetic Merchant", "SYN-SLIP",
                BigDecimal.ONE, "synthetic");
        slip.confirm();
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(transactions.findAllByUserIdOrderByOccurredAtDesc(userId)).thenReturn(List.of(slip));

        FinanceService.MonthCatalog catalog = service.months(userId, LocalDate.of(2026, 8, 7));

        assertThat(catalog.recommendedMonth()).isEqualTo("2026-07");
        assertThat(catalog.months()).extracting(FinanceService.MonthOption::month).containsExactly("2026-07");
        assertThat(catalog.months().getFirst().partial()).isTrue();
    }

    @Test
    void keepsMonthPartialWhenProviderStatementsLeaveACoverageGap() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        FinanceService service = new FinanceService(transactions, mock(FinancialAccountRepository.class),
                mock(CategoryRepository.class), documents, mock(MerchantAliasRuleRepository.class),
                mock(BudgetRepository.class), mock(CategorizationService.class), mock(ReconciliationService.class));
        UUID userId = UUID.randomUUID();
        SourceDocument first = new SourceDocument(userId, "july-first.pdf", "application/pdf", 200,
                "3".repeat(64), "objects/july-first", "iv", "wrapped");
        first.configureStatement(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));
        first.recordStatementOutcome("PUBLIC_BANK", 2, StatementValidationStatus.VALID,
                "valid", 2, 0, 0, 0);
        SourceDocument second = new SourceDocument(userId, "july-second.pdf", "application/pdf", 200,
                "4".repeat(64), "objects/july-second", "iv", "wrapped");
        second.configureStatement(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 31));
        second.recordStatementOutcome("PUBLIC_BANK", 2, StatementValidationStatus.VALID,
                "valid", 2, 0, 0, 0);
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(second, first));
        when(transactions.findAllByUserIdOrderByOccurredAtDesc(userId)).thenReturn(List.of());

        FinanceService.MonthCatalog catalog = service.months(userId, LocalDate.of(2026, 8, 7));

        assertThat(catalog.months()).hasSize(1);
        assertThat(catalog.months().getFirst().partial()).isTrue();
    }
}
