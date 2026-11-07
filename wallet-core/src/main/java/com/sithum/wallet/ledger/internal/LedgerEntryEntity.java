package com.sithum.wallet.ledger.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.sithum.wallet.ledger.Money;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One signed line of a transaction. Immutable; corrections are reversal entries (ADR-0002). */
@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntryEntity {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Positive increases the account's balance, negative decreases it. Never zero. */
    @Column(nullable = false)
    private long amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {
    }

    public LedgerEntryEntity(UUID transactionId, UUID accountId, Money amount) {
        if (amount.amount() == 0) {
            throw new IllegalArgumentException("entry amount must be non-zero");
        }
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Money getAmount() {
        return Money.of(amount, currency);
    }
}
