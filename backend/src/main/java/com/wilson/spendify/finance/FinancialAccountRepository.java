package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {
    List<FinancialAccount> findAllByUserIdOrderByDisplayName(UUID userId);
    Optional<FinancialAccount> findByUserIdAndProvider(UUID userId, String provider);
}

