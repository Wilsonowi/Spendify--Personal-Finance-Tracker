package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.IngestionJob;
import com.wilson.spendify.finance.IngestionJobRepository;
import com.wilson.spendify.finance.SourceDocument;
import com.wilson.spendify.finance.SourceDocumentRepository;
import com.wilson.spendify.storage.DocumentCryptoService;
import com.wilson.spendify.storage.EncryptedPayload;
import com.wilson.spendify.storage.ObjectStorage;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@Service
public class ImportService {
    private final SourceDocumentRepository documents;
    private final IngestionJobRepository jobs;
    private final FileTypeValidator fileTypes;
    private final DocumentCryptoService crypto;
    private final ObjectStorage storage;
    private final PdfStatementPageRenderer pageRenderer;

    public ImportService(SourceDocumentRepository documents, IngestionJobRepository jobs,
                         FileTypeValidator fileTypes, DocumentCryptoService crypto, ObjectStorage storage,
                         PdfStatementPageRenderer pageRenderer) {
        this.documents = documents;
        this.jobs = jobs;
        this.fileTypes = fileTypes;
        this.crypto = crypto;
        this.storage = storage;
        this.pageRenderer = pageRenderer;
    }

    @Transactional
    public SourceDocument upload(UUID userId, MultipartFile file) {
        return store(userId, file, null, null);
    }

    @Transactional
    public SourceDocument uploadStatement(UUID userId, MultipartFile file, LocalDate periodStart,
                                          LocalDate periodEnd) {
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("Statements must be uploaded as PDF documents.");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("The statement date range is invalid.");
        }
        return store(userId, file, periodStart, periodEnd);
    }

    private SourceDocument store(UUID userId, MultipartFile file, LocalDate periodStart, LocalDate periodEnd) {
        try {
            byte[] plaintext = file.getBytes();
            String contentType = fileTypes.validate(plaintext, file.getContentType());
            String hash = sha256(plaintext);
            if (documents.existsByUserIdAndSha256(userId, hash)) {
                throw new IllegalArgumentException("This exact document has already been uploaded.");
            }

            EncryptedPayload encrypted = crypto.encrypt(plaintext);
            String storageKey = userId + "/" + UUID.randomUUID() + ".enc";
            storage.put(storageKey, encrypted.ciphertext());
            try {
                String safeName = Path.of(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
                        .getFileName().toString();
                SourceDocument document = documents.save(new SourceDocument(userId, safeName, contentType,
                        plaintext.length, hash, storageKey, encrypted.iv(), encrypted.wrappedKey()));
                if (periodStart != null) document.configureStatement(periodStart, periodEnd);
                jobs.save(new IngestionJob(document.getId()));
                return document;
            } catch (RuntimeException ex) {
                storage.delete(storageKey);
                throw ex;
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("The uploaded document could not be read.", ex);
        }
    }

    public List<SourceDocument> list(UUID userId) {
        return documents.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public byte[] read(UUID userId, UUID documentId) {
        SourceDocument document = requireOwned(userId, documentId);
        return crypto.decrypt(storage.get(document.getStorageKey()), document.getEncryptionIv(),
                document.getWrappedKey());
    }

    public byte[] readPage(UUID userId, UUID documentId, int page) {
        SourceDocument document = requireOwned(userId, documentId);
        if (!"application/pdf".equalsIgnoreCase(document.getContentType())) {
            throw new IllegalArgumentException("Page preview is available only for PDF statements.");
        }
        byte[] plaintext = crypto.decrypt(storage.get(document.getStorageKey()), document.getEncryptionIv(),
                document.getWrappedKey());
        return pageRenderer.render(plaintext, page);
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        SourceDocument document = requireOwned(userId, documentId);
        storage.delete(document.getStorageKey());
        documents.delete(document);
    }

    private SourceDocument requireOwned(UUID userId, UUID documentId) {
        return documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found."));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
