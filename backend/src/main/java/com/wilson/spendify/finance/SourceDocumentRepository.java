package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {
    boolean existsByUserIdAndSha256(UUID userId, String sha256);
    Optional<SourceDocument> findByIdAndUserId(UUID id, UUID userId);
    List<SourceDocument> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}

