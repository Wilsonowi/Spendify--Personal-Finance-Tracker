package com.wilson.spendify.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class ReferenceFingerprint {
    private ReferenceFingerprint() {}

    static String of(String reference) {
        if (reference == null) return null;
        String normalized = reference.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.isBlank()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
