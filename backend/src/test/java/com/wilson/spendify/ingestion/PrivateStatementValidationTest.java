package com.wilson.spendify.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.wilson.spendify.finance.StatementValidationStatus;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SPENDIFY_PRIVATE_STATEMENT_ROOT", matches = ".+")
class PrivateStatementValidationTest {
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Test
    void validatesPrivateStatementsWithoutLoggingFinancialContent() throws Exception {
        Path root = Path.of(System.getenv("SPENDIFY_PRIVATE_STATEMENT_ROOT"));
        PdfStatementTextExtractor extractor = new PdfStatementTextExtractor();
        List<StatementProviderParser> parsers = List.of(
                new PublicBankStatementParser(), new TngStatementParser());
        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        Map<String, Integer> validationCounts = new LinkedHashMap<>();
        List<Path> files = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .filter(path -> path.toString().contains("2026")
                            || path.getFileName().toString().toLowerCase().contains("tng"))
                    .sorted()
                    .forEach(files::add);
        }

        assertThat(files).as("private 2026 statement PDFs").isNotEmpty();
        for (Path file : files) {
            List<String> pages = extractor.extract(Files.readAllBytes(file));
            StatementProviderParser parser = parsers.stream().filter(candidate -> candidate.supports(pages))
                    .findFirst().orElseThrow(() -> new AssertionError("Unsupported private statement layout"));
            StatementParseResult result = parser.parse(pages, FROM, TO);
            assertThat(result.rows()).as("parsed rows for provider " + result.provider()).isNotEmpty();
            assertThat(result.rows()).allSatisfy(row -> {
                assertThat(row.occurredOn()).isBetween(FROM, TO);
                assertThat(row.amount().signum()).isPositive();
            });
            assertThat(result.rows()).extracting(StatementRow::sequence).doesNotHaveDuplicates();
            assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
            rowCounts.merge(result.provider(), result.rows().size(), Integer::sum);
            validationCounts.merge(result.provider() + ":" + result.validationStatus(), 1, Integer::sum);
        }

        rowCounts.forEach((provider, count) ->
                System.out.printf("Private validation: provider=%s rows=%d%n", provider, count));
        validationCounts.forEach((key, count) ->
                System.out.printf("Private validation: %s documents=%d%n", key, count));
    }

}
