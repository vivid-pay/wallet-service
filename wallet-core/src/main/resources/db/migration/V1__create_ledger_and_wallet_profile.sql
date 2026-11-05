-- Ledger (module `ledger`): accounts with a cached balance, and immutable entries.
-- See ADR-0002 (ledger and balance) and ADR-0003 (money as minor units).

CREATE TABLE ledger_account (
    id         UUID         PRIMARY KEY,
    type       VARCHAR(16)  NOT NULL,
    code       VARCHAR(32),
    currency   CHAR(3)      NOT NULL,
    balance    BIGINT       NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_account_type CHECK (type IN ('WALLET', 'SYSTEM')),
    CONSTRAINT ck_ledger_account_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- wallets cannot go negative; system accounts (e.g. EXTERNAL_FUNDING) can
    CONSTRAINT ck_ledger_account_wallet_balance CHECK (type <> 'WALLET' OR balance >= 0),
    -- only system accounts have a code, and every system account has one
    CONSTRAINT ck_ledger_account_code CHECK ((type = 'SYSTEM') = (code IS NOT NULL)),
    CONSTRAINT uq_ledger_account_id_currency UNIQUE (id, currency),
    CONSTRAINT uq_ledger_account_code_currency UNIQUE (code, currency)
);

CREATE TABLE ledger_entry (
    id             UUID        PRIMARY KEY,
    transaction_id UUID        NOT NULL,
    account_id     UUID        NOT NULL,
    amount         BIGINT      NOT NULL,
    currency       CHAR(3)     NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_entry_amount CHECK (amount <> 0),
    CONSTRAINT ck_ledger_entry_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- an entry is in the same currency as its account
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (account_id, currency)
        REFERENCES ledger_account (id, currency)
);

CREATE INDEX ix_ledger_entry_account ON ledger_entry (account_id, created_at);
CREATE INDEX ix_ledger_entry_transaction ON ledger_entry (transaction_id);

-- Entries are immutable: corrections are reversal entries (ADR-0002).
-- A trigger applies to every role, so it holds whichever user the application connects as.
CREATE FUNCTION reject_ledger_entry_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'ledger_entry is immutable: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_ledger_entry_immutable
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_entry_change();

CREATE TRIGGER trg_ledger_entry_no_truncate
    BEFORE TRUNCATE ON ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_entry_change();

-- Wallet profile (module `accounts`): owner and status of a wallet.
-- ledger_account_id refers to the ledger module's account by id only; there is
-- deliberately no foreign key across modules.
CREATE TABLE wallet_profile (
    ledger_account_id UUID        PRIMARY KEY,
    owner_id          UUID        NOT NULL,
    currency          CHAR(3)     NOT NULL,
    status            VARCHAR(16) NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_wallet_profile_status CHECK (status IN ('ACTIVE', 'FROZEN')),
    CONSTRAINT ck_wallet_profile_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT uq_wallet_profile_owner_currency UNIQUE (owner_id, currency)
);

-- System accounts for the launch currencies.
INSERT INTO ledger_account (id, type, code, currency)
SELECT gen_random_uuid(), 'SYSTEM', c.code, cur.currency
FROM (VALUES ('EXTERNAL_FUNDING'), ('FEES')) AS c (code)
CROSS JOIN (VALUES ('USD'), ('EUR'), ('LKR')) AS cur (currency);
