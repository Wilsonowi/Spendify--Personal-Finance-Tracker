package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    Optional<Budget> findByUserIdAndCategoryIdAndMonthStart(UUID userId, UUID categoryId, LocalDate monthStart);
    List<Budget> findAllByUserIdAndMonthStart(UUID userId, LocalDate monthStart);
}
