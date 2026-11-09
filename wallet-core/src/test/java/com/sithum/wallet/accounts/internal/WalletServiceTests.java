package com.sithum.wallet.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sithum.wallet.accounts.DuplicateWalletException;
import com.sithum.wallet.accounts.WalletNotFoundException;
import com.sithum.wallet.ledger.LedgerAccounts;
import com.sithum.wallet.ledger.Money;
import com.sithum.wallet.ledger.UnsupportedCurrencyException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class WalletServiceTests {

    private final LedgerAccounts ledger = mock(LedgerAccounts.class);
    private final WalletProfileRepository profiles = mock(WalletProfileRepository.class);
    private final WalletStatusHistoryRepository history = mock(WalletStatusHistoryRepository.class);
    private final WalletService service = new WalletService(ledger, profiles, history);
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

    @Test
    void getReturnsTheOwnersWalletWithBalanceFromTheLedger() {
        UUID accountId = UUID.randomUUID();
        when(profiles.findById(accountId)).thenReturn(Optional.of(new WalletProfileEntity(accountId, owner, "LKR")));
        when(ledger.balanceOf(accountId)).thenReturn(Money.of(12_345, "LKR"));

        WalletDto wallet = service.get(owner, accountId);

        assertThat(wallet).isEqualTo(new WalletDto(accountId, "LKR", 12_345, WalletStatus.ACTIVE));
    }

    @Test
    void getOfAMissingWalletIsNotFoundAndDoesNotTouchTheLedger() {
        UUID id = UUID.randomUUID();
        when(profiles.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(owner, id)).isInstanceOf(WalletNotFoundException.class);
        verify(ledger, never()).balanceOf(any());
    }

    @Test
    void getOfSomeoneElsesWalletIsNotFoundAndDoesNotTouchTheLedger() {
        UUID accountId = UUID.randomUUID();
        when(profiles.findById(accountId))
                .thenReturn(Optional.of(new WalletProfileEntity(accountId, UUID.randomUUID(), "USD")));

        assertThatThrownBy(() -> service.get(owner, accountId)).isInstanceOf(WalletNotFoundException.class);
        verify(ledger, never()).balanceOf(any());
    }

    @Test
    void listReturnsTheOwnersWalletsWithBalancesAndPageMetadata() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(profiles.findByOwnerId(any(), pageable.capture())).thenReturn(new PageImpl<>(
                List.of(new WalletProfileEntity(a, owner, "LKR"), new WalletProfileEntity(b, owner, "USD")),
                org.springframework.data.domain.PageRequest.of(1, 2), 5));
        when(ledger.balanceOf(a)).thenReturn(Money.of(100, "LKR"));
        when(ledger.balanceOf(b)).thenReturn(Money.of(0, "USD"));

        PageDto<WalletDto> page = service.list(owner, 1, 2);

        assertThat(page.content()).containsExactly(new WalletDto(a, "LKR", 100, WalletStatus.ACTIVE),
                new WalletDto(b, "USD", 0, WalletStatus.ACTIVE));
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("createdAt", "ledgerAccountId"));
        verify(profiles).findByOwnerId(owner, pageable.getValue());
    }

    @Test
    void listForAnOwnerWithNoWalletsIsEmpty() {
        when(profiles.findByOwnerId(any(), any())).thenReturn(Page.empty());

        PageDto<WalletDto> page = service.list(owner, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        verify(ledger, never()).balanceOf(any());
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("violation",
                new ConstraintViolationException("violation", new SQLException(), constraint));
    }

    @Test
    void changeStatusFreezesAndRecordsTheActorAndAStrippedReason() {
        UUID accountId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(profiles.findForUpdate(accountId))
                .thenReturn(Optional.of(new WalletProfileEntity(accountId, owner, "USD")));
        when(ledger.balanceOf(accountId)).thenReturn(Money.of(5, "USD"));

        WalletDto wallet = service.changeStatus(actor, accountId, WalletStatus.FROZEN, "  fraud  ");

        assertThat(wallet).isEqualTo(new WalletDto(accountId, "USD", 5, WalletStatus.FROZEN));
        verify(history).save(any(WalletStatusHistoryEntity.class));
    }

    @Test
    void changeStatusToTheCurrentStatusWritesNoHistory() {
        UUID accountId = UUID.randomUUID();
        when(profiles.findForUpdate(accountId))
                .thenReturn(Optional.of(new WalletProfileEntity(accountId, owner, "USD")));
        when(ledger.balanceOf(accountId)).thenReturn(Money.of(0, "USD"));

        WalletDto wallet = service.changeStatus(owner, accountId, WalletStatus.ACTIVE, "noop");

        assertThat(wallet.status()).isEqualTo(WalletStatus.ACTIVE);
        verify(history, never()).save(any());
    }

    @Test
    void changeStatusOfAMissingWalletThrowsNotFound() {
        UUID accountId = UUID.randomUUID();
        when(profiles.findForUpdate(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(owner, accountId, WalletStatus.FROZEN, "x"))
                .isInstanceOf(WalletNotFoundException.class);
        verify(history, never()).save(any());
    }

    @Test
    void isActiveIsFalseOnlyForAFrozenWallet() {
        UUID accountId = UUID.randomUUID();
        WalletProfileEntity profile = new WalletProfileEntity(accountId, owner, "USD");
        when(profiles.findById(accountId)).thenReturn(Optional.of(profile));
        assertThat(service.isActive(accountId)).isTrue();

        profile.changeStatus(WalletStatus.FROZEN);
        assertThat(service.isActive(accountId)).isFalse();
    }
}
