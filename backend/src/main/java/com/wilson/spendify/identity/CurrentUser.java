package com.wilson.spendify.identity;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SpendifyPrincipal principal)) {
            throw new IllegalStateException("Authenticated user is unavailable.");
        }
        return principal.id();
    }
}

