package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.Direction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlipParserTest {
    private final SlipParser parser = new SlipParser();

    @Test
    void parsesPublicBankTopUpAsDebit() {
        ParsedSlip parsed = parser.parse("""
                PUBLIC BANK
                Description: TNG eWallet Top Up
                Transaction Amount: RM 100.00
                Transaction Date: 2026-07-10 14:30
                Reference No: PB-TNG-7812
                Recipient: Touch n Go eWallet
                """, 0.94);

        assertThat(parsed.provider()).isEqualTo("PUBLIC_BANK");
        assertThat(parsed.direction()).isEqualTo(Direction.DEBIT);
        assertThat(parsed.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(parsed.reference()).isEqualTo("PB-TNG-7812");
    }

    @Test
    void parsesTngTopUpAsCredit() {
        ParsedSlip parsed = parser.parse("""
                TNG eWallet
                Top Up Successful
                Top Up Amount: RM 100.00
                Date & Time: 2026-07-10 14:32
                Transaction ID: TNG-7812
                Description: Public Bank Reload
                """, 0.91);

        assertThat(parsed.provider()).isEqualTo("TNG");
        assertThat(parsed.direction()).isEqualTo(Direction.CREDIT);
        assertThat(parsed.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsUnsupportedProvider() {
        assertThatThrownBy(() -> parser.parse("Amount: RM 10.00", 0.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not recognized");
    }
}

