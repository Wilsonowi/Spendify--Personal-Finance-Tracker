package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, UUID> {
    Optional<FinanceTransaction> findByIdAndUserId(UUID id, UUID userId);
    List<FinanceTransaction> findAllByDocumentIdOrderBySourceSequence(UUID documentId);
    List<FinanceTransaction> findAllByUserIdOrderByOccurredAtDesc(UUID userId);

    @Query("select t from FinanceTransaction t where t.userId = :userId and t.status = 'CONFIRMED' and t.amount = :amount and t.direction = :direction and t.occurredAt between :from and :to")
    List<FinanceTransaction> findMatchCandidates(UUID userId, BigDecimal amount, Direction direction,
                                                  Instant from, Instant to);

    @Query("select t from FinanceTransaction t where t.userId = :userId and t.status = 'CONFIRMED' and t.occurredAt >= :from and t.occurredAt < :to order by t.occurredAt desc")
    List<FinanceTransaction> confirmedInPeriod(UUID userId, Instant from, Instant to);

    @Query("select t from FinanceTransaction t where t.userId = :userId and t.status = 'CONFIRMED' and t.accountId = :accountId and t.amount = :amount and t.direction = :direction and t.occurredAt between :from and :to")
    List<FinanceTransaction> probableDuplicates(UUID userId, UUID accountId, BigDecimal amount,
                                                 Direction direction, Instant from, Instant to);

    @Query("select t from FinanceTransaction t where t.userId = :userId and t.accountId = :accountId "
            + "and t.occurredAt = :occurredAt and t.amount = :amount and t.direction = :direction "
            + "and t.kind = :kind and t.referenceFingerprint = :fingerprint and t.status <> 'EXCLUDED'")
    Optional<FinanceTransaction> findStrongDuplicate(UUID userId, UUID accountId, Instant occurredAt,
                                                      BigDecimal amount, Direction direction,
                                                      TransactionKind kind, String fingerprint);

    @Query("select t from FinanceTransaction t where t.userId = :userId and t.accountId = :accountId "
            + "and t.amount = :amount and t.direction = :direction and t.kind = :kind "
            + "and t.occurredAt between :from and :to and t.status <> 'EXCLUDED'")
    List<FinanceTransaction> findPotentialDuplicates(UUID userId, UUID accountId, BigDecimal amount,
                                                      Direction direction, TransactionKind kind,
                                                      Instant from, Instant to);
}
