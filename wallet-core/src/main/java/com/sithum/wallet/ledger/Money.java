package com.sithum.wallet.ledger;

import java.util.regex.Pattern;

/** An amount in minor units of one currency (ADR-0003). Arithmetic is exact and single-currency. */
public record Money(long amount, String currency) {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    public Money {
        if (currency == null || !CURRENCY.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be a 3-letter uppercase code: " + currency);
        }
    }

    public static Money of(long amount, String currency) {
        return new Money(amount, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amount), currency);
    }

    public boolean isNegative() {
        return amount < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
