package com.wilson.spendify.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
    List<IngestionJob> findTop5ByStatusAndAvailableAtBeforeOrderByCreatedAt(JobStatus status, java.time.Instant now);
}
