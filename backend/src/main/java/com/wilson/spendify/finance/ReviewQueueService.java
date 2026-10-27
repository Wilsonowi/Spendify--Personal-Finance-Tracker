package com.wilson.spendify.finance;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewQueueService {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    private final FinanceTransactionRepository transactions;
    private final FinancialAccountRepository accounts;

    public ReviewQueueService(FinanceTransactionRepository transactions, FinancialAccountRepository accounts) {
        this.transactions = transactions;
        this.accounts = accounts;
    }

    public ReviewPage find(UUID userId, UUID documentId, String provider, YearMonth month, UUID categoryId,
                           boolean warningsOnly, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Map<UUID, String> providers = accounts.findAllByUserIdOrderByDisplayName(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, FinancialAccount::getProvider));
        List<FinanceTransaction> allDrafts = transactions.findAllByUserIdOrderByOccurredAtDesc(userId).stream()
                .filter(item -> item.getReviewReason() != null)
                .filter(item -> item.getReviewReason() != ReviewReason.DOCUMENT_VALIDATION_FAILED)
                .filter(item -> item.getStatus() == TransactionStatus.DRAFT
                        || item.getKind() == TransactionKind.TRANSFER_PENDING)
                .toList();
        Instant from = month == null ? null : month.atDay(1).atStartOfDay(MALAYSIA).toInstant();
        Instant to = month == null ? null : month.plusMonths(1).atDay(1).atStartOfDay(MALAYSIA).toInstant();
        List<FinanceTransaction> filtered = allDrafts.stream()
                .filter(item -> documentId == null || documentId.equals(item.getDocumentId()))
                .filter(item -> provider == null || provider.isBlank()
                        || provider.equalsIgnoreCase(providers.get(item.getAccountId())))
                .filter(item -> categoryId == null || categoryId.equals(item.getCategoryId()))
                .filter(item -> from == null || (!item.getOccurredAt().isBefore(from) && item.getOccurredAt().isBefore(to)))
                .filter(item -> !warningsOnly || item.getReviewReason() != null)
                .toList();
        int start = Math.min(filtered.size(), safePage * safeSize);
        int end = Math.min(filtered.size(), start + safeSize);
        return new ReviewPage(filtered.subList(start, end), filtered.size(), allDrafts.size(), safePage, safeSize);
    }

    public record ReviewPage(List<FinanceTransaction> items, int total, int remaining, int page, int size) {}
}
