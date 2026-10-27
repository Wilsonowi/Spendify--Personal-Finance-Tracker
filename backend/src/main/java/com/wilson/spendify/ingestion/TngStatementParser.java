package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.DatePrecision;
import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.StatementValidationStatus;
import com.wilson.spendify.finance.TransactionKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TngStatementParser implements StatementProviderParser {
    private static final Pattern ROW = Pattern.compile(
            "^(\\d{1,2}/\\d{1,2}/\\d{4})\\s+(Success|Failed|Unsuccessful)\\s+(.+?)\\s+RM([0-9,]+\\.\\d{2})\\s+RM([0-9,]+\\.\\d{2})\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final Pattern REFERENCE_MERCHANT_DETAILS =
            Pattern.compile("^(\\S+)\\s+(.+)\\s+(\\S+)$");
    private static final List<String> TYPES = List.of("Balance Top Up", "DuitNow QR TNGD", "DuitNow QR",
            "Receive from Wallet", "Transfer to Wallet", "Payment Refund", "Cashback", "Reload",
            "Online Payment", "Toll Payment", "Parking Payment", "Retail Payment", "DUITNOW_RECEI",
            "DUITNOW_TRANS", "Refund", "Payment");

    @Override
    public boolean supports(List<String> pages) {
        String heading = pages.isEmpty() ? "" : pages.getFirst().toLowerCase(Locale.ROOT);
        return heading.contains("tng wallet transaction history") || heading.contains("touch 'n go ewallet");
    }

    @Override
    public StatementParseResult parse(List<String> pages, LocalDate from, LocalDate to) {
        List<StatementRow> allRows = new ArrayList<>();
        int sequence = 0;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            for (String raw : pages.get(pageIndex).lines().toList()) {
                Matcher matcher = ROW.matcher(raw.strip());
                if (!matcher.matches() || !matcher.group(2).equalsIgnoreCase("Success")) continue;
                LocalDate date = LocalDate.parse(matcher.group(1), DATE);
                String middle = matcher.group(3).strip();
                String type = detectType(middle);
                String remainder = middle.substring(Math.min(type.length(), middle.length())).strip();
                ParsedFields fields = parseFields(remainder, type);
                String reference = fields.reference();
                String merchant = fields.merchant();
                BigDecimal amount = money(matcher.group(4));
                BigDecimal balance = money(matcher.group(5));
                Direction direction = direction(type);
                TransactionKind kind = kind(type, direction);
                String note = type.toLowerCase(Locale.ROOT).contains("top up") ? "Possible wallet top-up" : null;
                allRows.add(new StatementRow(++sequence, pageIndex + 1, date, DatePrecision.DATE_ONLY,
                        direction, kind, amount, balance, merchant, reference, new BigDecimal("0.980"), note, type));
            }
        }

        allRows = inferDirections(allRows);
        boolean balancesMatch = balancesMatch(allRows);
        StatementValidationStatus status = balancesMatch
                ? StatementValidationStatus.VALID : StatementValidationStatus.WARNING;
        String message = balancesMatch ? "Wallet balance continuity matches."
                : "One or more wallet balance changes do not match the transaction amount.";
        List<StatementRow> rows = allRows.stream()
                .filter(row -> !row.occurredOn().isBefore(from) && !row.occurredOn().isAfter(to)).toList();
        LocalDate periodStart = allRows.stream().map(StatementRow::occurredOn)
                .min(LocalDate::compareTo).orElse(from);
        LocalDate periodEnd = allRows.stream().map(StatementRow::occurredOn)
                .max(LocalDate::compareTo).orElse(to);
        return new StatementParseResult("TNG", periodStart, periodEnd, rows, status, message);
    }

    private List<StatementRow> inferDirections(List<StatementRow> rows) {
        if (rows.size() < 2) return rows;
        List<StatementRow> inferred = new ArrayList<>(rows);
        boolean descending = rows.getFirst().occurredOn().isAfter(rows.getLast().occurredOn());
        if (descending) {
            for (int index = 0; index < rows.size() - 1; index++) {
                StatementRow newer = inferred.get(index);
                StatementRow older = inferred.get(index + 1);
                Direction direction = directionFromChange(older.balance(), newer.balance(), newer.amount());
                if (direction != null) inferred.set(index, withDirection(newer, direction));
            }
        } else {
            for (int index = 1; index < rows.size(); index++) {
                StatementRow previous = inferred.get(index - 1);
                StatementRow current = inferred.get(index);
                Direction direction = directionFromChange(previous.balance(), current.balance(), current.amount());
                if (direction != null) inferred.set(index, withDirection(current, direction));
            }
        }
        return inferred;
    }

    private Direction directionFromChange(BigDecimal before, BigDecimal after, BigDecimal amount) {
        if (before.add(amount).compareTo(after) == 0) return Direction.CREDIT;
        if (before.subtract(amount).compareTo(after) == 0) return Direction.DEBIT;
        return null;
    }

    private StatementRow withDirection(StatementRow row, Direction direction) {
        boolean conflictsWithProviderType = row.direction() != direction;
        TransactionKind kind = row.kind() == TransactionKind.REFUND
                ? TransactionKind.REFUND
                : row.kind() == TransactionKind.TRANSFER_PENDING ? TransactionKind.TRANSFER_PENDING
                : direction == Direction.DEBIT ? TransactionKind.EXPENSE : TransactionKind.INCOME;
        String note = conflictsWithProviderType
                ? appendNote(row.reviewNote(), "Warning: provider type conflicts with wallet balance movement.")
                : row.reviewNote();
        return new StatementRow(row.sequence(), row.sourcePage(), row.occurredOn(), row.datePrecision(),
                direction, kind, row.amount(), row.balance(), row.merchant(), row.reference(),
                row.confidence(), note, row.providerTransactionType());
    }

    private String detectType(String value) {
        for (String type : TYPES) {
            if (value.regionMatches(true, 0, type, 0, type.length())) return type;
        }
        int separator = value.indexOf("  ");
        return separator > 0 ? value.substring(0, separator).strip() : value;
    }

    private ParsedFields parseFields(String remainder, String fallbackMerchant) {
        Matcher collapsed = REFERENCE_MERCHANT_DETAILS.matcher(remainder);
        if (collapsed.matches()) {
            return new ParsedFields(truncate(collapsed.group(1), 120),
                    truncate(collapsed.group(2).strip(), 160));
        }
        String[] fields = remainder.split("\\s{2,}");
        String reference = fields.length > 0 ? truncate(fields[0].strip(), 120) : null;
        String merchant = fields.length > 1 ? truncate(fields[1].strip(), 160) : fallbackMerchant;
        return new ParsedFields(reference, merchant);
    }

    private Direction direction(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.contains("top up") || normalized.contains("reload")
                || normalized.contains("receive") || normalized.contains("refund")
                || normalized.contains("cashback") ? Direction.CREDIT : Direction.DEBIT;
    }

    private TransactionKind kind(String type, Direction direction) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("refund") || normalized.contains("cashback")) return TransactionKind.REFUND;
        if (normalized.contains("top up") || normalized.contains("reload")) {
            return TransactionKind.TRANSFER_PENDING;
        }
        return direction == Direction.DEBIT ? TransactionKind.EXPENSE : TransactionKind.INCOME;
    }

    private String appendNote(String current, String addition) {
        return current == null || current.isBlank() ? addition : current + " " + addition;
    }

    private boolean balancesMatch(List<StatementRow> rows) {
        boolean descending = rows.size() > 1
                && rows.getFirst().occurredOn().isAfter(rows.getLast().occurredOn());
        for (int index = 1; index < rows.size(); index++) {
            StatementRow previous = rows.get(index - 1);
            StatementRow current = rows.get(index);
            BigDecimal expected;
            BigDecimal actual;
            if (descending) {
                expected = previous.direction() == Direction.CREDIT
                        ? current.balance().add(previous.amount()) : current.balance().subtract(previous.amount());
                actual = previous.balance();
            } else {
                expected = current.direction() == Direction.CREDIT
                        ? previous.balance().add(current.amount()) : previous.balance().subtract(current.amount());
                actual = current.balance();
            }
            if (expected.compareTo(actual) != 0) return false;
        }
        return true;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value.replace(",", ""));
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ParsedFields(String reference, String merchant) {}
}
