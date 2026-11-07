package com.sithum.wallet.accounts.internal;

import java.util.UUID;

/** A wallet as the API returns it. {@code balance} is in minor units of {@code currency} (ADR-0003). */
record WalletDto(UUID id, String currency, long balance, WalletStatus status) {
}
