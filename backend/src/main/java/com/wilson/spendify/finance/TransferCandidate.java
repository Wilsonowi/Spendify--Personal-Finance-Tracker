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
@Table(name = "transfer_candidate")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferCandidate {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "debit_transaction_id", nullable = false) private UUID debitTransactionId;
    @Column(name = "credit_transaction_id", nullable = false) private UUID creditTransactionId;
    @Column(nullable = false) private int score;
    @Column(nullable = false, length = 500) private String evidence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TransferStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public TransferCandidate(UUID userId, UUID debitId, UUID creditId, int score, String evidence) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.debitTransactionId = debitId;
        this.creditTransactionId = creditId;
        this.score = score;
        this.evidence = evidence;
        this.status = TransferStatus.PROPOSED;
        this.createdAt = Instant.now();
    }

    public void confirm() { status = TransferStatus.CONFIRMED; }
    public void reject() { status = TransferStatus.REJECTED; }
}

