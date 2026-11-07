package com.sithum.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
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
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Import(LedgerAccountsIntegrationTests.PostgresConfig.class)
class LedgerAccountsIntegrationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired
    LedgerAccounts ledger;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void opensAWalletAccountWithZeroBalance() {
        UUID id = ledger.openWalletAccount("USD");

        Map<String, Object> row = jdbc.queryForMap(
                "select type, currency, balance, code from ledger_account where id = ?", id);
        assertThat(row).containsEntry("type", "WALLET").containsEntry("currency", "USD")
                .containsEntry("balance", 0L).containsEntry("code", null);
    }

    @Test
    void readsBalanceAndCurrencyAsMoney() {
        UUID id = ledger.openWalletAccount("LKR");

        assertThat(ledger.balanceOf(id)).isEqualTo(Money.of(0, "LKR"));
    }

    @Test
    void readsABalanceThatChanged() {
        UUID id = ledger.openWalletAccount("EUR");
        jdbc.update("update ledger_account set balance = 1234 where id = ?", id);

        assertThat(ledger.balanceOf(id)).isEqualTo(Money.of(1234, "EUR"));
    }

    @Test
    void eachCallOpensANewAccount() {
        assertThat(ledger.openWalletAccount("USD")).isNotEqualTo(ledger.openWalletAccount("USD"));
    }

    @Test
    void rejectsUnsupportedOrMalformedCurrency() {
        for (String bad : new String[] {"JPY", "usd", "US", "USDX", "U1D", "", " ", null}) {
            assertThatThrownBy(() -> ledger.openWalletAccount(bad))
                    .as("currency %s", bad)
                    .isInstanceOf(UnsupportedCurrencyException.class);
        }
    }

    @Test
    void rejectedCurrencyCreatesNoRow() {
        Long before = jdbc.queryForObject("select count(*) from ledger_account", Long.class);
        assertThatThrownBy(() -> ledger.openWalletAccount("XXX")).isInstanceOf(UnsupportedCurrencyException.class);
        assertThat(jdbc.queryForObject("select count(*) from ledger_account", Long.class)).isEqualTo(before);
    }

    @Test
    void readingAMissingAccountThrows() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> ledger.balanceOf(missing))
                .isInstanceOf(LedgerAccountNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void checkConstraintStillHoldsForOpenedAccounts() {
        UUID id = ledger.openWalletAccount("USD");

        assertThatThrownBy(() -> jdbc.update("update ledger_account set balance = -1 where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ledger.balanceOf(id).amount()).isZero();
    }
}
