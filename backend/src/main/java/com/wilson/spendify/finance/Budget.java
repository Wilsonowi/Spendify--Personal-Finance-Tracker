package com.wilson.spendify.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "budget")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(name = "month_start", nullable = false) private LocalDate monthStart;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Budget(UUID userId, UUID categoryId, LocalDate monthStart, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.categoryId = categoryId;
        this.monthStart = monthStart.withDayOfMonth(1);
        this.amount = amount.setScale(2);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void updateAmount(BigDecimal amount) { this.amount = amount.setScale(2); this.updatedAt = Instant.now(); }
}

