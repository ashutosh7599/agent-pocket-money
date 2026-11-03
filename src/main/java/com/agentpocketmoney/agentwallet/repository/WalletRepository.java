package com.agentpocketmoney.agentwallet.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agentpocketmoney.agentwallet.model.wallet.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from Wallet wallet where wallet.id = :walletId")
    Optional<Wallet> findByIdForUpdate(@Param("walletId") UUID walletId);
}
