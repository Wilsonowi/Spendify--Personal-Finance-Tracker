package com.wilson.spendify.ingestion;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class FileTypeValidator {
    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/webp", "application/pdf");

    public String validate(byte[] bytes, String claimedType) {
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Each document must be between 1 byte and 10 MB.");
        }
        String detected = detect(bytes);
        if (!ALLOWED.contains(detected)) {
            throw new IllegalArgumentException("Only PNG, JPEG, WebP, and PDF documents are accepted.");
        }
        if (claimedType != null && !claimedType.isBlank() && !claimedType.equalsIgnoreCase(detected)) {
            throw new IllegalArgumentException("The file content does not match its declared type.");
        }
        return detected;
    }

    private String detect(byte[] bytes) {
        if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47})) return "image/png";
        if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return "image/jpeg";
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if (startsWith(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        return "application/octet-stream";
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }
}

