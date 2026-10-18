package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.wallet.id = :walletId and p.id = :paymentId")
    Optional<Payment> findForUpdate(@Param("walletId") UUID walletId, @Param("paymentId") UUID paymentId);
}
