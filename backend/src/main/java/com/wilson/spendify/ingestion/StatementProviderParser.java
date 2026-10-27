package com.wilson.spendify.ingestion;

import java.time.LocalDate;
import java.util.List;

public interface StatementProviderParser {
    boolean supports(List<String> pages);
    StatementParseResult parse(List<String> pages, LocalDate from, LocalDate to);
}
