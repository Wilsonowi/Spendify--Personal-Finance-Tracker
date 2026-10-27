package com.wilson.spendify.finance;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoverageServiceTest {
    @Test
    void reportsProviderGapAfterLatestStatementCutoff() {
        SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
        CoverageService service = new CoverageService(documents);
        UUID userId = UUID.randomUUID();
        SourceDocument publicBank = statement(userId, "PUBLIC_BANK",
                LocalDate.of(2025, 12, 20), LocalDate.of(2026, 7, 20));
        SourceDocument tng = statement(userId, "TNG",
                LocalDate.of(2025, 8, 8), LocalDate.of(2026, 8, 7));
        when(documents.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(publicBank, tng));

        var coverage = service.forYear(userId, 2026, LocalDate.of(2026, 8, 7));

        assertThat(coverage).extracting(CoverageService.ProviderCoverage::provider)
                .containsExactly("PUBLIC_BANK", "TNG");
        assertThat(coverage.getFirst().complete()).isFalse();
        assertThat(coverage.getFirst().gapStart()).isEqualTo(LocalDate.of(2026, 7, 21));
        assertThat(coverage.get(1).complete()).isTrue();
    }

    private SourceDocument statement(UUID userId, String provider, LocalDate from, LocalDate to) {
        SourceDocument document = new SourceDocument(userId, provider + ".pdf", "application/pdf", 200,
                UUID.randomUUID().toString().replace("-", "").repeat(2), "objects/" + provider, "iv", "wrapped");
        document.configureStatement(from, to);
        document.needsStatementReview(provider, 1, StatementValidationStatus.VALID, "valid");
        return document;
    }
}
