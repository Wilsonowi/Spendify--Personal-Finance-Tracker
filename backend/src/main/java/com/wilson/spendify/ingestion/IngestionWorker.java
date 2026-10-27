package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.CategorizationService;
import com.wilson.spendify.finance.FinanceTransaction;
import com.wilson.spendify.finance.FinanceTransactionRepository;
import com.wilson.spendify.finance.FinancialAccount;
import com.wilson.spendify.finance.FinancialAccountRepository;
import com.wilson.spendify.finance.IngestionJob;
import com.wilson.spendify.finance.IngestionJobRepository;
import com.wilson.spendify.finance.JobStatus;
import com.wilson.spendify.finance.SourceDocument;
import com.wilson.spendify.finance.SourceDocumentRepository;
import com.wilson.spendify.storage.DocumentCryptoService;
import com.wilson.spendify.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Component
public class IngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionJobRepository jobs;
    private final SourceDocumentRepository documents;
    private final FinancialAccountRepository accounts;
    private final FinanceTransactionRepository transactions;
    private final DocumentCryptoService crypto;
    private final ObjectStorage storage;
    private final OcrClient ocr;
    private final SlipParser parser;
    private final CategorizationService categorization;
    private final StatementIngestionService statements;
    private final TransactionTemplate transactionsTemplate;

    public IngestionWorker(IngestionJobRepository jobs, SourceDocumentRepository documents,
                           FinancialAccountRepository accounts, FinanceTransactionRepository transactions,
                           DocumentCryptoService crypto, ObjectStorage storage, OcrClient ocr,
                           SlipParser parser, CategorizationService categorization,
                           StatementIngestionService statements,
                           TransactionTemplate transactionsTemplate) {
        this.jobs = jobs;
        this.documents = documents;
        this.accounts = accounts;
        this.transactions = transactions;
        this.crypto = crypto;
        this.storage = storage;
        this.ocr = ocr;
        this.parser = parser;
        this.categorization = categorization;
        this.statements = statements;
        this.transactionsTemplate = transactionsTemplate;
    }

    @Scheduled(fixedDelayString = "${spendify.ingestion.poll-delay-ms:2000}")
    public void poll() {
        List<IngestionJob> pending = jobs.findTop5ByStatusAndAvailableAtBeforeOrderByCreatedAt(
                JobStatus.PENDING, Instant.now());
        pending.forEach(job -> {
            try {
                transactionsTemplate.executeWithoutResult(status -> process(job.getId()));
            } catch (RuntimeException ex) {
                log.warn("Ingestion transaction {} failed with {}", job.getId(), ex.getClass().getSimpleName());
            }
        });
    }

    void process(java.util.UUID jobId) {
        IngestionJob job = jobs.findById(jobId).orElseThrow();
        if (job.getStatus() != JobStatus.PENDING) return;
        SourceDocument document = documents.findById(job.getDocumentId()).orElseThrow();
        job.start();
        document.processing();
        try {
            byte[] plaintext = crypto.decrypt(storage.get(document.getStorageKey()),
                    document.getEncryptionIv(), document.getWrappedKey());
            if (document.getDocumentKind() == com.wilson.spendify.finance.DocumentKind.STATEMENT) {
                statements.ingest(document, plaintext);
                job.complete();
                return;
            }
            OcrClient.OcrResult result = ocr.extract(plaintext, document.getContentType(), document.getOriginalName());
            ParsedSlip parsed = parser.parse(result.text(), result.confidence());
            FinancialAccount account = accounts.findByUserIdAndProvider(document.getUserId(), parsed.provider())
                    .orElseThrow(() -> new IllegalArgumentException("No account is configured for " + parsed.provider()));
            CategorizationService.Suggestion suggestion = categorization.suggest(document.getUserId(), parsed.merchant());

            FinanceTransaction draft = new FinanceTransaction(document.getUserId(), account.getId(), document.getId(),
                    parsed.direction(), parsed.kind(), parsed.amount(), parsed.occurredAt(),
                    suggestion.canonicalMerchant(), parsed.reference(), parsed.confidence(), parsed.evidence());
            draft.revise(account.getId(), suggestion.categoryId(), parsed.direction(), parsed.kind(), parsed.amount(),
                    parsed.occurredAt(), suggestion.canonicalMerchant(), parsed.reference());
            draft.requireReview(com.wilson.spendify.finance.ReviewReason.SLIP_REVIEW_REQUIRED);
            transactions.save(draft);
            document.needsReview(parsed.provider());
            job.complete();
        } catch (RuntimeException ex) {
            String message = sanitizeError(ex.getMessage());
            document.failed(message);
            job.fail(message);
            log.warn("Ingestion job {} failed with {}", jobId, ex.getClass().getSimpleName());
        }
    }

    private String sanitizeError(String message) {
        if (message == null || message.isBlank()) return "Document processing failed.";
        String sanitized = message.replaceAll("[\\r\\n]+", " ");
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }
}
