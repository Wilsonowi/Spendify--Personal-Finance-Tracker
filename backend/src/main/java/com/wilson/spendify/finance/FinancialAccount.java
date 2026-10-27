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
@Table(name = "financial_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialAccount {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20) private AccountType accountType;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public FinancialAccount(UUID userId, String provider, String displayName, AccountType accountType) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.displayName = displayName;
        this.accountType = accountType;
        this.createdAt = Instant.now();
    }
}

