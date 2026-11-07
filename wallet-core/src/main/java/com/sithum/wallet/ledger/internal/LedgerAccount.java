package com.sithum.wallet.ledger.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.sithum.wallet.ledger.Money;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ledger_account")
public class LedgerAccount {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerAccountType type;

    /** Set for system accounts only, e.g. EXTERNAL_FUNDING or FEES. */
    private String code;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    /** Minor units; a cached projection of the entries (ADR-0002). */
    @Column(nullable = false)
    private long balance;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerAccount() {
    }

    public static LedgerAccount wallet(String currency) {
        LedgerAccount account = new LedgerAccount();
        account.id = UUID.randomUUID();
        account.type = LedgerAccountType.WALLET;
        account.currency = currency;
        account.createdAt = account.updatedAt = Instant.now();
        return account;
    }

    public UUID getId() {
        return id;
    }

    public LedgerAccountType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public Money getBalance() {
        return Money.of(balance, currency);
    }

    public long getVersion() {
        return version;
    }
}
