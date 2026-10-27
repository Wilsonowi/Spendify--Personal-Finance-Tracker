package com.wilson.spendify.finance;

import com.wilson.spendify.identity.AppUser;
import com.wilson.spendify.identity.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresContractTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private FinancialAccountRepository accounts;
    @Autowired private SourceDocumentRepository documents;
    @Autowired private FinanceTransactionRepository transactions;
    @Autowired private DataSource dataSource;

    @Test
    void migrationPreservesMoneyPrecisionAndMatchingIndexes() {
        var moneyColumn = jdbc.queryForMap("""
                SELECT numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'finance_transaction'
                  AND column_name = 'amount'
                """);
        assertThat(moneyColumn.get("numeric_precision")).isEqualTo(14);
        assertThat(moneyColumn.get("numeric_scale")).isEqualTo(2);

        String matchingIndex = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'idx_transaction_match'
                """, String.class);
        assertThat(matchingIndex).contains("user_id", "amount", "direction", "occurred_at");

        String confirmedIndex = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'uq_confirmed_transfer_debit'
                """, String.class);
        assertThat(confirmedIndex).contains("UNIQUE", "WHERE", "status", "CONFIRMED");
    }

    @Test
    void statementMigrationSupportsManyOrderedRowsPerEncryptedDocument() {
        var sourceColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'source_document'
                  AND column_name IN ('document_kind','period_start','period_end','row_count',
                                      'resolved_count','validation_status','validation_message')
                """, String.class);
        assertThat(sourceColumns).hasSize(7);

        var transactionColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'finance_transaction'
                  AND column_name IN ('source_page','source_sequence','date_precision','statement_balance',
                                      'review_note','exclusion_reason')
                """, String.class);
        assertThat(transactionColumns).hasSize(6);

        String sourceSequenceIndex = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'uq_transaction_document_sequence'
                """, String.class);
        assertThat(sourceSequenceIndex).contains("UNIQUE", "document_id", "source_sequence");
    }

    @Test
    void statementFirstMigrationStoresPostingAndReviewAuditMetadata() {
        var sourceColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'source_document'
                  AND column_name IN ('auto_posted_count','exception_count','duplicate_count','transfer_count')
                """, String.class);
        assertThat(sourceColumns).hasSize(4);

        var transactionColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'finance_transaction'
                  AND column_name IN ('categorization_confidence','categorization_source','review_reason',
                                      'resolution_method','provider_transaction_type','reference_fingerprint',
                                      'refund_of')
                """, String.class);
        assertThat(transactionColumns).hasSize(7);

        String referenceIndex = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'idx_transaction_reference_fingerprint'
                """, String.class);
        assertThat(referenceIndex).contains("user_id", "account_id", "reference_fingerprint");
    }

    @Test
    void statementSourceSequenceIsUniqueWithinOneDocument() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash) VALUES (?,?,?,?)",
                userId, "sequence@spendify.local", "Sequence", "hash");
        jdbc.update("INSERT INTO financial_account(id,user_id,provider,display_name,account_type) VALUES (?,?,?,?,?)",
                accountId, userId, "TNG", "Sequence Wallet", "EWALLET");
        jdbc.update("""
                INSERT INTO source_document
                    (id,user_id,original_name,content_type,byte_size,sha256,storage_key,
                     encryption_iv,wrapped_key,document_kind,row_count,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, documentId, userId, "synthetic.pdf", "application/pdf", 100, "c".repeat(64),
                "objects/sequence", "iv", "wrapped", "STATEMENT", 2, "UPLOADED");
        insertStatementTransaction(UUID.randomUUID(), userId, accountId, documentId, 1);

        assertThatThrownBy(() -> insertStatementTransaction(
                UUID.randomUUID(), userId, accountId, documentId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoriesKeepDocumentsAndTransactionsScopedToTheirOwner() {
        AppUser first = users.save(new AppUser("first@spendify.local", "First", "hash"));
        AppUser second = users.save(new AppUser("second@spendify.local", "Second", "hash"));
        FinancialAccount firstAccount = accounts.save(
                new FinancialAccount(first.getId(), "TNG", "First Wallet", AccountType.EWALLET));

        SourceDocument document = documents.save(new SourceDocument(
                first.getId(), "synthetic.png", "image/png", 128,
                "a".repeat(64), "documents/first", "iv", "wrapped"));
        FinanceTransaction transaction = transactions.save(new FinanceTransaction(
                first.getId(), firstAccount.getId(), document.getId(), Direction.DEBIT,
                TransactionKind.EXPENSE, new BigDecimal("12.50"), Instant.parse("2026-07-15T02:45:00Z"),
                "Synthetic Merchant", "SYN-001", new BigDecimal("0.950"), "synthetic evidence"));

        assertThat(documents.findByIdAndUserId(document.getId(), first.getId())).isPresent();
        assertThat(documents.findByIdAndUserId(document.getId(), second.getId())).isEmpty();
        assertThat(transactions.findByIdAndUserId(transaction.getId(), first.getId())).isPresent();
        assertThat(transactions.findByIdAndUserId(transaction.getId(), second.getId())).isEmpty();
    }

    @Test
    void deletingStatementCascadesItsTransactionsAndTransferCandidates() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID debitId = UUID.randomUUID();
        UUID creditId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash) VALUES (?,?,?,?)",
                userId, "cascade@spendify.local", "Cascade", "hash");
        jdbc.update("INSERT INTO financial_account(id,user_id,provider,display_name,account_type) VALUES (?,?,?,?,?)",
                accountId, userId, "TNG", "Cascade Wallet", "EWALLET");
        jdbc.update("""
                INSERT INTO source_document
                    (id,user_id,original_name,content_type,byte_size,sha256,storage_key,
                     encryption_iv,wrapped_key,document_kind,row_count,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, documentId, userId, "cascade.pdf", "application/pdf", 100, "d".repeat(64),
                "objects/cascade", "iv", "wrapped", "STATEMENT", 2, "CONFIRMED");
        insertStatementTransaction(debitId, userId, accountId, documentId, 1);
        insertStatementTransaction(creditId, userId, accountId, documentId, 2);
        jdbc.update("""
                INSERT INTO transfer_candidate
                    (id,user_id,debit_transaction_id,credit_transaction_id,score,evidence,status)
                VALUES (?,?,?,?,?,?,?)
                """, transferId, userId, debitId, creditId, 100, "synthetic", "PROPOSED");

        jdbc.update("DELETE FROM source_document WHERE id = ?", documentId);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_transaction WHERE document_id = ?", Integer.class, documentId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transfer_candidate WHERE id = ?", Integer.class, transferId))
                .isZero();
    }

    @Test
    void confirmedTransferCannotReuseTheSameDebitTransaction() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID debitId = UUID.randomUUID();
        UUID firstCreditId = UUID.randomUUID();
        UUID secondCreditId = UUID.randomUUID();

        jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash) VALUES (?,?,?,?)",
                userId, "constraint@spendify.local", "Constraint", "hash");
        jdbc.update("INSERT INTO financial_account(id,user_id,provider,display_name,account_type) VALUES (?,?,?,?,?)",
                accountId, userId, "TNG", "Constraint Wallet", "EWALLET");
        insertTransaction(debitId, userId, accountId, "DEBIT", "2026-07-15T02:00:00Z");
        insertTransaction(firstCreditId, userId, accountId, "CREDIT", "2026-07-15T02:05:00Z");
        insertTransaction(secondCreditId, userId, accountId, "CREDIT", "2026-07-15T02:10:00Z");

        jdbc.update("""
                INSERT INTO transfer_candidate
                    (id,user_id,debit_transaction_id,credit_transaction_id,score,evidence,status)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, debitId, firstCreditId, 100, "synthetic", "CONFIRMED");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transfer_candidate
                    (id,user_id,debit_transaction_id,credit_transaction_id,score,evidence,status)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, debitId, secondCreditId, 100, "synthetic", "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void automaticAnalysisMigrationRepairsValidDraftsAndStaleTransferProposals() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID debitId = UUID.randomUUID();
        UUID confirmedCreditId = UUID.randomUUID();
        UUID staleCreditId = UUID.randomUUID();
        UUID staleCandidateId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash) VALUES (?,?,?,?)",
                userId, "repair@spendify.local", "Repair", "hash");
        jdbc.update("INSERT INTO financial_account(id,user_id,provider,display_name,account_type) VALUES (?,?,?,?,?)",
                accountId, userId, "TNG", "Repair Wallet", "EWALLET");
        jdbc.update("""
                INSERT INTO source_document
                    (id,user_id,original_name,content_type,byte_size,sha256,storage_key,encryption_iv,
                     wrapped_key,document_kind,provider,row_count,resolved_count,exception_count,validation_status,status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, documentId, userId, "repair.pdf", "application/pdf", 100, "e".repeat(64),
                "objects/repair", "iv", "wrapped", "STATEMENT", "TNG", 2, 0, 2, "VALID", "NEEDS_REVIEW");
        jdbc.update("""
                INSERT INTO finance_transaction
                    (id,user_id,account_id,document_id,category_id,direction,kind,amount,currency,
                     occurred_at,merchant,reference,provider_transaction_type,status,review_reason,resolution_method,
                     source_page,source_sequence,date_precision)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, draftId, userId, accountId, documentId,
                UUID.fromString("00000000-0000-0000-0000-000000000011"), "DEBIT", "EXPENSE",
                new BigDecimal("12.50"), "MYR", Timestamp.from(Instant.parse("2026-07-02T04:00:00Z")),
                "Payment", "20260702101 Shopee Malaysia 202607021234567890", "Payment",
                "DRAFT", "UNCATEGORIZED", null, 1, 1, "DATE_ONLY");
        jdbc.update("""
                INSERT INTO finance_transaction
                    (id,user_id,account_id,document_id,category_id,direction,kind,amount,currency,
                     occurred_at,status,review_reason,resolution_method,source_page,source_sequence,date_precision)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, pendingId, userId, accountId, documentId,
                UUID.fromString("00000000-0000-0000-0000-000000000011"), "CREDIT", "TRANSFER_PENDING",
                new BigDecimal("100.00"), "MYR", Timestamp.from(Instant.parse("2026-07-03T04:00:00Z")),
                "CONFIRMED", "AMBIGUOUS_TRANSFER", null, 1, 2, "DATE_ONLY");
        insertTransaction(debitId, userId, accountId, "DEBIT", "2026-07-04T04:00:00Z");
        insertTransaction(confirmedCreditId, userId, accountId, "CREDIT", "2026-07-04T04:00:00Z");
        insertTransaction(staleCreditId, userId, accountId, "CREDIT", "2026-07-04T04:00:00Z");
        jdbc.update("""
                INSERT INTO transfer_candidate
                    (id,user_id,debit_transaction_id,credit_transaction_id,score,evidence,status)
                VALUES (?,?,?,?,?,?,?),(?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, debitId, confirmedCreditId, 100, "confirmed", "CONFIRMED",
                staleCandidateId, userId, debitId, staleCreditId, 78, "stale", "PROPOSED");

        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V4__automatic_statement_analysis.sql"))
                .execute(dataSource);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V5__repair_statement_categories.sql"))
                .execute(dataSource);

        assertThat(jdbc.queryForObject("SELECT status FROM finance_transaction WHERE id=?", String.class, draftId))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT review_reason FROM finance_transaction WHERE id=?", String.class,
                draftId)).isNull();
        assertThat(jdbc.queryForObject("SELECT merchant FROM finance_transaction WHERE id=?", String.class,
                draftId)).isEqualTo("Shopee Malaysia");
        assertThat(jdbc.queryForObject("SELECT category_id FROM finance_transaction WHERE id=?", UUID.class,
                draftId)).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000005"));
        assertThat(jdbc.queryForObject("SELECT review_reason FROM finance_transaction WHERE id=?", String.class,
                pendingId)).isNull();
        assertThat(jdbc.queryForMap("""
                SELECT auto_posted_count, transfer_count, exception_count, resolved_count, status
                FROM source_document WHERE id=?
                """, documentId)).containsEntry("auto_posted_count", 1)
                .containsEntry("transfer_count", 1).containsEntry("exception_count", 0)
                .containsEntry("resolved_count", 2).containsEntry("status", "CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT status FROM transfer_candidate WHERE id=?", String.class,
                staleCandidateId)).isEqualTo("REJECTED");
    }

    private void insertTransaction(UUID id, UUID userId, UUID accountId, String direction, String occurredAt) {
        jdbc.update("""
                INSERT INTO finance_transaction
                    (id,user_id,account_id,direction,kind,amount,currency,occurred_at,status)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, userId, accountId, direction, "TRANSFER", new BigDecimal("100.00"), "MYR",
                Timestamp.from(Instant.parse(occurredAt)), "CONFIRMED");
    }

    private void insertStatementTransaction(UUID id, UUID userId, UUID accountId, UUID documentId, int sequence) {
        jdbc.update("""
                INSERT INTO finance_transaction
                    (id,user_id,account_id,document_id,direction,kind,amount,currency,occurred_at,status,
                     source_page,source_sequence,date_precision)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, userId, accountId, documentId, "DEBIT", "EXPENSE", new BigDecimal("1.00"), "MYR",
                Timestamp.from(Instant.parse("2026-01-02T04:00:00Z")), "DRAFT", 1, sequence, "DATE_ONLY");
    }
}
