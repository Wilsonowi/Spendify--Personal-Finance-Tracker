package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.CategorizationService;
import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.FinanceTransaction;
import com.wilson.spendify.finance.FinanceTransactionRepository;
import com.wilson.spendify.finance.FinancialAccount;
import com.wilson.spendify.finance.FinancialAccountRepository;
import com.wilson.spendify.finance.AccountType;
import com.wilson.spendify.finance.IngestionJob;
import com.wilson.spendify.finance.IngestionJobRepository;
import com.wilson.spendify.finance.ReviewReason;
import com.wilson.spendify.finance.SourceDocument;
import com.wilson.spendify.finance.SourceDocumentRepository;
import com.wilson.spendify.finance.TransactionKind;
import com.wilson.spendify.storage.DocumentCryptoService;
import com.wilson.spendify.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngestionWorkerTest {
    @Test
    void slipDraftIsMarkedForRequiredManualReview() {
        IngestionJobRepository jobs = mock(IngestionJobRepository.class);
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        FinancialAccountRepository accounts = mock(FinancialAccountRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        DocumentCryptoService crypto = mock(DocumentCryptoService.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        OcrClient ocr = mock(OcrClient.class);
        SlipParser parser = mock(SlipParser.class);
        CategorizationService categorization = mock(CategorizationService.class);
        UUID userId = UUID.randomUUID();
        SourceDocument document = new SourceDocument(userId, "synthetic.png", "image/png", 128,
                "a".repeat(64), "objects/synthetic", "iv", "wrapped");
        IngestionJob job = new IngestionJob(document.getId());
        FinancialAccount account = new FinancialAccount(userId, "TNG", "TNG eWallet", AccountType.EWALLET);
        UUID categoryId = UUID.randomUUID();
        ParsedSlip parsed = new ParsedSlip("TNG", Direction.DEBIT, TransactionKind.EXPENSE,
                new BigDecimal("12.50"), Instant.parse("2026-07-15T04:00:00Z"),
                "Synthetic Merchant", "SYN-1", new BigDecimal("0.990"), "synthetic");
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(storage.get(document.getStorageKey())).thenReturn(new byte[]{1});
        when(crypto.decrypt(new byte[]{1}, document.getEncryptionIv(), document.getWrappedKey()))
                .thenReturn(new byte[]{2});
        when(ocr.extract(new byte[]{2}, document.getContentType(), document.getOriginalName()))
                .thenReturn(new OcrClient.OcrResult("synthetic", 0.99));
        when(parser.parse("synthetic", 0.99)).thenReturn(parsed);
        when(accounts.findByUserIdAndProvider(userId, "TNG")).thenReturn(Optional.of(account));
        when(categorization.suggest(userId, "Synthetic Merchant"))
                .thenReturn(new CategorizationService.Suggestion(categoryId, "Synthetic Merchant", 0.85, "keyword"));
        ArgumentCaptor<FinanceTransaction> saved = ArgumentCaptor.forClass(FinanceTransaction.class);
        when(transactions.save(saved.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        IngestionWorker worker = new IngestionWorker(jobs, documents, accounts, transactions, crypto,
                storage, ocr, parser, categorization, mock(StatementIngestionService.class), null);

        worker.process(job.getId());

        assertThat(saved.getValue().getReviewReason()).isEqualTo(ReviewReason.SLIP_REVIEW_REQUIRED);
    }
}
