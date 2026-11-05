package com.sithum.wallet.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Owner and status of a wallet. Refers to its ledger account by id only; the
 * ledger module owns the account and balance, so there is no cross-module foreign key.
 */
@Entity
@Table(name = "wallet_profile")
public class WalletProfile {

    @Id
    @Column(name = "ledger_account_id")
    private UUID ledgerAccountId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletProfile() {
    }

    public WalletProfile(UUID ledgerAccountId, UUID ownerId, String currency) {
        this.ledgerAccountId = ledgerAccountId;
        this.ownerId = ownerId;
        this.currency = currency;
        this.status = WalletStatus.ACTIVE;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }

    public WalletStatus getStatus() {
        return status;
    }
}
