package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.TransactionKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SlipParser {
    private static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?im)^\\s*(?:transaction\\s+amount|top\\s*up\\s+amount|total\\s+amount|amount\\s+(?:paid|transferred|sent|received)|(?:paid|payment|transfer)\\s+amount|amount|total)\\s*[:=]?\\s*(?:(?:RM|MYR)\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*$");
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile(
            "(?i)(?<![A-Z0-9])(?:RM|MYR)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![0-9])");
    private static final Pattern DATE = Pattern.compile(
            "(?im)^\\s*(?:transaction\\s+date|date\\s*&\\s*time|date)\\s*:?\\s*(.*?)\\s*$");
    private static final Pattern WALLET_REFERENCE = Pattern.compile(
            "(?im)^\\s*eWallet\\s+Reference\\s+No\\.?\\s*:?\\s*(.*?)\\s*$");
    private static final Pattern DUITNOW_REFERENCE = Pattern.compile(
            "(?im)^\\s*DuitNow\\s+(?:Reference|Ref)\\s+No\\.?\\s*:?\\s*(.*?)\\s*$");
    private static final Pattern REFERENCE = Pattern.compile(
            "(?im)^\\s*(?:reference(?:\\s+no)?|transaction\\s+id|ref)\\s*:?\\s*(.*?)\\s*$");
    private static final Pattern MERCHANT = Pattern.compile(
            "(?im)^\\s*(?:merchant|operator|paid\\s+to|description|recipient\\s+reference|recipient\\s+name|recipient(?!\\s+(?:account|bank|reference|name)))\\s*:?\\s*(.{2,160}?)\\s*$");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a", Locale.ENGLISH)
    };

    public ParsedSlip parse(String text, double ocrConfidence) {
        String provider = detectProvider(text);
        BigDecimal amount = extractAmount(text);
        Instant occurredAt = parseDate(extractField(DATE, text, 1));
        String merchant = clean(extract(MERCHANT, text));
        String reference = clean(extractReference(text));
        Direction direction = detectDirection(provider, text);
        TransactionKind kind = direction == Direction.DEBIT ? TransactionKind.EXPENSE : TransactionKind.INCOME;
        BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, ocrConfidence))).setScale(3,
                RoundingMode.HALF_UP);
        String evidence = "provider=" + provider + "; amount=" + amount + "; reference="
                + (reference == null ? "unavailable" : "present");
        return new ParsedSlip(provider, direction, kind, amount, occurredAt, merchant,
                reference, confidence, evidence);
    }

    private String detectProvider(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        String heading = normalized.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        if (heading.contains("tng") || heading.contains("touch n go") || heading.contains("touch 'n go")) {
            return "TNG";
        }
        if (heading.contains("public bank") || heading.contains("pbe bank")) return "PUBLIC_BANK";
        if (normalized.contains("public bank") || normalized.contains("pbe bank")) return "PUBLIC_BANK";
        if (normalized.contains("touch 'n go") || normalized.contains("touch n go")
                || normalized.contains("tng ewallet")) return "TNG";
        if (normalized.contains("ewallet reference no") && normalized.contains("ewallet balance")
                && normalized.contains("operator")) return "TNG";
        throw new IllegalArgumentException("The slip is not recognized as Public Bank or TNG eWallet.");
    }

    private Direction detectDirection(String provider, String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (provider.equals("TNG") && (normalized.contains("top up") || normalized.contains("reload"))) {
            return Direction.CREDIT;
        }
        if (normalized.contains("money received") || normalized.contains("credit transaction")) {
            return Direction.CREDIT;
        }
        return Direction.DEBIT;
    }

    private Instant parseDate(String raw) {
        if (raw == null) throw new IllegalArgumentException("The transaction date could not be extracted.");
        String normalized = raw.trim().replaceAll(
                "(\\d{1,2}/\\d{1,2}/\\d{4})(?=\\d{1,2}:\\d{2})", "$1 ");
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(normalized, format).atZone(MALAYSIA).toInstant();
            } catch (DateTimeParseException ignored) {
                // Try the next known provider format.
            }
        }
        throw new IllegalArgumentException("Unsupported transaction date format: " + raw);
    }

    private String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractReference(String text) {
        String value = extractField(DUITNOW_REFERENCE, text, 2);
        if (value == null) value = extractField(WALLET_REFERENCE, text, 2);
        if (value == null) value = extractField(REFERENCE, text, 1);
        return value == null ? null : value.replaceAll("\\s+", "");
    }

    private String extractField(Pattern pattern, String text, int continuationLines) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            Matcher matcher = pattern.matcher(lines[index]);
            if (!matcher.matches()) continue;
            String value = matcher.group(1).trim();
            for (int next = index + 1, used = 0; value.isBlank() || used < continuationLines;
                 next++, used++) {
                if (next >= lines.length) break;
                String continuation = lines[next].trim();
                if (continuation.isEmpty()) continue;
                if (isFieldLabel(continuation)) break;
                value = value.isBlank() ? continuation : value + " " + continuation;
                if (!value.isBlank() && (pattern == DATE || continuationLines == 1)) break;
            }
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private boolean isFieldLabel(String line) {
        return line.matches("(?i)^(?:payment\\s+result|amount|total|date(?:\\s*&\\s*time)?|transaction\\s+date|"
                + "(?:eWallet|DuitNow)?\\s*(?:reference|ref)(?:\\s+no\\.?)?|transaction\\s+id|"
                + "duitnow\\s+status\\s+code|transfer\\s+method|payment\\s+method|operator|"
                + "recipient(?:\\s+(?:reference|name|account|bank))?|from\\s+account)\\s*:?$");
    }

    private BigDecimal extractAmount(String text) {
        String labeledAmount = extract(AMOUNT, text);
        if (labeledAmount != null) return parseAmount(labeledAmount);

        Matcher matcher = CURRENCY_AMOUNT.matcher(text);
        java.util.Map<BigDecimal, BigDecimal> candidates = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            BigDecimal amount = parseAmount(matcher.group(1));
            candidates.putIfAbsent(amount.stripTrailingZeros(), amount);
        }
        if (candidates.size() == 1) return candidates.values().iterator().next();
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("The transaction amount is ambiguous; review the slip manually.");
        }
        throw new IllegalArgumentException("The transaction amount could not be extracted.");
    }

    private BigDecimal parseAmount(String value) {
        return new BigDecimal(value.replace(",", "")).setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip().replaceAll("[\\p{Cntrl}]", "");
    }
}
