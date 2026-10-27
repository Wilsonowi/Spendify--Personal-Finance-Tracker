package com.wilson.spendify.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewQueueServiceTest {
    @Test
    void filtersDraftsAndPaginatesWithoutBulkConfirmation() {
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        ReviewQueueService service = new ReviewQueueService(transactions, accounts);
        UUID userId = UUID.randomUUID();
        FinancialAccount tng = new FinancialAccount(userId, "TNG", "TNG", AccountType.EWALLET);
        FinanceTransaction january = transaction(userId, tng, "2026-01-02T04:00:00Z", "Lunch");
        FinanceTransaction february = transaction(userId, tng, "2026-02-02T04:00:00Z", "Travel");
        february.attachStatementMetadata(2, 2, DatePrecision.DATE_ONLY, new BigDecimal("50.00"), "warning");
        january.requireReview(ReviewReason.UNCATEGORIZED);
        february.requireReview(ReviewReason.PARSER_WARNING);
        when(transactions.findAllByUserIdOrderByOccurredAtDesc(userId)).thenReturn(List.of(february, january));
        when(accounts.findAllByUserIdOrderByDisplayName(userId)).thenReturn(List.of(tng));

        var page = service.find(userId, null, "TNG", YearMonth.of(2026, 2), null, true, 0, 50);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.remaining()).isEqualTo(2);
        assertThat(page.items()).containsExactly(february);
    }

    private FinanceTransaction transaction(UUID userId, FinancialAccount account, String occurredAt,
                                           String merchant) {
        return new FinanceTransaction(userId, account.getId(), null, Direction.DEBIT, TransactionKind.EXPENSE,
                new BigDecimal("10.00"), Instant.parse(occurredAt), merchant, null, BigDecimal.ONE, "synthetic");
    }
}
