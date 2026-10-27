package com.wilson.spendify.storage;

import com.wilson.spendify.config.SpendifyProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DocumentCryptoService {
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKey masterKey;
    private final SecureRandom random = new SecureRandom();

    public DocumentCryptoService(SpendifyProperties properties) {
        String encoded = properties.storage().masterKeyBase64();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("SPENDIFY_MASTER_KEY_BASE64 must contain a 32-byte Base64 key.");
        }
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length != 32) {
            throw new IllegalStateException("SPENDIFY_MASTER_KEY_BASE64 must decode to exactly 32 bytes.");
        }
        this.masterKey = new SecretKeySpec(key, "AES");
    }

    public EncryptedPayload encrypt(byte[] plaintext) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256, random);
            SecretKey dataKey = generator.generateKey();
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher contentCipher = Cipher.getInstance("AES/GCM/NoPadding");
            contentCipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = contentCipher.doFinal(plaintext);

            Cipher keyCipher = Cipher.getInstance("AESWrap");
            keyCipher.init(Cipher.WRAP_MODE, masterKey);
            byte[] wrappedKey = keyCipher.wrap(dataKey);
            return new EncryptedPayload(ciphertext, Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(wrappedKey));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Document encryption failed.", ex);
        }
    }

    public byte[] decrypt(byte[] ciphertext, String encodedIv, String encodedWrappedKey) {
        try {
            Cipher keyCipher = Cipher.getInstance("AESWrap");
            keyCipher.init(Cipher.UNWRAP_MODE, masterKey);
            SecretKey dataKey = (SecretKey) keyCipher.unwrap(
                    Base64.getDecoder().decode(encodedWrappedKey), "AES", Cipher.SECRET_KEY);

            Cipher contentCipher = Cipher.getInstance("AES/GCM/NoPadding");
            contentCipher.init(Cipher.DECRYPT_MODE, dataKey,
                    new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(encodedIv)));
            return contentCipher.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Document authentication or decryption failed.", ex);
        }
    }
}

