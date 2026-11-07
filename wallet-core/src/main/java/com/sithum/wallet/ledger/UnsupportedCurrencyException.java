package com.sithum.wallet.ledger;

/** The currency is malformed or not on the configured list of supported currencies (ADR-0003). */
public class UnsupportedCurrencyException extends RuntimeException {

    private final String currency;

    public UnsupportedCurrencyException(String currency) {
        super("Unsupported currency: " + currency);
        this.currency = currency;
    }

    public String getCurrency() {
        return currency;
    }
}
