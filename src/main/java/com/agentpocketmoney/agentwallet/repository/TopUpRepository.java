package com.agentpocketmoney.agentwallet.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.agentpocketmoney.agentwallet.model.wallet.TopUp;

public interface TopUpRepository extends JpaRepository<TopUp, UUID> {
    Optional<TopUp> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);
}
