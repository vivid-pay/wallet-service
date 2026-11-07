package com.sithum.wallet.accounts.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletProfileTests {

    @Test
    void newProfileIsActiveAndKeepsItsIdentity() {
        UUID account = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        WalletProfile profile = new WalletProfile(account, owner, "LKR");

        assertThat(profile.getLedgerAccountId()).isEqualTo(account);
        assertThat(profile.getOwnerId()).isEqualTo(owner);
        assertThat(profile.getCurrency()).isEqualTo("LKR");
        assertThat(profile.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }
}
