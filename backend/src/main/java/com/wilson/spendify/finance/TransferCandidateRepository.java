package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferCandidateRepository extends JpaRepository<TransferCandidate, UUID> {
    Optional<TransferCandidate> findByIdAndUserId(UUID id, UUID userId);
    List<TransferCandidate> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByDebitTransactionIdAndCreditTransactionId(UUID debitId, UUID creditId);
    Optional<TransferCandidate> findByDebitTransactionIdAndCreditTransactionId(UUID debitId, UUID creditId);
}
