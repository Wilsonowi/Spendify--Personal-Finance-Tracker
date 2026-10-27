package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantAliasRuleRepository extends JpaRepository<MerchantAliasRule, UUID> {
    Optional<MerchantAliasRule> findByUserIdAndNormalizedAlias(UUID userId, String normalizedAlias);
    List<MerchantAliasRule> findAllByUserIdOrderByCanonicalMerchant(UUID userId);
}

