package com.sithum.wallet.accounts.internal;

import com.sithum.wallet.accounts.CurrentOwner;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/wallets")
class WalletController {

    private final WalletService wallets;
    private final CurrentOwner currentOwner;

    WalletController(WalletService wallets, CurrentOwner currentOwner) {
        this.wallets = wallets;
        this.currentOwner = currentOwner;
    }

    @PostMapping
    ResponseEntity<WalletDto> create(@Valid @RequestBody CreateWalletRequestDto request) {
        WalletDto wallet = wallets.create(currentOwner.ownerId(), request.currency());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(wallet.id()).toUri();
        return ResponseEntity.created(location).body(wallet);
    }
}
