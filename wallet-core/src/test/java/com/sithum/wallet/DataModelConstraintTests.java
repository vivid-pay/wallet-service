package com.sithum.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sithum.wallet.accounts.internal.WalletProfileEntity;
import com.sithum.wallet.accounts.internal.WalletStatus;
import com.sithum.wallet.ledger.Money;
import com.sithum.wallet.ledger.internal.LedgerAccountEntity;
import com.sithum.wallet.ledger.internal.LedgerEntryEntity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Proves the database itself rejects bad data (ADR-0002), independent of the Java code. */
@SpringBootTest
@Import(DataModelConstraintTests.PostgresConfig.class)
class DataModelConstraintTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    private UUID insertAccount(String type, String code, String currency, long balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into ledger_account (id, type, code, currency, balance) values (?, ?, ?, ?, ?)",
                id, type, code, currency, balance);
        return id;
    }

    private UUID insertWallet(String currency) {
        return insertAccount("WALLET", null, currency, 0);
    }

    private UUID insertEntry(UUID accountId, long amount, String currency) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into ledger_entry (id, transaction_id, account_id, amount, currency) values (?, ?, ?, ?, ?)",
                id, UUID.randomUUID(), accountId, amount, currency);
        return id;
    }

    private void insertProfile(UUID accountId, UUID owner, String currency, String status) {
        jdbc.update("insert into wallet_profile (ledger_account_id, owner_id, currency, status) values (?, ?, ?, ?)",
                accountId, owner, currency, status);
    }

    @Test
    void walletBalanceCannotBeNegative() {
        assertThatThrownBy(() -> insertAccount("WALLET", null, "USD", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID wallet = insertWallet("USD");
        assertThatThrownBy(() -> jdbc.update("update ledger_account set balance = -1 where id = ?", wallet))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void systemAccountBalanceCanBeNegative() {
        UUID id = insertAccount("SYSTEM", "TEST_ACCOUNT", "USD", -500);
        assertThat(jdbc.queryForObject("select balance from ledger_account where id = ?", Long.class, id))
                .isEqualTo(-500);
    }

    @Test
    void accountTypeIsRestricted() {
        assertThatThrownBy(() -> insertAccount("OTHER", null, "USD", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void currencyMustBeThreeUppercaseLetters() {
        assertThatThrownBy(() -> insertAccount("WALLET", null, "usd", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAccount("WALLET", null, "U1D", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAccount("WALLET", null, "US", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID wallet = insertWallet("USD");
        assertThatThrownBy(() -> insertProfile(wallet, UUID.randomUUID(), "usd", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlySystemAccountsHaveACode() {
        assertThatThrownBy(() -> insertAccount("WALLET", "FEES", "USD", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAccount("SYSTEM", null, "USD", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void systemAccountCodeIsUniquePerCurrency() {
        assertThatThrownBy(() -> insertAccount("SYSTEM", "FEES", "USD", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void systemAccountsAreSeededForEachSupportedCurrency() {
        List<String> rows = jdbc.queryForList(
                "select code || ':' || currency from ledger_account where type = 'SYSTEM' "
                        + "and code in ('EXTERNAL_FUNDING', 'FEES') order by 1", String.class);
        assertThat(rows).containsExactly(
                "EXTERNAL_FUNDING:EUR", "EXTERNAL_FUNDING:LKR", "EXTERNAL_FUNDING:USD",
                "FEES:EUR", "FEES:LKR", "FEES:USD");
    }

    @Test
    void walletProfileStatusIsRestricted() {
        UUID wallet = insertWallet("USD");
        assertThatThrownBy(() -> insertProfile(wallet, UUID.randomUUID(), "USD", "CLOSED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertProfile(wallet, UUID.randomUUID(), "USD", "FROZEN");
    }

    @Test
    void walletProfileIsUniquePerOwnerAndCurrency() {
        UUID owner = UUID.randomUUID();
        insertProfile(insertWallet("USD"), owner, "USD", "ACTIVE");
        assertThatThrownBy(() -> insertProfile(insertWallet("USD"), owner, "USD", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertProfile(insertWallet("EUR"), owner, "EUR", "ACTIVE");
    }

    @Test
    void entryAmountMustBeNonZero() {
        UUID wallet = insertWallet("USD");
        assertThatThrownBy(() -> insertEntry(wallet, 0, "USD"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertEntry(wallet, 100, "USD");
        insertEntry(wallet, -100, "USD");
    }

    @Test
    void entryMustReferenceAnExistingAccountInTheSameCurrency() {
        UUID wallet = insertWallet("USD");
        assertThatThrownBy(() -> insertEntry(wallet, 100, "EUR"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), 100, "USD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void entryCurrencyMustBeThreeUppercaseLetters() {
        UUID wallet = insertWallet("USD");
        assertThatThrownBy(() -> insertEntry(wallet, 100, "usd"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void entriesCannotBeUpdatedDeletedOrTruncated() {
        UUID entry = insertEntry(insertWallet("USD"), 100, "USD");
        assertThatThrownBy(() -> jdbc.update("update ledger_entry set amount = 5 where id = ?", entry))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from ledger_entry where id = ?", entry))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.execute("truncate ledger_entry"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select amount from ledger_entry where id = ?", Long.class, entry))
                .isEqualTo(100);
    }

    @Test
    void jpaEntitiesMapTheTables() {
        UUID owner = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID accountId = tx.execute(status -> {
            LedgerAccountEntity account = LedgerAccountEntity.wallet("USD");
            em.persist(account);
            em.persist(new WalletProfileEntity(account.getId(), owner, "USD"));
            em.persist(new LedgerEntryEntity(txId, account.getId(), Money.of(250, "USD")));
            return account.getId();
        });

        tx.executeWithoutResult(status -> {
            LedgerAccountEntity account = em.find(LedgerAccountEntity.class, accountId);
            assertThat(account.getBalance()).isEqualTo(Money.of(0, "USD"));
            WalletProfileEntity profile = em.find(WalletProfileEntity.class, accountId);
            assertThat(profile.getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(profile.getOwnerId()).isEqualTo(owner);
            Long total = em.createQuery("select sum(e.amount) from LedgerEntryEntity e where e.transactionId = :t", Long.class)
                    .setParameter("t", txId).getSingleResult();
            assertThat(total).isEqualTo(250);
        });
    }
}
