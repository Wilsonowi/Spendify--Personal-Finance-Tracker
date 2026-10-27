package com.wilson.spendify.finance;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CoverageService {
    private final SourceDocumentRepository documents;

    public CoverageService(SourceDocumentRepository documents) {
        this.documents = documents;
    }

    public List<ProviderCoverage> forYear(UUID userId, int year, LocalDate asOf) {
        LocalDate expectedStart = LocalDate.of(year, 1, 1);
        LocalDate expectedEnd = asOf.getYear() == year ? asOf : LocalDate.of(year, 12, 31);
        Map<String, MutableCoverage> byProvider = new LinkedHashMap<>();
        documents.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> item.getDocumentKind() == DocumentKind.STATEMENT)
                .filter(item -> item.getProvider() != null && item.getPeriodStart() != null && item.getPeriodEnd() != null)
                .filter(item -> item.getValidationStatus() != StatementValidationStatus.INVALID)
                .forEach(item -> byProvider.computeIfAbsent(item.getProvider(), ignored -> new MutableCoverage())
                        .include(item.getPeriodStart(), item.getPeriodEnd()));
        return byProvider.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> providerOrder(entry.getKey())))
                .map(entry -> entry.getValue().view(entry.getKey(), expectedStart, expectedEnd)).toList();
    }

    private int providerOrder(String provider) {
        return "PUBLIC_BANK".equals(provider) ? 0 : "TNG".equals(provider) ? 1 : 2;
    }

    public record ProviderCoverage(String provider, LocalDate coveredFrom, LocalDate coveredTo,
                                   LocalDate expectedFrom, LocalDate expectedTo, boolean complete,
                                   LocalDate gapStart, LocalDate gapEnd) {}

    private static final class MutableCoverage {
        private LocalDate from;
        private LocalDate to;

        private void include(LocalDate nextFrom, LocalDate nextTo) {
            from = from == null || nextFrom.isBefore(from) ? nextFrom : from;
            to = to == null || nextTo.isAfter(to) ? nextTo : to;
        }

        private ProviderCoverage view(String provider, LocalDate expectedStart, LocalDate expectedEnd) {
            LocalDate coveredFrom = from.isBefore(expectedStart) ? expectedStart : from;
            LocalDate coveredTo = to.isAfter(expectedEnd) ? expectedEnd : to;
            boolean complete = !coveredFrom.isAfter(expectedStart) && !coveredTo.isBefore(expectedEnd);
            LocalDate gapStart = coveredTo.isBefore(expectedEnd) ? coveredTo.plusDays(1) : null;
            LocalDate gapEnd = gapStart == null ? null : expectedEnd;
            return new ProviderCoverage(provider, coveredFrom, coveredTo, expectedStart, expectedEnd,
                    complete, gapStart, gapEnd);
        }
    }
}
