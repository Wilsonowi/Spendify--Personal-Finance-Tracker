package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @Query("select c from Category c where c.userId is null or c.userId = :userId order by c.name")
    List<Category> availableTo(UUID userId);

    @Query("select c from Category c where lower(c.name) = lower(:name) and (c.userId is null or c.userId = :userId) order by c.userId desc nulls last")
    List<Category> findAvailableByName(UUID userId, String name);

    default Optional<Category> resolveByName(UUID userId, String name) {
        return findAvailableByName(userId, name).stream().findFirst();
    }
}

