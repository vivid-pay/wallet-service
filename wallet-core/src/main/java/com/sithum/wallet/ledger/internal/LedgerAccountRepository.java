package com.sithum.wallet.ledger.internal;

import com.sithum.wallet.ledger.LedgerAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
}
