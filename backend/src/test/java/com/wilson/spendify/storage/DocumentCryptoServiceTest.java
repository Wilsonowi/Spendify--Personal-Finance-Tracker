package com.wilson.spendify.storage;

import com.wilson.spendify.config.SpendifyProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCryptoServiceTest {
    private final DocumentCryptoService crypto = new DocumentCryptoService(properties());

    @Test
    void encryptsAndAuthenticatesEachDocument() {
        byte[] plaintext = "synthetic financial slip".getBytes(StandardCharsets.UTF_8);
        EncryptedPayload encrypted = crypto.encrypt(plaintext);

        assertThat(encrypted.ciphertext()).isNotEqualTo(plaintext);
        assertThat(crypto.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.wrappedKey()))
                .isEqualTo(plaintext);
    }

    @Test
    void rejectsTamperedCiphertext() {
        EncryptedPayload encrypted = crypto.encrypt("private".getBytes(StandardCharsets.UTF_8));
        encrypted.ciphertext()[0] ^= 1;

        assertThatThrownBy(() -> crypto.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.wrappedKey()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication");
    }

    private SpendifyProperties properties() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new SpendifyProperties(new SpendifyProperties.Storage("", "", "", "", key),
                new SpendifyProperties.Ocr("", false), false);
    }
}

