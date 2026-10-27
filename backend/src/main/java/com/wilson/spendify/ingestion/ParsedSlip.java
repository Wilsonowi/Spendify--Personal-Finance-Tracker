package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.TransactionKind;

import java.math.BigDecimal;
import java.time.Instant;

public record ParsedSlip(String provider, Direction direction, TransactionKind kind,
                         BigDecimal amount, Instant occurredAt, String merchant,
                         String reference, BigDecimal confidence, String evidence) {}

