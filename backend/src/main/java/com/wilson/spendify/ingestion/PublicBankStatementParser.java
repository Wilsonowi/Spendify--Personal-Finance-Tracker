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
public class PublicBankStatementParser implements StatementProviderParser {
    private static final Pattern STATEMENT_DATE = Pattern.compile(
            "(?i)(?:statement date|tarikh penyata)\\s+(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})");
    private static final Pattern ROW_DATE = Pattern.compile("^(\\d{2})/(\\d{2})\\s+(.*)$");
    private static final Pattern MONEY = Pattern.compile("(?<![A-Z0-9])([0-9][0-9,]*\\.\\d{2})(?![A-Z0-9])");
    private static final DateTimeFormatter STATEMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Override
    public boolean supports(List<String> pages) {
        String text = String.join("\n", pages).toLowerCase(Locale.ROOT);
        return text.contains("public bank") || text.contains("public islamic bank")
                || text.contains("pbe bank");
    }

    @Override
    public StatementParseResult parse(List<String> pages, LocalDate from, LocalDate to) {
        String allText = String.join("\n", pages);
        LocalDate statementDate = statementDate(allText);
        List<MutableRow> parsed = new ArrayList<>();
        LocalDate currentDate = null;
        BigDecimal previousBalance = null;
        int sequence = 0;

        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            ColumnPositions columns = columns(pages.get(pageIndex));
            for (String raw : pages.get(pageIndex).lines().toList()) {
                String line = raw.stripTrailing();
                Matcher dated = ROW_DATE.matcher(line.stripLeading());
                String body = line.stripLeading();
                if (dated.matches()) {
                    int day = Integer.parseInt(dated.group(1));
                    int month = Integer.parseInt(dated.group(2));
                    int year = month > statementDate.getMonthValue()
                            ? statementDate.getYear() - 1 : statementDate.getYear();
                    currentDate = LocalDate.of(year, month, day);
                    body = dated.group(3);
                }

                String normalized = body.toLowerCase(Locale.ROOT);
                if (normalized.contains("balance from last statement") || normalized.contains("balance b/f")
                        || normalized.contains("balance c/f") || normalized.contains("balance carried")) {
                    List<MoneyToken> balanceValues = moneyTokens(body, line.indexOf(body));
                    if (!balanceValues.isEmpty()) previousBalance = balanceValues.getLast().value();
                    continue;
                }

                List<MoneyToken> amounts = moneyTokens(body, line.indexOf(body));
                if (currentDate != null && amounts.size() >= 2) {
                    MoneyToken transactionAmount = amounts.get(amounts.size() - 2);
                    MoneyToken balance = amounts.getLast();
                    String description = body.substring(0, Math.max(0,
                            transactionAmount.start() - line.indexOf(body))).strip();
                    if (description.isBlank()) description = "Statement transaction";
                    Direction direction = directionFromBalances(previousBalance, transactionAmount.value(),
                            balance.value(), columns.directionFor(transactionAmount.start(), description));
                    if (direction == null) {
                        if (!parsed.isEmpty()) parsed.getLast().appendDescription(description);
                        continue;
                    }
                    parsed.add(new MutableRow(++sequence, pageIndex + 1, currentDate, direction,
                            transactionAmount.value(), balance.value(), description));
                    previousBalance = balance.value();
                } else if (!parsed.isEmpty() && amounts.isEmpty() && isDescriptionContinuation(body)) {
                    parsed.getLast().appendDescription(body.strip());
                }
            }
        }

        StatementValidationStatus status = validateSummary(allText, parsed);
        String message = status == StatementValidationStatus.VALID
                ? "Statement totals and transaction counts match."
                : "Parsed rows require review because statement totals or counts do not match.";
        List<StatementRow> rows = parsed.stream()
                .filter(row -> !row.date.isBefore(from) && !row.date.isAfter(to))
                .map(MutableRow::toRow).toList();
        LocalDate periodStart = parsed.stream().map(row -> row.date).min(LocalDate::compareTo).orElse(from);
        LocalDate periodEnd = parsed.stream().map(row -> row.date).max(LocalDate::compareTo).orElse(to);
        return new StatementParseResult("PUBLIC_BANK", periodStart, periodEnd, rows, status, message);
    }

    private Direction directionFromBalances(BigDecimal previousBalance, BigDecimal amount,
                                            BigDecimal nextBalance, Direction fallback) {
        if (previousBalance == null) return fallback;
        if (previousBalance.subtract(amount).compareTo(nextBalance) == 0) return Direction.DEBIT;
        if (previousBalance.add(amount).compareTo(nextBalance) == 0) return Direction.CREDIT;
        return null;
    }

    private LocalDate statementDate(String text) {
        Matcher matcher = STATEMENT_DATE.matcher(text);
        if (!matcher.find()) throw new IllegalArgumentException("The Public Bank statement date could not be read.");
        return LocalDate.parse(matcher.group(1), STATEMENT_DATE_FORMAT);
    }

    private ColumnPositions columns(String page) {
        for (String line : page.lines().toList()) {
            String upper = line.toUpperCase(Locale.ROOT);
            int debit = upper.indexOf("DEBIT");
            int credit = upper.indexOf("CREDIT");
            int balance = upper.indexOf("BALANCE");
            if (debit >= 0 && credit > debit && balance > credit) return new ColumnPositions(debit, credit);
        }
        return new ColumnPositions(48, 62);
    }

    private List<MoneyToken> moneyTokens(String body, int bodyOffset) {
        List<MoneyToken> values = new ArrayList<>();
        Matcher matcher = MONEY.matcher(body);
        while (matcher.find()) {
            values.add(new MoneyToken(new BigDecimal(matcher.group(1).replace(",", "")),
                    bodyOffset + matcher.start(1)));
        }
        return values;
    }

    private boolean isDescriptionContinuation(String body) {
        if (body.isBlank()) return false;
        String normalized = body.toLowerCase(Locale.ROOT);
        return !normalized.startsWith("date") && !normalized.startsWith("tarikh")
                && !normalized.startsWith("baki harian")
                && !normalized.startsWith("daily balance")
                && !normalized.contains("statement of account") && !normalized.contains("page ");
    }

    private StatementValidationStatus validateSummary(String text, List<MutableRow> rows) {
        BigDecimal expectedDebits = summaryMoney(text, "total\\s+debits");
        BigDecimal expectedCredits = summaryMoney(text, "total\\s+credits");
        Integer expectedDebitCount = summaryCount(text, "no\\.\\s+of\\s+debits");
        Integer expectedCreditCount = summaryCount(text, "no\\.\\s+of\\s+credits");
        if (expectedDebits == null && expectedCredits == null) return StatementValidationStatus.WARNING;

        List<MutableRow> debits = rows.stream().filter(row -> row.direction == Direction.DEBIT).toList();
        List<MutableRow> credits = rows.stream().filter(row -> row.direction == Direction.CREDIT).toList();
        BigDecimal debitTotal = debits.stream().map(row -> row.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditTotal = credits.stream().map(row -> row.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean totalsMatch = (expectedDebits == null || expectedDebits.compareTo(debitTotal) == 0)
                && (expectedCredits == null || expectedCredits.compareTo(creditTotal) == 0);
        boolean countsMatch = (expectedDebitCount == null || expectedDebitCount == debits.size())
                && (expectedCreditCount == null || expectedCreditCount == credits.size());
        return totalsMatch && countsMatch ? StatementValidationStatus.VALID : StatementValidationStatus.WARNING;
    }

    private BigDecimal summaryMoney(String text, String label) {
        Matcher matcher = Pattern.compile("(?i)" + label + "\\s+([0-9,]+\\.\\d{2})").matcher(text);
        return matcher.find() ? new BigDecimal(matcher.group(1).replace(",", "")) : null;
    }

    private Integer summaryCount(String text, String label) {
        Matcher matcher = Pattern.compile("(?i)" + label + "\\s+(\\d+)").matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private record MoneyToken(BigDecimal value, int start) {}

    private record ColumnPositions(int debit, int credit) {
        Direction directionFor(int amountStart, String description) {
            String normalized = description.toLowerCase(Locale.ROOT);
            if (normalized.contains("debit") || normalized.endsWith(" dr")) return Direction.DEBIT;
            if (normalized.contains("credit") || normalized.endsWith(" cr")) return Direction.CREDIT;
            return amountStart < (debit + credit) / 2 ? Direction.DEBIT : Direction.CREDIT;
        }
    }

    private static final class MutableRow {
        private final int sequence;
        private final int page;
        private final LocalDate date;
        private final Direction direction;
        private final BigDecimal amount;
        private final BigDecimal balance;
        private String description;

        private MutableRow(int sequence, int page, LocalDate date, Direction direction, BigDecimal amount,
                           BigDecimal balance, String description) {
            this.sequence = sequence;
            this.page = page;
            this.date = date;
            this.direction = direction;
            this.amount = amount;
            this.balance = balance;
            this.description = description;
        }

        private void appendDescription(String value) {
            if (description.length() < 140) description = (description + " " + value).strip();
        }

        private StatementRow toRow() {
            boolean topUp = direction == Direction.DEBIT && isWalletTopUp(description);
            TransactionKind kind = topUp ? TransactionKind.TRANSFER_PENDING
                    : direction == Direction.DEBIT ? TransactionKind.EXPENSE : TransactionKind.INCOME;
            String note = topUp ? "Recognized bank-to-wallet top-up" : null;
            String merchant = cleanDescription(description);
            return new StatementRow(sequence, page, date, DatePrecision.DATE_ONLY, direction, kind,
                    amount, balance, truncate(merchant, 160), null, new BigDecimal("0.970"), note,
                    direction == Direction.DEBIT ? "BANK_DEBIT" : "BANK_CREDIT");
        }

        private static String cleanDescription(String value) {
            String cleaned = value.replaceFirst("(?i)\\s+Baki Harian.*$", "").strip();
            cleaned = cleaned.replaceFirst(
                    "(?i)^PB\\s+DEBIT\\s+CARD\\s+DR\\s+(?:(?:VISA|MASTERCARD)\\s*)?[A-Z0-9]{6,}\\s*",
                    "").strip();
            return cleaned.isBlank() ? value.strip() : cleaned;
        }

        private static boolean isWalletTopUp(String value) {
            String normalized = value.toLowerCase(Locale.ROOT);
            return normalized.contains("tng") || normalized.contains("touch n go")
                    || normalized.contains("touch 'n go") || normalized.contains("ewallet reload")
                    || normalized.contains("wallet top up") || normalized.contains("wallet top-up");
        }

        private static String truncate(String value, int length) {
            return value.length() <= length ? value : value.substring(0, length);
        }
    }
}
