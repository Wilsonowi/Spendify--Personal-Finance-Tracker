package com.wilson.spendify.finance;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeMap;

@Service
public class FinanceService {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");

    private final FinanceTransactionRepository transactions;
    private final FinancialAccountRepository accounts;
    private final CategoryRepository categories;
    private final SourceDocumentRepository documents;
    private final MerchantAliasRuleRepository aliases;
    private final BudgetRepository budgets;
    private final CategorizationService categorization;
    private final ReconciliationService reconciliation;

    public FinanceService(FinanceTransactionRepository transactions, FinancialAccountRepository accounts,
                          CategoryRepository categories, SourceDocumentRepository documents,
                          MerchantAliasRuleRepository aliases, BudgetRepository budgets,
                          CategorizationService categorization, ReconciliationService reconciliation) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.documents = documents;
        this.aliases = aliases;
        this.budgets = budgets;
        this.categorization = categorization;
        this.reconciliation = reconciliation;
    }

    public List<FinanceTransaction> transactions(UUID userId) {
        return transactions.findAllByUserIdOrderByOccurredAtDesc(userId);
    }

    public List<FinancialAccount> accounts(UUID userId) {
        return accounts.findAllByUserIdOrderByDisplayName(userId);
    }

    public List<Category> categories(UUID userId) {
        return categories.availableTo(userId);
    }

    public List<MerchantAliasRule> aliases(UUID userId) {
        return aliases.findAllByUserIdOrderByCanonicalMerchant(userId);
    }

    @Transactional
    public FinanceTransaction revise(UUID userId, UUID transactionId, Revision revision) {
        FinanceTransaction transaction = requireOwned(userId, transactionId);
        boolean transferPending = transaction.getKind() == TransactionKind.TRANSFER_PENDING;
        if (transaction.getStatus() != TransactionStatus.DRAFT && !transferPending) {
            throw new IllegalStateException("Only exception transactions can be revised.");
        }
        requireAccount(userId, revision.accountId());
        requireCategory(userId, revision.categoryId());
        requirePositive(revision.amount());
        transaction.revise(revision.accountId(), revision.categoryId(), revision.direction(), revision.kind(),
                revision.amount(), revision.occurredAt(), clean(revision.merchant(), 160),
                clean(revision.reference(), 120));
        if (transferPending) transaction.requireReview(ReviewReason.AMBIGUOUS_TRANSFER);
        return transaction;
    }

    @Transactional
    public FinanceTransaction confirm(UUID userId, UUID transactionId, boolean allowDuplicate) {
        FinanceTransaction transaction = requireOwned(userId, transactionId);
        if (transaction.getStatus() != TransactionStatus.DRAFT) {
            throw new IllegalStateException("Only draft transactions can be confirmed.");
        }
        Instant duplicateFrom;
        Instant duplicateTo;
        if (transaction.getDatePrecision() == DatePrecision.DATE_ONLY) {
            LocalDate occurredOn = transaction.getOccurredAt().atZone(MALAYSIA).toLocalDate();
            duplicateFrom = occurredOn.atStartOfDay(MALAYSIA).toInstant();
            duplicateTo = occurredOn.plusDays(1).atStartOfDay(MALAYSIA).toInstant();
        } else {
            duplicateFrom = transaction.getOccurredAt().minusSeconds(120);
            duplicateTo = transaction.getOccurredAt().plusSeconds(120);
        }
        List<FinanceTransaction> duplicates = transactions.findPotentialDuplicates(userId, transaction.getAccountId(),
                transaction.getAmount(), transaction.getDirection(), transaction.getKind(), duplicateFrom, duplicateTo)
                .stream()
                .filter(item -> !item.getId().equals(transaction.getId()))
                .filter(item -> referencesConflict(transaction.getReference(), item.getReference()))
                .toList();
        if (!duplicates.isEmpty() && !allowDuplicate) {
            throw new IllegalStateException("Probable duplicate found. Review it before confirming with allowDuplicate=true.");
        }
        if (!duplicates.isEmpty() && allowDuplicate) transaction.confirmDuplicateOverride();
        else transaction.confirm();
        resolveDocument(transaction);
        reconciliation.proposeFor(transaction);
        return transaction;
    }

    @Transactional
    public FinanceTransaction exclude(UUID userId, UUID transactionId, String reason) {
        FinanceTransaction transaction = requireOwned(userId, transactionId);
        if (transaction.getStatus() != TransactionStatus.DRAFT
                && transaction.getKind() != TransactionKind.TRANSFER_PENDING) {
            throw new IllegalStateException("Only exception transactions can be excluded.");
        }
        String cleaned = clean(reason, 120);
        if (cleaned == null || cleaned.isBlank()) {
            throw new IllegalArgumentException("An exclusion reason is required.");
        }
        transaction.exclude(cleaned);
        resolveDocument(transaction);
        return transaction;
    }

    @Transactional
    public FinanceTransaction createManual(UUID userId, Revision revision) {
        requireAccount(userId, revision.accountId());
        requireCategory(userId, revision.categoryId());
        requirePositive(revision.amount());
        FinanceTransaction transaction = new FinanceTransaction(userId, revision.accountId(), null,
                revision.direction(), revision.kind(), revision.amount(), revision.occurredAt(),
                clean(revision.merchant(), 160), clean(revision.reference(), 120), BigDecimal.ONE,
                "manual entry");
        transaction.revise(revision.accountId(), revision.categoryId(), revision.direction(), revision.kind(),
                revision.amount(), revision.occurredAt(), clean(revision.merchant(), 160),
                clean(revision.reference(), 120));
        transaction.confirm();
        transactions.save(transaction);
        reconciliation.proposeFor(transaction);
        return transaction;
    }

    @Transactional
    public MerchantAliasRule learnAlias(UUID userId, String alias, String merchant, UUID categoryId) {
        return categorization.learn(userId, alias, merchant, categoryId);
    }

    @Transactional
    public Category createCategory(UUID userId, String name, UUID parentId) {
        if (name == null || name.isBlank() || name.length() > 60) {
            throw new IllegalArgumentException("Category name must contain 1 to 60 characters.");
        }
        if (parentId != null) requireCategory(userId, parentId);
        return categories.save(new Category(userId, name, parentId));
    }

    @Transactional
    public Budget setBudget(UUID userId, UUID categoryId, YearMonth month, BigDecimal amount) {
        requireCategory(userId, categoryId);
        requirePositive(amount);
        LocalDate monthStart = month.atDay(1);
        Budget budget = budgets.findByUserIdAndCategoryIdAndMonthStart(userId, categoryId, monthStart)
                .orElseGet(() -> new Budget(userId, categoryId, monthStart, amount));
        budget.updateAmount(amount);
        return budgets.save(budget);
    }

    public MonthlyAnalytics analytics(UUID userId, YearMonth month) {
        TimeRange current = range(month);
        TimeRange previous = range(month.minusMonths(1));
        List<FinanceTransaction> currentTransactions = transactions.confirmedInPeriod(userId, current.from(), current.to());
        List<FinanceTransaction> previousTransactions = transactions.confirmedInPeriod(userId, previous.from(), previous.to());
        Map<UUID, String> categoryNames = categories(userId).stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, Category::getName));
        Map<UUID, String> accountProviders = accounts(userId).stream()
                .collect(java.util.stream.Collectors.toMap(FinancialAccount::getId, FinancialAccount::getProvider));

        BigDecimal grossExpense = total(currentTransactions, Direction.DEBIT, TransactionKind.EXPENSE);
        BigDecimal refunds = total(currentTransactions, Direction.CREDIT, TransactionKind.REFUND);
        BigDecimal expense = grossExpense.subtract(refunds);
        BigDecimal income = total(currentTransactions, Direction.CREDIT, TransactionKind.INCOME);
        BigDecimal transferVolume = currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.TRANSFER && item.getDirection() == Direction.DEBIT)
                .map(FinanceTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousExpense = total(previousTransactions, Direction.DEBIT, TransactionKind.EXPENSE)
                .subtract(total(previousTransactions, Direction.CREDIT, TransactionKind.REFUND));
        BigDecimal change = previousExpense.signum() == 0 ? BigDecimal.ZERO
                : expense.subtract(previousExpense).multiply(BigDecimal.valueOf(100))
                .divide(previousExpense, 1, RoundingMode.HALF_UP);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.EXPENSE && item.getDirection() == Direction.DEBIT)
                .forEach(item -> byCategory.merge(categoryNames.getOrDefault(item.getCategoryId(), "Uncategorized"),
                        item.getAmount(), BigDecimal::add));
        currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.REFUND && item.getDirection() == Direction.CREDIT)
                .forEach(item -> byCategory.merge(categoryNames.getOrDefault(item.getCategoryId(), "Uncategorized"),
                        item.getAmount().negate(), BigDecimal::add));

        Map<String, BigDecimal> byMerchant = new LinkedHashMap<>();
        currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.EXPENSE && item.getDirection() == Direction.DEBIT)
                .forEach(item -> byMerchant.merge(merchantName(item), item.getAmount(), BigDecimal::add));
        currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.REFUND && item.getDirection() == Direction.CREDIT)
                .forEach(item -> byMerchant.merge(merchantName(item), item.getAmount().negate(), BigDecimal::add));

        int transactionCount = currentTransactions.size();
        int expenseCount = (int) currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.EXPENSE && item.getDirection() == Direction.DEBIT)
                .count();
        int refundCount = (int) currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.REFUND && item.getDirection() == Direction.CREDIT)
                .count();
        BigDecimal uncategorizedExpense = currentTransactions.stream()
                .filter(item -> item.getKind() == TransactionKind.EXPENSE && item.getDirection() == Direction.DEBIT)
                .filter(item -> "Uncategorized".equals(categoryNames.getOrDefault(
                        item.getCategoryId(), "Uncategorized")))
                .map(FinanceTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal categorizedExpense = grossExpense.subtract(uncategorizedExpense);
        int categorizationPercent = grossExpense.signum() == 0 ? 0
                : categorizedExpense.multiply(BigDecimal.valueOf(100))
                .divide(grossExpense, 0, RoundingMode.HALF_UP).intValue();
        Map<String, Integer> providerActivity = new LinkedHashMap<>();
        currentTransactions.forEach(item -> providerActivity.merge(
                accountProviders.getOrDefault(item.getAccountId(), "Other"), 1, Integer::sum));

        List<BudgetProgress> progress = budgets.findAllByUserIdAndMonthStart(userId, month.atDay(1)).stream()
                .map(budget -> {
                    String name = categoryNames.getOrDefault(budget.getCategoryId(), "Unknown");
                    BigDecimal spent = byCategory.getOrDefault(name, BigDecimal.ZERO);
                    int percent = spent.multiply(BigDecimal.valueOf(100))
                            .divide(budget.getAmount(), 0, RoundingMode.HALF_UP).intValue();
                    return new BudgetProgress(budget.getId(), budget.getCategoryId(), name,
                            budget.getAmount(), spent, percent);
                }).toList();
        MonthOption monthOption = months(userId, LocalDate.now(MALAYSIA)).months().stream()
                .filter(item -> item.month().equals(month.toString())).findFirst()
                .orElse(new MonthOption(month.toString(), true, 0, List.of()));
        return new MonthlyAnalytics(month.toString(), expense, grossExpense, refunds, expense, income,
                transferVolume, previousExpense, change, byCategory, byMerchant, progress,
                monthOption.coverage(), monthOption.partial(), monthOption.exceptionCount(),
                transactionCount, expenseCount, refundCount, categorizedExpense, uncategorizedExpense,
                categorizationPercent, providerActivity);
    }

    public MonthCatalog months(UUID userId, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        Map<YearMonth, Map<String, CoverageAccumulator>> coverageByMonth = new TreeMap<>(Comparator.reverseOrder());
        documents.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(document -> document.getDocumentKind() == DocumentKind.STATEMENT)
                .filter(document -> document.getValidationStatus() == StatementValidationStatus.VALID)
                .filter(document -> document.getPeriodStart() != null && document.getPeriodEnd() != null)
                .forEach(document -> {
                    YearMonth cursor = YearMonth.from(document.getPeriodStart());
                    YearMonth end = YearMonth.from(document.getPeriodEnd());
                    while (!cursor.isAfter(end)) {
                        LocalDate coveredFrom = document.getPeriodStart().isAfter(cursor.atDay(1))
                                ? document.getPeriodStart() : cursor.atDay(1);
                        LocalDate coveredTo = document.getPeriodEnd().isBefore(cursor.atEndOfMonth())
                                ? document.getPeriodEnd() : cursor.atEndOfMonth();
                        coverageByMonth.computeIfAbsent(cursor, ignored -> new java.util.LinkedHashMap<>())
                                .computeIfAbsent(document.getProvider(), ignored -> new CoverageAccumulator())
                                .include(coveredFrom, coveredTo);
                        cursor = cursor.plusMonths(1);
                    }
                });
        List<FinanceTransaction> allTransactions = transactions.findAllByUserIdOrderByOccurredAtDesc(userId);
        allTransactions.stream()
                .filter(item -> item.getStatus() == TransactionStatus.CONFIRMED || item.getReviewReason() != null)
                .map(item -> YearMonth.from(item.getOccurredAt().atZone(MALAYSIA)))
                .forEach(month -> coverageByMonth.computeIfAbsent(month, ignored -> new java.util.LinkedHashMap<>()));
        Map<YearMonth, Integer> exceptions = new java.util.HashMap<>();
        allTransactions.stream()
                .filter(item -> item.getReviewReason() != null)
                .filter(item -> item.getReviewReason() != ReviewReason.DOCUMENT_VALIDATION_FAILED)
                .forEach(item -> exceptions.merge(YearMonth.from(item.getOccurredAt().atZone(MALAYSIA)), 1,
                        Integer::sum));
        List<MonthOption> options = coverageByMonth.entrySet().stream().map(entry -> {
            YearMonth month = entry.getKey();
            List<MonthCoverage> coverage = entry.getValue().entrySet().stream()
                    .map(provider -> provider.getValue().toView(provider.getKey(), month)).toList();
            boolean partial = coverage.isEmpty() || !month.isBefore(currentMonth)
                    || coverage.stream().anyMatch(MonthCoverage::partial);
            return new MonthOption(month.toString(), partial, exceptions.getOrDefault(month, 0), coverage);
        }).toList();
        String recommended = options.stream().filter(item -> !item.partial()).map(MonthOption::month).findFirst()
                .orElse(options.isEmpty() ? currentMonth.toString() : options.getFirst().month());
        return new MonthCatalog(recommended, options);
    }

    public String exportCsv(UUID userId) {
        return exportCsv(userId, null, null);
    }

    public String exportCsv(UUID userId, LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder(
                "date,account,provider,direction,kind,provider_type,amount,currency,merchant,category,reference,status,resolution_method,treatment\n");
        Map<UUID, String> accountNames = accounts(userId).stream()
                .collect(java.util.stream.Collectors.toMap(FinancialAccount::getId, FinancialAccount::getDisplayName));
        Map<UUID, String> accountProviders = accounts(userId).stream()
                .collect(java.util.stream.Collectors.toMap(FinancialAccount::getId, FinancialAccount::getProvider));
        Map<UUID, String> categoryNames = categories(userId).stream()
                .collect(java.util.stream.Collectors.toMap(Category::getId, Category::getName));
        Instant fromInstant = from == null ? null : from.atStartOfDay(MALAYSIA).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(MALAYSIA).toInstant();
        transactions(userId).stream().filter(item -> item.getStatus() == TransactionStatus.CONFIRMED)
                .filter(item -> fromInstant == null || !item.getOccurredAt().isBefore(fromInstant))
                .filter(item -> toInstant == null || item.getOccurredAt().isBefore(toInstant))
                .forEach(item -> csv.append(item.getOccurredAt()).append(',')
                        .append(csvCell(accountNames.get(item.getAccountId()))).append(',')
                        .append(csvCell(accountProviders.get(item.getAccountId()))).append(',')
                        .append(item.getDirection()).append(',').append(item.getKind()).append(',')
                        .append(csvCell(item.getProviderTransactionType())).append(',')
                        .append(item.getAmount()).append(',').append(item.getCurrency()).append(',')
                        .append(csvCell(item.getMerchant())).append(',')
                        .append(csvCell(categoryNames.get(item.getCategoryId()))).append(',')
                        .append(csvCell(item.getReference())).append(',')
                        .append(item.getStatus()).append(',')
                        .append(item.getResolutionMethod() == null ? "" : item.getResolutionMethod()).append(',')
                        .append(treatment(item)).append('\n'));
        return csv.toString();
    }

    private void resolveDocument(FinanceTransaction transaction) {
        if (transaction.getDocumentId() == null) return;
        documents.findById(transaction.getDocumentId()).ifPresent(document -> {
            if (document.getDocumentKind() == DocumentKind.STATEMENT) document.resolveOne();
            else document.confirmed();
        });
    }

    private FinanceTransaction requireOwned(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found."));
    }

    private FinancialAccount requireAccount(UUID userId, UUID id) {
        return accounts.findById(id).filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Account is not available to this user."));
    }

    private Category requireCategory(UUID userId, UUID id) {
        return categories.findById(id).filter(item -> item.getUserId() == null || item.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Category is not available to this user."));
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must be positive with at most two decimal places.");
        }
    }

    private String clean(String value, int max) {
        if (value == null) return null;
        String cleaned = value.strip().replaceAll("[\\p{Cntrl}]", "");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private BigDecimal total(List<FinanceTransaction> values, Direction direction, TransactionKind kind) {
        return values.stream().filter(item -> item.getDirection() == direction && item.getKind() == kind)
                .map(FinanceTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String merchantName(FinanceTransaction transaction) {
        return transaction.getMerchant() == null || transaction.getMerchant().isBlank()
                ? "Unknown merchant" : transaction.getMerchant();
    }

    private boolean referencesConflict(String current, String existing) {
        String left = normalizedReference(current);
        String right = normalizedReference(existing);
        return left == null || right == null || left.equals(right);
    }

    private String normalizedReference(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String treatment(FinanceTransaction transaction) {
        if (transaction.getKind() == TransactionKind.TRANSFER) return "INTERNAL_TRANSFER";
        if (transaction.getKind() == TransactionKind.REFUND) return "REFUND_OFFSET";
        return transaction.getKind() == TransactionKind.EXPENSE ? "EXPENSE" : "INCOME";
    }

    private TimeRange range(YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(MALAYSIA).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(MALAYSIA).toInstant();
        return new TimeRange(from, to);
    }

    private String csvCell(String value) {
        if (value == null) return "";
        String safe = value.matches("^[=+@-].*") ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public record Revision(UUID accountId, UUID categoryId, Direction direction, TransactionKind kind,
                           BigDecimal amount, Instant occurredAt, String merchant, String reference) {}
    private record TimeRange(Instant from, Instant to) {}
    public record BudgetProgress(UUID id, UUID categoryId, String category, BigDecimal limit,
                                 BigDecimal spent, int percent) {}
    public record MonthlyAnalytics(String month, BigDecimal expense, BigDecimal grossExpense,
                                   BigDecimal refunds, BigDecimal netExpense, BigDecimal income,
                                   BigDecimal transferVolume, BigDecimal previousExpense,
                                   BigDecimal expenseChangePercent, Map<String, BigDecimal> categories,
                                   Map<String, BigDecimal> merchants, List<BudgetProgress> budgets,
                                   List<MonthCoverage> coverage, boolean partial, int pendingExceptions,
                                   int transactionCount, int expenseCount, int refundCount,
                                   BigDecimal categorizedExpense, BigDecimal uncategorizedExpense,
                                   int categorizationPercent, Map<String, Integer> providerActivity) {}
    public record MonthCatalog(String recommendedMonth, List<MonthOption> months) {}
    public record MonthOption(String month, boolean partial, int exceptionCount,
                              List<MonthCoverage> coverage) {}
    public record MonthCoverage(String provider, LocalDate coveredFrom, LocalDate coveredTo,
                                boolean partial) {}

    private static final class CoverageAccumulator {
        private LocalDate from;
        private LocalDate to;
        private final List<CoverageRange> ranges = new ArrayList<>();

        private void include(LocalDate candidateFrom, LocalDate candidateTo) {
            if (from == null || candidateFrom.isBefore(from)) from = candidateFrom;
            if (to == null || candidateTo.isAfter(to)) to = candidateTo;
            ranges.add(new CoverageRange(candidateFrom, candidateTo));
        }

        private MonthCoverage toView(String provider, YearMonth month) {
            LocalDate nextUncovered = month.atDay(1);
            for (CoverageRange range : ranges.stream()
                    .sorted(Comparator.comparing(CoverageRange::from)).toList()) {
                if (range.to().isBefore(nextUncovered)) continue;
                if (range.from().isAfter(nextUncovered)) break;
                nextUncovered = range.to().plusDays(1);
                if (nextUncovered.isAfter(month.atEndOfMonth())) break;
            }
            boolean partial = !nextUncovered.isAfter(month.atEndOfMonth());
            return new MonthCoverage(provider, from, to, partial);
        }
    }

    private record CoverageRange(LocalDate from, LocalDate to) {}
}
