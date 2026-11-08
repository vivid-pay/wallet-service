package com.sithum.wallet.accounts.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/v1/admin/wallets/{id}/status}. An unknown status value is rejected as a 400. */
record ChangeWalletStatusRequestDto(@NotNull WalletStatus status, @NotBlank String reason) {
}
