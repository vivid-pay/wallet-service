package com.sithum.wallet.ledger;

import java.util.UUID;

/**
 * Public API of the ledger module for managing ledger accounts. Other modules (e.g. accounts)
 * use this interface and never touch the ledger tables (ADR-0002).
 */
public interface LedgerAccounts {

    /**
     * Opens a WALLET ledger account in the given currency with a zero balance.
     *
     * @return the id of the new account
     * @throws UnsupportedCurrencyException if the currency is null, malformed or not supported
     */
    UUID openWalletAccount(String currency);

    /**
     * Reads the current balance of an account; the returned {@link Money} carries the account's currency.
     *
     * @throws LedgerAccountNotFoundException if no such account exists
     */
    Money balanceOf(UUID accountId);
}
