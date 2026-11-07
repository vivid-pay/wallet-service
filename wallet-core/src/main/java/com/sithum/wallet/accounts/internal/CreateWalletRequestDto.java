package com.sithum.wallet.accounts.internal;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/wallets}. Whether the currency is supported is the ledger's call. */
record CreateWalletRequestDto(@NotNull String currency) {
}
