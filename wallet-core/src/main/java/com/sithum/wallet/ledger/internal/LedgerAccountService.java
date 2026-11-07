package com.sithum.wallet.ledger.internal;

import com.sithum.wallet.ledger.LedgerAccountNotFoundException;
import com.sithum.wallet.ledger.LedgerAccounts;
import com.sithum.wallet.ledger.Money;
import com.sithum.wallet.ledger.UnsupportedCurrencyException;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LedgerAccountService implements LedgerAccounts {

    private final LedgerAccountRepository repository;
    private final Set<String> supportedCurrencies;

    LedgerAccountService(LedgerAccountRepository repository,
            @Value("${wallet.ledger.supported-currencies:USD,EUR,LKR}") Set<String> supportedCurrencies) {
        this.repository = repository;
        this.supportedCurrencies = Set.copyOf(supportedCurrencies);
    }

    @Override
    @Transactional
    public UUID openWalletAccount(String currency) {
        if (currency == null || !supportedCurrencies.contains(currency)) {
            throw new UnsupportedCurrencyException(currency);
        }
        return repository.save(LedgerAccountEntity.wallet(currency)).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Money balanceOf(UUID accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new LedgerAccountNotFoundException(accountId))
                .getBalance();
    }
}
