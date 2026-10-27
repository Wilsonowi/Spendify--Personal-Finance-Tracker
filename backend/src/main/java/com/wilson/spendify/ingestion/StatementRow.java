package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.DatePrecision;
import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.TransactionKind;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementRow(int sequence, int sourcePage, LocalDate occurredOn, DatePrecision datePrecision,
                           Direction direction, TransactionKind kind, BigDecimal amount, BigDecimal balance,
                           String merchant, String reference, BigDecimal confidence, String reviewNote,
                           String providerTransactionType) {}
