package com.sithum.wallet.accounts;

import java.util.UUID;

/** The owner already has a wallet in this currency (mapped to 409). */
public class DuplicateWalletException extends RuntimeException {

    private final UUID ownerId;
    private final String currency;

    public DuplicateWalletException(UUID ownerId, String currency) {
        this(ownerId, currency, null);
    }

    public DuplicateWalletException(UUID ownerId, String currency, Throwable cause) {
        super("Owner " + ownerId + " already has a " + currency + " wallet", cause);
        this.ownerId = ownerId;
        this.currency = currency;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }
}
