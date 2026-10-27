package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.StatementValidationStatus;

import java.time.LocalDate;
import java.util.List;

public record StatementParseResult(String provider, LocalDate periodStart, LocalDate periodEnd,
                                   List<StatementRow> rows, StatementValidationStatus validationStatus,
                                   String validationMessage) {}
