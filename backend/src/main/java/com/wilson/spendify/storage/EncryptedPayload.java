package com.wilson.spendify.storage;

public record EncryptedPayload(byte[] ciphertext, String iv, String wrappedKey) {}

