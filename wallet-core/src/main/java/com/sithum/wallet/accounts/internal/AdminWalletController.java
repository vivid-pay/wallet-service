package com.sithum.wallet.accounts.internal;

import com.sithum.wallet.accounts.CurrentOwner;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator endpoints. <strong>UNPROTECTED:</strong> there is no admin-only authorisation yet, so anyone who sends
 * a well-formed {@code X-User-Id} can freeze any wallet, and the actor recorded in the history is whatever id
 * the caller claims (ADR-0006). VPAY-7 (Security epic) must protect {@code /api/v1/admin/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/wallets")
class AdminWalletController {

    private final WalletService wallets;
    private final CurrentOwner currentOwner;

    AdminWalletController(WalletService wallets, CurrentOwner currentOwner) {
        this.wallets = wallets;
        this.currentOwner = currentOwner;
    }

    @PutMapping("/{id}/status")
    WalletDto changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeWalletStatusRequestDto request) {
        return wallets.changeStatus(currentOwner.ownerId(), id, request.status(), request.reason());
    }
}
