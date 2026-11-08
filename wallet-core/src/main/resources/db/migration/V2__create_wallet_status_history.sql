-- Wallet status history (module `accounts`): who froze or unfroze a wallet, when and why.
-- Append-only: the current status stays on wallet_profile, this table is the audit trail.
CREATE TABLE wallet_status_history (
    id          UUID         PRIMARY KEY,
    wallet_id   UUID         NOT NULL,
    from_status VARCHAR(16)  NOT NULL,
    to_status   VARCHAR(16)  NOT NULL,
    actor_id    UUID         NOT NULL,
    reason      TEXT         NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_wallet_status_history_wallet FOREIGN KEY (wallet_id)
        REFERENCES wallet_profile (ledger_account_id),
    CONSTRAINT ck_wallet_status_history_from CHECK (from_status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_wallet_status_history_to CHECK (to_status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_wallet_status_history_changed CHECK (from_status <> to_status),
    CONSTRAINT ck_wallet_status_history_reason CHECK (btrim(reason) <> '')
);

CREATE INDEX ix_wallet_status_history_wallet ON wallet_status_history (wallet_id, changed_at);

-- Same guarantee as ledger_entry: history is never updated or deleted, whichever role connects.
CREATE FUNCTION reject_wallet_status_history_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'wallet_status_history is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_wallet_status_history_immutable
    BEFORE UPDATE OR DELETE ON wallet_status_history
    FOR EACH ROW EXECUTE FUNCTION reject_wallet_status_history_change();

CREATE TRIGGER trg_wallet_status_history_no_truncate
    BEFORE TRUNCATE ON wallet_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION reject_wallet_status_history_change();
