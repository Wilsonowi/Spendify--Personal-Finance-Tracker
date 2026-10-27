package com.wilson.spendify.storage;

import com.wilson.spendify.config.SpendifyProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(SpendifyProperties properties) {
        SpendifyProperties.Storage storage = properties.storage();
        this.client = MinioClient.builder()
                .endpoint(storage.endpoint())
                .credentials(storage.accessKey(), storage.secretKey())
                .build();
        this.bucket = storage.bucket();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize encrypted object storage.", ex);
        }
    }

    @Override
    public void put(String key, byte[] bytes) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType("application/octet-stream").build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to store encrypted document.", ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return stream.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read encrypted document.", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to delete encrypted document.", ex);
        }
    }
}
