package com.wilson.spendify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spendify")
public record SpendifyProperties(Storage storage, Ocr ocr, boolean aiEnabled) {
    public record Storage(String endpoint, String accessKey, String secretKey, String bucket,
                          String masterKeyBase64) {}
    public record Ocr(String baseUrl, boolean enabled) {}
}

