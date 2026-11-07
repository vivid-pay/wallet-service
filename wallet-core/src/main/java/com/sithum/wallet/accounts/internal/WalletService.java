package com.sithum.wallet.accounts.internal;

import com.sithum.wallet.accounts.DuplicateWalletException;
import com.sithum.wallet.accounts.WalletNotFoundException;
import com.sithum.wallet.ledger.LedgerAccounts;
import com.sithum.wallet.ledger.Money;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WalletService {

    static final String OWNER_CURRENCY_CONSTRAINT = "uq_wallet_profile_owner_currency";

    private final LedgerAccounts ledger;
    private final WalletProfileRepository profiles;

    WalletService(LedgerAccounts ledger, WalletProfileRepository profiles) {
        this.ledger = ledger;
        this.profiles = profiles;
    }

    /**
     * Opens the ledger account and the wallet profile in one transaction: if either step fails,
     * neither is kept. The exists-check is only a fast path; the {@code (owner_id, currency)} unique
     * constraint is what makes concurrent creates safe.
     */
    @Transactional
    WalletDto create(UUID ownerId, String currency) {
        if (profiles.existsByOwnerIdAndCurrency(ownerId, currency)) {
            throw new DuplicateWalletException(ownerId, currency);
        }
        UUID accountId = ledger.openWalletAccount(currency);
        try {
            profiles.saveAndFlush(new WalletProfileEntity(accountId, ownerId, currency));
        } catch (DataIntegrityViolationException e) {
            if (violatesOwnerCurrencyConstraint(e)) {
                throw new DuplicateWalletException(ownerId, currency, e);
            }
            throw e;
        }
        return new WalletDto(accountId, currency, ledger.balanceOf(accountId).amount(), WalletStatus.ACTIVE);
    }

    /**
     * Returns one of the caller's wallets with its current balance, read through the ledger's public API.
     * A wallet that does not exist and one owned by someone else are indistinguishable: both throw
     * {@link WalletNotFoundException}.
     */
    @Transactional(readOnly = true)
    WalletDto get(UUID ownerId, UUID walletId) {
        WalletProfileEntity profile = profiles.findById(walletId)
                .filter(p -> p.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        Money balance = ledger.balanceOf(profile.getLedgerAccountId());
        return new WalletDto(profile.getLedgerAccountId(), balance.currency(), balance.amount(),
                profile.getStatus());
    }

    private static boolean violatesOwnerCurrencyConstraint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null
                    && cve.getConstraintName().toLowerCase().contains(OWNER_CURRENCY_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
