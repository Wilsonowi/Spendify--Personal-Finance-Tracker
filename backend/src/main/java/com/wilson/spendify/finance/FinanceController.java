package com.wilson.spendify.finance;

import com.wilson.spendify.identity.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final FinanceService finance;
    private final ReconciliationService reconciliation;
    private final ReviewQueueService reviewQueue;

    public FinanceController(FinanceService finance, ReconciliationService reconciliation,
                             ReviewQueueService reviewQueue) {
        this.finance = finance;
        this.reconciliation = reconciliation;
        this.reviewQueue = reviewQueue;
    }

    @GetMapping("/accounts")
    public List<AccountView> accounts(Authentication auth) {
        return finance.accounts(CurrentUser.id(auth)).stream().map(AccountView::from).toList();
    }

    @GetMapping("/categories")
    public List<CategoryView> categories(Authentication auth) {
        return finance.categories(CurrentUser.id(auth)).stream().map(CategoryView::from).toList();
    }

    @PostMapping("/categories")
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest request, Authentication auth) {
        return CategoryView.from(finance.createCategory(CurrentUser.id(auth), request.name(), request.parentId()));
    }

    @GetMapping("/transactions")
    public List<TransactionView> transactions(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) TransactionStatus status,
                                               Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        Map<UUID, String> accountNames = finance.accounts(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, FinancialAccount::getDisplayName));
        Map<UUID, String> categoryNames = finance.categories(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        String query = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        return finance.transactions(userId).stream()
                .filter(item -> status == null || item.getStatus() == status)
                .filter(item -> query.isEmpty() || contains(item.getMerchant(), query)
                        || contains(item.getReference(), query) || item.getAmount().toPlainString().contains(query))
                .map(item -> TransactionView.from(item, accountNames, categoryNames)).toList();
    }

    @PostMapping("/transactions")
    public TransactionView createManual(@Valid @RequestBody TransactionRequest request, Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        FinanceTransaction saved = finance.createManual(userId, request.toRevision());
        return view(userId, saved);
    }

    @PatchMapping("/transactions/{id}")
    public TransactionView revise(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request,
                                  Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        return view(userId, finance.revise(userId, id, request.toRevision()));
    }

    @PostMapping("/transactions/{id}/confirm")
    public TransactionView confirm(@PathVariable UUID id,
                                   @RequestParam(defaultValue = "false") boolean allowDuplicate,
                                   Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        return view(userId, finance.confirm(userId, id, allowDuplicate));
    }

    @PostMapping("/transactions/{id}/exclude")
    public TransactionView exclude(@PathVariable UUID id, @Valid @RequestBody ExclusionRequest request,
                                   Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        return view(userId, finance.exclude(userId, id, request.reason()));
    }

    @GetMapping("/review-queue")
    public ReviewQueueView reviewQueue(@RequestParam(required = false) UUID document,
                                       @RequestParam(required = false) String provider,
                                       @RequestParam(required = false) YearMonth month,
                                       @RequestParam(required = false) UUID category,
                                       @RequestParam(defaultValue = "false") boolean warningsOnly,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size,
                                       Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        Map<UUID, String> accountNames = finance.accounts(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, FinancialAccount::getDisplayName));
        Map<UUID, String> categoryNames = finance.categories(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        ReviewQueueService.ReviewPage result = reviewQueue.find(userId, document, provider, month, category,
                warningsOnly, page, size);
        return new ReviewQueueView(result.items().stream()
                .map(item -> TransactionView.from(item, accountNames, categoryNames)).toList(),
                result.total(), result.remaining(), result.page(), result.size());
    }

    @GetMapping(value = "/transactions/export.csv", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         Authentication auth) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Export start date must not be after the end date.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=spendify-transactions.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(finance.exportCsv(CurrentUser.id(auth), from, to));
    }

    @GetMapping("/merchant-rules")
    public List<MerchantRuleView> merchantRules(Authentication auth) {
        return finance.aliases(CurrentUser.id(auth)).stream().map(MerchantRuleView::from).toList();
    }

    @PostMapping("/merchant-rules")
    public MerchantRuleView learnRule(@Valid @RequestBody MerchantRuleRequest request, Authentication auth) {
        return MerchantRuleView.from(finance.learnAlias(CurrentUser.id(auth), request.alias(),
                request.canonicalMerchant(), request.categoryId()));
    }

    @PutMapping("/budgets/{categoryId}")
    public BudgetView setBudget(@PathVariable UUID categoryId, @RequestParam YearMonth month,
                                @Valid @RequestBody BudgetRequest request, Authentication auth) {
        return BudgetView.from(finance.setBudget(CurrentUser.id(auth), categoryId, month, request.amount()));
    }

    @GetMapping("/analytics/monthly")
    public FinanceService.MonthlyAnalytics analytics(@RequestParam YearMonth month, Authentication auth) {
        return finance.analytics(CurrentUser.id(auth), month);
    }

    @GetMapping("/analytics/months")
    public FinanceService.MonthCatalog analyticsMonths(Authentication auth) {
        return finance.months(CurrentUser.id(auth), LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")));
    }

    @GetMapping("/transfers")
    public List<TransferView> transfers(Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        Map<UUID, FinanceTransaction> transactionMap = finance.transactions(userId).stream()
                .collect(Collectors.toMap(FinanceTransaction::getId, item -> item));
        Map<UUID, String> accountMap = finance.accounts(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, FinancialAccount::getDisplayName));
        return reconciliation.list(userId).stream()
                .map(candidate -> TransferView.from(candidate, transactionMap, accountMap)).toList();
    }

    @PostMapping("/transfers/{id}/confirm")
    public TransferView confirmTransfer(@PathVariable UUID id, Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        return transferView(userId, reconciliation.confirm(userId, id));
    }

    @PostMapping("/transfers/{id}/reject")
    public TransferView rejectTransfer(@PathVariable UUID id, Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        return transferView(userId, reconciliation.reject(userId, id));
    }

    private TransferView transferView(UUID userId, TransferCandidate candidate) {
        Map<UUID, FinanceTransaction> transactionMap = finance.transactions(userId).stream()
                .collect(Collectors.toMap(FinanceTransaction::getId, item -> item));
        Map<UUID, String> accountMap = finance.accounts(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, FinancialAccount::getDisplayName));
        return TransferView.from(candidate, transactionMap, accountMap);
    }

    private TransactionView view(UUID userId, FinanceTransaction transaction) {
        Map<UUID, String> accountNames = finance.accounts(userId).stream()
                .collect(Collectors.toMap(FinancialAccount::getId, FinancialAccount::getDisplayName));
        Map<UUID, String> categoryNames = finance.categories(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return TransactionView.from(transaction, accountNames, categoryNames);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public record AccountView(UUID id, String provider, String name, AccountType type) {
        static AccountView from(FinancialAccount account) {
            return new AccountView(account.getId(), account.getProvider(), account.getDisplayName(),
                    account.getAccountType());
        }
    }

    public record CategoryView(UUID id, String name, UUID parentId, boolean system) {
        static CategoryView from(Category category) {
            return new CategoryView(category.getId(), category.getName(), category.getParentId(),
                    category.isSystemCategory());
        }
    }

    public record TransactionView(UUID id, UUID documentId, UUID accountId, String account,
                                  UUID categoryId, String category, Direction direction, TransactionKind kind,
                                  BigDecimal amount, String currency, Instant occurredAt, String merchant,
                                  String reference, BigDecimal confidence, TransactionStatus status,
                                  Integer sourcePage, Integer sourceSequence, DatePrecision occurredPrecision,
                                  BigDecimal statementBalance, String reviewNote, String exclusionReason,
                                  ReviewReason reviewReason, ResolutionMethod resolutionMethod,
                                  String providerTransactionType, UUID refundOf) {
        static TransactionView from(FinanceTransaction transaction, Map<UUID, String> accounts,
                                    Map<UUID, String> categories) {
            return new TransactionView(transaction.getId(), transaction.getDocumentId(), transaction.getAccountId(),
                    accounts.get(transaction.getAccountId()), transaction.getCategoryId(),
                    categories.getOrDefault(transaction.getCategoryId(), "Uncategorized"),
                    transaction.getDirection(), transaction.getKind(), transaction.getAmount(),
                    transaction.getCurrency(), transaction.getOccurredAt(), transaction.getMerchant(),
                    transaction.getReference(), transaction.getExtractionConfidence(), transaction.getStatus(),
                    transaction.getSourcePage(), transaction.getSourceSequence(), transaction.getDatePrecision(),
                    transaction.getStatementBalance(), transaction.getReviewNote(), transaction.getExclusionReason(),
                    transaction.getReviewReason(), transaction.getResolutionMethod(),
                    transaction.getProviderTransactionType(), transaction.getRefundOf());
        }
    }

    public record ReviewQueueView(List<TransactionView> items, int total, int remaining, int page, int size) {}

    public record TransactionRequest(@NotNull UUID accountId, @NotNull UUID categoryId,
                                     @NotNull Direction direction, @NotNull TransactionKind kind,
                                     @NotNull @DecimalMin("0.01") BigDecimal amount,
                                     @NotNull Instant occurredAt, @Size(max = 160) String merchant,
                                     @Size(max = 120) String reference) {
        FinanceService.Revision toRevision() {
            return new FinanceService.Revision(accountId, categoryId, direction, kind, amount,
                    occurredAt, merchant, reference);
        }
    }

    public record CategoryRequest(@NotBlank @Size(max = 60) String name, UUID parentId) {}
    public record MerchantRuleRequest(@NotBlank @Size(max = 180) String alias,
                                      @NotBlank @Size(max = 160) String canonicalMerchant,
                                      @NotNull UUID categoryId) {}
    public record BudgetRequest(@NotNull @DecimalMin("0.01") BigDecimal amount) {}
    public record ExclusionRequest(@NotBlank @Size(max = 120) String reason) {}

    public record MerchantRuleView(UUID id, String alias, String canonicalMerchant, UUID categoryId) {
        static MerchantRuleView from(MerchantAliasRule rule) {
            return new MerchantRuleView(rule.getId(), rule.getNormalizedAlias(), rule.getCanonicalMerchant(),
                    rule.getCategoryId());
        }
    }

    public record BudgetView(UUID id, UUID categoryId, LocalDate monthStart, BigDecimal amount) {
        static BudgetView from(Budget budget) {
            return new BudgetView(budget.getId(), budget.getCategoryId(), budget.getMonthStart(), budget.getAmount());
        }
    }

    public record TransferView(UUID id, UUID debitTransactionId, UUID creditTransactionId,
                               int score, String evidence, TransferStatus status,
                               TransferSide debit, TransferSide credit) {
        static TransferView from(TransferCandidate candidate, Map<UUID, FinanceTransaction> transactions,
                                 Map<UUID, String> accounts) {
            FinanceTransaction debit = transactions.get(candidate.getDebitTransactionId());
            FinanceTransaction credit = transactions.get(candidate.getCreditTransactionId());
            return new TransferView(candidate.getId(), candidate.getDebitTransactionId(),
                    candidate.getCreditTransactionId(), candidate.getScore(), candidate.getEvidence(),
                    candidate.getStatus(), TransferSide.from(debit, accounts),
                    TransferSide.from(credit, accounts));
        }
    }

    public record TransferSide(UUID transactionId, String account, Direction direction, TransactionKind kind,
                               BigDecimal amount, Instant occurredAt, String merchant,
                               String providerTransactionType) {
        static TransferSide from(FinanceTransaction transaction, Map<UUID, String> accounts) {
            if (transaction == null) throw new EntityNotFoundException("Transfer transaction not found.");
            return new TransferSide(transaction.getId(), accounts.getOrDefault(transaction.getAccountId(), "Account"),
                    transaction.getDirection(), transaction.getKind(), transaction.getAmount(),
                    transaction.getOccurredAt(), transaction.getMerchant(), transaction.getProviderTransactionType());
        }
    }
}
