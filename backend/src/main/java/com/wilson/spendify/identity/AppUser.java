package com.wilson.spendify.identity;

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
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AppUser(String email, String displayName, String passwordHash) {
        this.id = UUID.randomUUID();
        this.email = email.trim().toLowerCase();
        this.displayName = displayName.trim();
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }
}

