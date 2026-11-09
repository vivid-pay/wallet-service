package com.sithum.wallet.accounts.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WalletStatusHistoryRepository extends JpaRepository<WalletStatusHistoryEntity, UUID> {
}
