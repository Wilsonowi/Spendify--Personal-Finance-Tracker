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
import java.util.UUID;

@Getter
@Entity
@Table(name = "ingestion_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionJob {
    @Id private UUID id;
    @Column(name = "document_id", nullable = false, unique = true) private UUID documentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private JobStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public IngestionJob(UUID documentId) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.status = JobStatus.PENDING;
        this.availableAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void start() { status = JobStatus.PROCESSING; lockedAt = Instant.now(); attempts++; updatedAt = Instant.now(); }
    public void complete() { status = JobStatus.COMPLETE; updatedAt = Instant.now(); }
    public void fail(String message) { status = JobStatus.FAILED; lastError = message; updatedAt = Instant.now(); }
}

