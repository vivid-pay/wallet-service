package com.sithum.wallet.accounts;

import java.util.UUID;

/**
 * What other modules may ask the accounts module about a wallet. Transfers use this to refuse moving money
 * out of or into a frozen wallet.
 */
public interface Wallets {

    /**
     * @return {@code true} if the wallet exists and is not frozen
     * @throws WalletNotFoundException if there is no wallet with this id
     */
    boolean isActive(UUID walletId);
}
