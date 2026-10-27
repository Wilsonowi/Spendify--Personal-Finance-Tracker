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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "source_document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceDocument {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "original_name", nullable = false) private String originalName;
    @Column(name = "content_type", nullable = false, length = 80) private String contentType;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "storage_key", nullable = false) private String storageKey;
    @Column(name = "encryption_iv", nullable = false, length = 32) private String encryptionIv;
    @Column(name = "wrapped_key", nullable = false, length = 128) private String wrappedKey;
    @Column(name = "key_version", nullable = false) private int keyVersion;
    @Column(length = 30) private String provider;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_kind", nullable = false, length = 20) private DocumentKind documentKind;
    @Column(name = "period_start") private LocalDate periodStart;
    @Column(name = "period_end") private LocalDate periodEnd;
    @Column(name = "row_count", nullable = false) private int rowCount;
    @Column(name = "resolved_count", nullable = false) private int resolvedCount;
    @Column(name = "auto_posted_count", nullable = false) private int autoPostedCount;
    @Column(name = "exception_count", nullable = false) private int exceptionCount;
    @Column(name = "duplicate_count", nullable = false) private int duplicateCount;
    @Column(name = "transfer_count", nullable = false) private int transferCount;
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private StatementValidationStatus validationStatus;
    @Column(name = "validation_message", length = 500) private String validationMessage;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private DocumentStatus status;
    @Column(name = "error_message", length = 500) private String errorMessage;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public SourceDocument(UUID userId, String originalName, String contentType, long byteSize,
                          String sha256, String storageKey, String encryptionIv, String wrappedKey) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.encryptionIv = encryptionIv;
        this.wrappedKey = wrappedKey;
        this.keyVersion = 1;
        this.documentKind = DocumentKind.SLIP;
        this.rowCount = 0;
        this.resolvedCount = 0;
        this.autoPostedCount = 0;
        this.exceptionCount = 0;
        this.duplicateCount = 0;
        this.transferCount = 0;
        this.validationStatus = StatementValidationStatus.PENDING;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = Instant.now();
    }

    public void processing() { this.status = DocumentStatus.PROCESSING; this.errorMessage = null; }
    public void needsReview(String provider) { this.status = DocumentStatus.NEEDS_REVIEW; this.provider = provider; }
    public void configureStatement(LocalDate periodStart, LocalDate periodEnd) {
        this.documentKind = DocumentKind.STATEMENT;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }
    public void needsStatementReview(String provider, int rowCount, StatementValidationStatus validationStatus,
                                     String validationMessage) {
        this.provider = provider;
        this.rowCount = rowCount;
        this.resolvedCount = 0;
        this.validationStatus = validationStatus;
        this.validationMessage = validationMessage;
        this.status = DocumentStatus.NEEDS_REVIEW;
    }
    public void recordStatementOutcome(String provider, int rowCount,
                                       StatementValidationStatus validationStatus, String validationMessage,
                                       int autoPostedCount, int duplicateCount, int transferCount,
                                       int exceptionCount) {
        this.provider = provider;
        this.rowCount = rowCount;
        this.autoPostedCount = autoPostedCount;
        this.duplicateCount = duplicateCount;
        this.transferCount = transferCount;
        this.exceptionCount = exceptionCount;
        this.resolvedCount = autoPostedCount + duplicateCount + transferCount;
        this.validationStatus = validationStatus;
        this.validationMessage = validationMessage;
        refreshPostingStatus();
    }
    public void resolveOne() {
        if (exceptionCount > 0) exceptionCount--;
        if (resolvedCount < rowCount) resolvedCount++;
        refreshPostingStatus();
    }
    public void resolveTransfer() {
        if (exceptionCount > 0) exceptionCount--;
        if (resolvedCount < rowCount) resolvedCount++;
        transferCount++;
        refreshPostingStatus();
    }
    public void failed(String message) { this.status = DocumentStatus.FAILED; this.errorMessage = message; }
    public void confirmed() { this.status = DocumentStatus.CONFIRMED; }

    private void refreshPostingStatus() {
        if (documentKind != DocumentKind.STATEMENT) return;
        if (validationStatus != StatementValidationStatus.VALID) {
            this.status = DocumentStatus.NEEDS_REVIEW;
        } else if (exceptionCount == 0) {
            this.status = DocumentStatus.CONFIRMED;
        } else if (resolvedCount > 0) {
            this.status = DocumentStatus.PARTIALLY_POSTED;
        } else {
            this.status = DocumentStatus.NEEDS_REVIEW;
        }
    }
}
