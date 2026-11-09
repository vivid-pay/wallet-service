package com.sithum.wallet.accounts.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WalletProfileRepository extends JpaRepository<WalletProfileEntity, UUID> {

    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);

    Page<WalletProfileEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    /** Loads the profile holding a row lock, so two concurrent status changes cannot both record the same history. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WalletProfileEntity p where p.ledgerAccountId = :id")
    Optional<WalletProfileEntity> findForUpdate(@Param("id") UUID id);
}
