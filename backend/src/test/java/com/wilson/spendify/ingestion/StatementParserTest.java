package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.DatePrecision;
import com.wilson.spendify.finance.Direction;
import com.wilson.spendify.finance.StatementValidationStatus;
import com.wilson.spendify.finance.TransactionKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementParserTest {
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @Test
    void parsesPublicBankCycleRowsAndExcludesBalances() {
        String page = """
                PUBLIC ISLAMIC BANK
                STATEMENT DATE 20 Jan 2026
                Total Debits 60.00
                No. of Debits 2
                Total Credits 100.00
                No. of Credits 1
                DATE  TRANSACTION  DEBIT  CREDIT  BALANCE
                20/12  Balance From Last Statement                 50.00
                21/12  PB DEBIT CARD DR                  25.00     25.00
                       TNG-EWALLET ECOM
                02/01  SALARY CREDIT                              100.00 125.00
                       PB DEBIT CARD DR                  35.00     90.00
                       Balance C/F                                90.00
                """;

        StatementParseResult result = new PublicBankStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.provider()).isEqualTo("PUBLIC_BANK");
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().getFirst().occurredOn()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(result.rows().getFirst().direction()).isEqualTo(Direction.CREDIT);
        assertThat(result.rows().getFirst().amount()).isEqualByComparingTo("100.00");
        assertThat(result.rows().get(1).occurredOn()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(result.rows().get(1).direction()).isEqualTo(Direction.DEBIT);
        assertThat(result.rows().get(1).amount()).isEqualByComparingTo("35.00");
        assertThat(result.rows()).allMatch(row -> row.datePrecision() == DatePrecision.DATE_ONLY);
        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
    }

    @Test
    void infersPublicBankDirectionFromBalanceWhenPdfSpacingIsAmbiguous() {
        String page = """
                PUBLIC BANK
                STATEMENT DATE 20 Feb 2026
                Total Debits 25.00
                No. of Debits 1
                Total Credits 100.00
                No. of Credits 1
                DATE TRANSACTION DEBIT CREDIT BALANCE
                20/01 Balance From Last Statement 50.00
                21/01 DESCRIPTION WITH VARIABLE SPACING 25.00 25.00
                22/01 ANOTHER DESCRIPTION 100.00 125.00
                """;

        StatementParseResult result = new PublicBankStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.rows()).extracting(StatementRow::direction)
                .containsExactly(Direction.DEBIT, Direction.CREDIT);
        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
    }

    @Test
    void ignoresCardDetailLinesThatContainTwoDecimalValues() {
        String page = """
                PUBLIC BANK
                STATEMENT DATE 20 Jul 2026
                Total Debits 18.90
                No. of Debits 1
                Total Credits 0.00
                No. of Credits 0
                DATE TRANSACTION DEBIT CREDIT BALANCE
                19/07 Balance From Last Statement 100.00
                20/07 PB DEBIT CARD DR 18.90 81.10
                       180726 FOREIGN CURRENCY 0.10 0000018.90
                """;

        StatementParseResult result = new PublicBankStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
    }

    @Test
    void excludesPublicBankFooterTextFromTheMerchantDescription() {
        String page = """
                PUBLIC BANK
                STATEMENT DATE 20 Jul 2026
                Total Debits 18.90
                No. of Debits 1
                Total Credits 0.00
                No. of Credits 0
                DATE TRANSACTION DEBIT CREDIT BALANCE
                19/07 Balance From Last Statement 100.00
                20/07 PB DEBIT CARD DR VISA123456789 SHOPEEPAY MSIA MY 18.90 81.10
                      Baki Harian Dan Penutup Meliputi Semua Cek Yang Telah Didepositkan
                """;

        StatementParseResult result = new PublicBankStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.rows().getFirst().merchant()).isEqualTo("SHOPEEPAY MSIA MY");
    }

    @Test
    void parsesTngRowsFiltersYearAndSkipsFailedTransactions() {
        String page = """
                TNG WALLET TRANSACTION HISTORY
                Date  Status  Transaction Type  Reference  Description  Details  Amount (RM)  Wallet Balance
                31/12/2025  Success  DuitNow QR  OLD-1  Old Merchant  detail  RM5.00  RM20.00
                02/01/2026  Success  Balance Top Up  TOP-1  Quick Payment Card  detail  RM100.00  RM120.00
                03/01/2026  Failed  DuitNow QR  FAIL-1  Failed Merchant  detail  RM20.00  RM120.00
                04/01/2026  Success  DuitNow QR  PAY-1  Lunch Merchant  detail  RM12.50  RM107.50
                05/01/2026  Success  Payment Refund  REF-1  Lunch Merchant  detail  RM2.50  RM110.00
                """;

        StatementParseResult result = new TngStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.provider()).isEqualTo("TNG");
        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows()).extracting(StatementRow::direction)
                .containsExactly(Direction.CREDIT, Direction.DEBIT, Direction.CREDIT);
        assertThat(result.rows()).extracting(StatementRow::kind)
                .containsExactly(TransactionKind.TRANSFER_PENDING, TransactionKind.EXPENSE, TransactionKind.REFUND);
        assertThat(result.rows()).extracting(StatementRow::providerTransactionType)
                .containsExactly("Balance Top Up", "DuitNow QR", "Payment Refund");
        assertThat(result.rows()).extracting(StatementRow::amount)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("12.50"), new BigDecimal("2.50"));
        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
    }

    @Test
    void warnsWhenTngBalanceContinuityDoesNotMatch() {
        String page = """
                TNG WALLET TRANSACTION HISTORY
                Date  Status  Transaction Type  Reference  Description  Details  Amount (RM)  Wallet Balance
                02/01/2026  Success  Balance Top Up  TOP-1  Card  detail  RM100.00  RM100.00
                03/01/2026  Success  DuitNow QR  PAY-1  Merchant  detail  RM10.00  RM95.00
                """;

        StatementParseResult result = new TngStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.WARNING);
        assertThat(result.validationMessage()).contains("balance");
    }

    @Test
    void validatesNewestFirstTngBalanceContinuity() {
        String page = """
                TNG WALLET TRANSACTION HISTORY
                04/01/2026  Success  DuitNow QR  PAY-1  Lunch Merchant  detail  RM12.50  RM107.50
                02/01/2026  Success  Balance Top Up  TOP-1  Quick Payment Card  detail  RM100.00  RM120.00
                """;

        StatementParseResult result = new TngStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
    }

    @Test
    void extractsTngMerchantWhenPdfColumnsCollapseToSingleSpaces() {
        String page = """
                TNG WALLET TRANSACTION HISTORY
                22/6/2026 Success Payment 20260622101 EASTERN GOODS TRADING SDN. 202606222112128001101715241360 RM150.00 RM190.89
                23/6/2026 Success Payment 20260623101 ZIGNATURE SDN. BHD. 202606232112128001101710105361 RM36.00 RM154.89
                """;

        StatementParseResult result = new TngStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.rows()).extracting(StatementRow::merchant)
                .containsExactly("EASTERN GOODS TRADING SDN.", "ZIGNATURE SDN. BHD.");
        assertThat(result.rows()).extracting(StatementRow::reference)
                .containsExactly("20260622101", "20260623101");
    }

    @Test
    void usesWalletBalanceToInferUnknownProviderReceiveCode() {
        String page = """
                TNG WALLET TRANSACTION HISTORY
                04/01/2026  Success  DUITNOW_RECEI  SYN-2  Wallet transfer  detail  RM10.00  RM110.00
                03/01/2026  Success  Payment  SYN-1  Merchant  detail  RM5.00  RM100.00
                """;

        StatementParseResult result = new TngStatementParser().parse(List.of(page), FROM, TO);

        assertThat(result.rows().getFirst().direction()).isEqualTo(Direction.CREDIT);
        assertThat(result.validationStatus()).isEqualTo(StatementValidationStatus.VALID);
    }

    @Test
    void flagsARefundWhoseProviderTypeConflictsWithWalletMovement() {
        String page = """
                TNG WALLET TRANSACTION HISTORY
                02/01/2026  Success  Balance Top Up  TOP-1  Card  detail  RM100.00  RM100.00
                03/01/2026  Success  Payment Refund  REF-1  Merchant  detail  RM10.00  RM90.00
                """;

        StatementParseResult result = new TngStatementParser().parse(List.of(page), FROM, TO);

        StatementRow refund = result.rows().get(1);
        assertThat(refund.direction()).isEqualTo(Direction.DEBIT);
        assertThat(refund.kind()).isEqualTo(TransactionKind.REFUND);
        assertThat(refund.reviewNote()).contains("conflicts");
    }
}
