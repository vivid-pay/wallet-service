package com.sithum.wallet.accounts;

import java.util.UUID;

/**
 * No wallet with this id is visible to the caller (mapped to 404). Deliberately the same for a wallet that does
 * not exist and one that belongs to someone else, so the API leaks nothing about other users' wallets.
 */
public class WalletNotFoundException extends RuntimeException {

    private final UUID walletId;

    public WalletNotFoundException(UUID walletId) {
        super("Wallet not found: " + walletId);
        this.walletId = walletId;
    }

    public UUID getWalletId() {
        return walletId;
    }
}
