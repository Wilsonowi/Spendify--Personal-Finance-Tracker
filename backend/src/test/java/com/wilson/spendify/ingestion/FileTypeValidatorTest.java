package com.wilson.spendify.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTypeValidatorTest {
    private final FileTypeValidator validator = new FileTypeValidator();

    @Test
    void detectsPngFromMagicBytes() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a};
        assertThat(validator.validate(png, "image/png")).isEqualTo("image/png");
    }

    @Test
    void rejectsMismatchedClaimedType() {
        byte[] pdf = "%PDF-1.7 synthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> validator.validate(pdf, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }
}

