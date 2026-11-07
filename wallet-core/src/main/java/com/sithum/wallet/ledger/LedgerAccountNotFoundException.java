package com.sithum.wallet.ledger;

import java.util.UUID;

/** No ledger account exists with the given id. */
public class LedgerAccountNotFoundException extends RuntimeException {

    private final UUID accountId;

    public LedgerAccountNotFoundException(UUID accountId) {
        super("Ledger account not found: " + accountId);
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
