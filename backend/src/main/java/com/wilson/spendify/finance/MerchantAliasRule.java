package com.wilson.spendify.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "merchant_alias_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantAliasRule {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "normalized_alias", nullable = false, length = 180) private String normalizedAlias;
    @Column(name = "canonical_merchant", nullable = false, length = 160) private String canonicalMerchant;
    @Column(name = "category_id", nullable = false) private UUID categoryId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public MerchantAliasRule(UUID userId, String normalizedAlias, String canonicalMerchant, UUID categoryId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.normalizedAlias = normalizedAlias;
        this.canonicalMerchant = canonicalMerchant.trim();
        this.categoryId = categoryId;
        this.createdAt = Instant.now();
    }
}

