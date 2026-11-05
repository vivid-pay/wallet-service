package com.sithum.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTests {

    @Test
    void addsAndSubtractsWithinOneCurrency() {
        assertThat(Money.of(1000, "USD").plus(Money.of(234, "USD"))).isEqualTo(Money.of(1234, "USD"));
        assertThat(Money.of(1000, "USD").minus(Money.of(1500, "USD"))).isEqualTo(Money.of(-500, "USD"));
        assertThat(Money.of(5, "USD").negate().isNegative()).isTrue();
    }

    @Test
    void rejectsMixedCurrencies() {
        assertThatThrownBy(() -> Money.of(1, "USD").plus(Money.of(1, "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsLoudlyOnOverflow() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "USD").plus(Money.of(1, "USD")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsInvalidCurrencyCodes() {
        assertThatThrownBy(() -> Money.of(1, "usd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(1, "US")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(1, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
