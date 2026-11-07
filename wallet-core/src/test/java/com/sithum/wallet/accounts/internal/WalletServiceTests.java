package com.sithum.wallet.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sithum.wallet.accounts.DuplicateWalletException;
import com.sithum.wallet.ledger.LedgerAccounts;
import com.sithum.wallet.ledger.Money;
import com.sithum.wallet.ledger.UnsupportedCurrencyException;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class WalletServiceTests {

    private final LedgerAccounts ledger = mock(LedgerAccounts.class);
    private final WalletProfileRepository profiles = mock(WalletProfileRepository.class);
    private final WalletService service = new WalletService(ledger, profiles);
    private final UUID owner = UUID.randomUUID();

    @Test
    void createsAnActiveWalletWithZeroBalance() {
        UUID accountId = UUID.randomUUID();
        when(ledger.openWalletAccount("LKR")).thenReturn(accountId);
        when(ledger.balanceOf(accountId)).thenReturn(Money.of(0, "LKR"));

        WalletDto wallet = service.create(owner, "LKR");

        assertThat(wallet).isEqualTo(new WalletDto(accountId, "LKR", 0, WalletStatus.ACTIVE));
        ArgumentCaptor<WalletProfileEntity> saved = ArgumentCaptor.forClass(WalletProfileEntity.class);
        verify(profiles).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getLedgerAccountId()).isEqualTo(accountId);
        assertThat(saved.getValue().getOwnerId()).isEqualTo(owner);
        assertThat(saved.getValue().getCurrency()).isEqualTo("LKR");
    }

    @Test
    void existingWalletIsRejectedBeforeOpeningALedgerAccount() {
        when(profiles.existsByOwnerIdAndCurrency(owner, "USD")).thenReturn(true);

        assertThatThrownBy(() -> service.create(owner, "USD")).isInstanceOf(DuplicateWalletException.class);
        verify(ledger, never()).openWalletAccount(any());
    }

    @Test
    void uniqueConstraintViolationBecomesDuplicate() {
        when(ledger.openWalletAccount("USD")).thenReturn(UUID.randomUUID());
        when(profiles.saveAndFlush(any())).thenThrow(violation("uq_wallet_profile_owner_currency"));

        assertThatThrownBy(() -> service.create(owner, "USD"))
                .isInstanceOf(DuplicateWalletException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otherIntegrityViolationsAreNotReportedAsDuplicates() {
        when(ledger.openWalletAccount("USD")).thenReturn(UUID.randomUUID());
        DataIntegrityViolationException other = violation("ck_wallet_profile_currency");
        when(profiles.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> service.create(owner, "USD")).isSameAs(other);
    }

    @Test
    void unsupportedCurrencyPropagatesAndSavesNothing() {
        when(ledger.openWalletAccount("JPY")).thenThrow(new UnsupportedCurrencyException("JPY"));

        assertThatThrownBy(() -> service.create(owner, "JPY")).isInstanceOf(UnsupportedCurrencyException.class);
        verify(profiles, never()).saveAndFlush(any());
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException(), constraint));
    }
}
