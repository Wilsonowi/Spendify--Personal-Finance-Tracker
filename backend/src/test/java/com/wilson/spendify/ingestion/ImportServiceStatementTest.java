package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.DocumentKind;
import com.wilson.spendify.finance.IngestionJobRepository;
import com.wilson.spendify.finance.SourceDocumentRepository;
import com.wilson.spendify.storage.DocumentCryptoService;
import com.wilson.spendify.storage.EncryptedPayload;
import com.wilson.spendify.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportServiceStatementTest {
    @Test
    void storesStatementWithRequestedDateRange() {
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        IngestionJobRepository jobs = mock(IngestionJobRepository.class);
        FileTypeValidator fileTypes = mock(FileTypeValidator.class);
        DocumentCryptoService crypto = mock(DocumentCryptoService.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        ImportService service = new ImportService(documents, jobs, fileTypes, crypto, storage,
                mock(PdfStatementPageRenderer.class));
        byte[] content = "%PDF-1.7 synthetic".getBytes();
        MockMultipartFile file = new MockMultipartFile("files", "statement.pdf", "application/pdf", content);

        when(fileTypes.validate(content, "application/pdf")).thenReturn("application/pdf");
        when(crypto.encrypt(content)).thenReturn(new EncryptedPayload(new byte[]{9}, "iv", "wrapped"));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var document = service.uploadStatement(UUID.randomUUID(), file,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(document.getDocumentKind()).isEqualTo(DocumentKind.STATEMENT);
        assertThat(document.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(document.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void rejectsNonPdfStatement() {
        ImportService service = new ImportService(mock(SourceDocumentRepository.class),
                mock(IngestionJobRepository.class), mock(FileTypeValidator.class),
                mock(DocumentCryptoService.class), mock(ObjectStorage.class),
                mock(PdfStatementPageRenderer.class));
        MockMultipartFile file = new MockMultipartFile("files", "statement.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.uploadStatement(UUID.randomUUID(), file,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }
}
