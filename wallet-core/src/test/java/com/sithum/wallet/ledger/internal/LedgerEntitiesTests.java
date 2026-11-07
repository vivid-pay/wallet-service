package com.sithum.wallet.ledger.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sithum.wallet.ledger.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerEntitiesTests {

    @Test
    void newWalletAccountIsEmptyAndTyped() {
        LedgerAccountEntity account = LedgerAccountEntity.wallet("USD");

        assertThat(account.getId()).isNotNull();
        assertThat(account.getType()).isEqualTo(LedgerAccountType.WALLET);
        assertThat(account.getCode()).isNull();
        assertThat(account.getBalance()).isEqualTo(Money.of(0, "USD"));
        assertThat(account.getVersion()).isZero();
    }

    @Test
    void entryExposesWhatItWasCreatedWith() {
        UUID tx = UUID.randomUUID();
        UUID account = UUID.randomUUID();

        LedgerEntryEntity entry = new LedgerEntryEntity(tx, account, Money.of(-250, "EUR"));

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getTransactionId()).isEqualTo(tx);
        assertThat(entry.getAccountId()).isEqualTo(account);
        assertThat(entry.getAmount()).isEqualTo(Money.of(-250, "EUR"));
    }

    @Test
    void entryRejectsAZeroAmount() {
        UUID id = UUID.randomUUID();
        Money zero = Money.of(0, "USD");

        assertThatThrownBy(() -> new LedgerEntryEntity(id, id, zero))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
