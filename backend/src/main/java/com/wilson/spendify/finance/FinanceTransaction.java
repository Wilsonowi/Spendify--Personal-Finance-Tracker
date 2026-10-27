package com.wilson.spendify.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "finance_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinanceTransaction {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "document_id") private UUID documentId;
    @Column(name = "category_id") private UUID categoryId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private Direction direction;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TransactionKind kind;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(length = 160) private String merchant;
    @Column(length = 120) private String reference;
    @Column(name = "extraction_confidence", precision = 4, scale = 3) private BigDecimal extractionConfidence;
    @Column(name = "raw_evidence", columnDefinition = "TEXT") private String rawEvidence;
    @Column(name = "source_page") private Integer sourcePage;
    @Column(name = "source_sequence") private Integer sourceSequence;
    @Enumerated(EnumType.STRING)
    @Column(name = "date_precision", nullable = false, length = 20) private DatePrecision datePrecision;
    @Column(name = "statement_balance", precision = 14, scale = 2) private BigDecimal statementBalance;
    @Column(name = "review_note", length = 500) private String reviewNote;
    @Column(name = "exclusion_reason", length = 120) private String exclusionReason;
    @Column(name = "categorization_confidence", precision = 4, scale = 3) private BigDecimal categorizationConfidence;
    @Column(name = "categorization_source", length = 30) private String categorizationSource;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", length = 40) private ReviewReason reviewReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_method", length = 40) private ResolutionMethod resolutionMethod;
    @Column(name = "provider_transaction_type", length = 80) private String providerTransactionType;
    @Column(name = "reference_fingerprint", length = 64) private String referenceFingerprint;
    @Column(name = "refund_of") private UUID refundOf;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TransactionStatus status;
    @Column(name = "duplicate_of") private UUID duplicateOf;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public FinanceTransaction(UUID userId, UUID accountId, UUID documentId, Direction direction,
                              TransactionKind kind, BigDecimal amount, Instant occurredAt,
                              String merchant, String reference, BigDecimal confidence, String rawEvidence) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.accountId = accountId;
        this.documentId = documentId;
        this.direction = direction;
        this.kind = kind;
        this.amount = amount.setScale(2);
        this.currency = "MYR";
        this.occurredAt = occurredAt;
        this.merchant = merchant;
        this.reference = reference;
        this.extractionConfidence = confidence;
        this.rawEvidence = rawEvidence;
        this.datePrecision = DatePrecision.EXACT;
        this.status = TransactionStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void revise(UUID accountId, UUID categoryId, Direction direction, TransactionKind kind,
                       BigDecimal amount, Instant occurredAt, String merchant, String reference) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.direction = direction;
        this.kind = kind;
        this.amount = amount.setScale(2);
        this.occurredAt = occurredAt;
        this.merchant = merchant;
        this.reference = reference;
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        this.status = TransactionStatus.CONFIRMED;
        if (this.resolutionMethod == null) this.resolutionMethod = ResolutionMethod.USER_CONFIRMED;
        this.reviewReason = null;
        this.updatedAt = Instant.now();
    }
    public void autoPost() {
        this.status = TransactionStatus.CONFIRMED;
        this.resolutionMethod = ResolutionMethod.AUTO_POSTED;
        this.reviewReason = null;
        this.updatedAt = Instant.now();
    }
    public void confirmDuplicateOverride() {
        this.status = TransactionStatus.CONFIRMED;
        this.resolutionMethod = ResolutionMethod.DUPLICATE_OVERRIDE;
        this.reviewReason = null;
        this.updatedAt = Instant.now();
    }
    public void markDuplicate(UUID originalId) {
        this.status = TransactionStatus.DUPLICATE;
        this.duplicateOf = originalId;
        this.resolutionMethod = ResolutionMethod.AUTO_DUPLICATE;
        this.reviewReason = null;
        this.updatedAt = Instant.now();
    }
    public void attachStatementMetadata(int sourcePage, int sourceSequence, DatePrecision datePrecision,
                                        BigDecimal statementBalance, String reviewNote) {
        this.sourcePage = sourcePage;
        this.sourceSequence = sourceSequence;
        this.datePrecision = datePrecision;
        this.statementBalance = statementBalance;
        this.reviewNote = reviewNote;
    }
    public void exclude(String reason) {
        this.status = TransactionStatus.EXCLUDED;
        this.exclusionReason = reason;
        this.resolutionMethod = ResolutionMethod.USER_EXCLUDED;
        this.reviewReason = null;
        this.updatedAt = Instant.now();
    }
    public void applyCategorization(BigDecimal confidence, String source) {
        this.categorizationConfidence = confidence;
        this.categorizationSource = source;
    }
    public void applyImportAudit(String providerTransactionType, String referenceFingerprint) {
        this.providerTransactionType = providerTransactionType;
        this.referenceFingerprint = referenceFingerprint;
    }
    public void requireReview(ReviewReason reason) {
        this.reviewReason = reason;
        this.status = TransactionStatus.DRAFT;
        this.resolutionMethod = null;
        this.updatedAt = Instant.now();
    }
    public void markTransferPending() {
        this.kind = TransactionKind.TRANSFER_PENDING;
        this.status = TransactionStatus.CONFIRMED;
        this.reviewReason = null;
        this.resolutionMethod = ResolutionMethod.AUTO_POSTED;
        this.updatedAt = Instant.now();
    }
    public void markTransfer() {
        markTransfer(ResolutionMethod.AUTO_TRANSFER);
    }
    public void markTransfer(ResolutionMethod method) {
        this.kind = TransactionKind.TRANSFER;
        this.status = TransactionStatus.CONFIRMED;
        this.reviewReason = null;
        this.resolutionMethod = method;
        this.updatedAt = Instant.now();
    }
    public void linkRefund(UUID originalId) { this.refundOf = originalId; }
}
