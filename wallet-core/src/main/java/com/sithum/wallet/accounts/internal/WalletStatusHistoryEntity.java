package com.sithum.wallet.accounts.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One status change of a wallet: who, when and why. Append-only; the database rejects updates and deletes. */
@Entity
@Table(name = "wallet_status_history")
class WalletStatusHistoryEntity {

    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    private WalletStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private WalletStatus toStatus;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected WalletStatusHistoryEntity() {
    }

    WalletStatusHistoryEntity(UUID walletId, WalletStatus fromStatus, WalletStatus toStatus, UUID actorId,
            String reason) {
        this.id = UUID.randomUUID();
        this.walletId = walletId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reason = reason;
        this.changedAt = Instant.now();
    }
}
