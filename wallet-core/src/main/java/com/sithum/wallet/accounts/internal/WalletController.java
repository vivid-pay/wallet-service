package com.sithum.wallet.accounts.internal;

import com.sithum.wallet.accounts.CurrentOwner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/wallets")
class WalletController {

    /** Largest page a caller may ask for; a larger {@code size} is rejected with 400, not silently capped. */
    static final int MAX_PAGE_SIZE = 100;

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

    @GetMapping
    PageDto<WalletDto> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return wallets.list(currentOwner.ownerId(), page, size);
    }

    @GetMapping("/{id}")
    WalletDto get(@PathVariable UUID id) {
        return wallets.get(currentOwner.ownerId(), id);
    }
}
