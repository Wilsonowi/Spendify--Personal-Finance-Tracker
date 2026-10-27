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
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Column(nullable = false, length = 60) private String name;
    @Column(name = "parent_id") private UUID parentId;
    @Column(name = "system_category", nullable = false) private boolean systemCategory;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public Category(UUID userId, String name, UUID parentId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name.trim();
        this.parentId = parentId;
        this.systemCategory = false;
        this.createdAt = Instant.now();
    }
}

