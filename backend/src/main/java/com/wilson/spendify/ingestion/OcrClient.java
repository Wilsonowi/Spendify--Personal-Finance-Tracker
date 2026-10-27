package com.wilson.spendify.ingestion;

import com.wilson.spendify.config.SpendifyProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class OcrClient {
    private final RestClient client;
    private final boolean enabled;

    public OcrClient(RestClient.Builder builder, SpendifyProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.client = builder.baseUrl(properties.ocr().baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.enabled = properties.ocr().enabled();
    }

    public OcrResult extract(byte[] bytes, String contentType, String filename) {
        if (!enabled) {
            throw new IllegalStateException("OCR processing is disabled.");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<>(new NamedByteArrayResource(bytes, filename), partHeaders));
        OcrResult result = client.post().uri("/v1/ocr")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(OcrResult.class);
        if (result == null || result.text() == null || result.text().isBlank()) {
            throw new IllegalStateException("OCR returned no readable text.");
        }
        return result;
    }

    public record OcrResult(String text, double confidence) {}

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
