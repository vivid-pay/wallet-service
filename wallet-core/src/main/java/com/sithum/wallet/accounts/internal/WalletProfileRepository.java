package com.sithum.wallet.accounts.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WalletProfileRepository extends JpaRepository<WalletProfileEntity, UUID> {

    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);
}
