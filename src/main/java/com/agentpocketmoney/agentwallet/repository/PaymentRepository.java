package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("select coalesce(sum(p.amountMinorUnits),0) from Payment p where p.wallet.id=:walletId and p.createdAt>=:from and p.status in :statuses")
    long sumSpendSince(@Param("walletId") UUID walletId,@Param("from") java.time.Instant from,@Param("statuses") List<PaymentStatus> statuses);
    @Query("select coalesce(sum(p.amountMinorUnits),0) from Payment p where p.wallet.id=:walletId and p.category=:category and p.createdAt>=:from and p.status in :statuses")
    long sumCategorySpendSince(@Param("walletId") UUID walletId,@Param("category") String category,@Param("from") java.time.Instant from,@Param("statuses") List<PaymentStatus> statuses);
    @Query("select count(p) from Payment p where p.wallet.id=:walletId and p.createdAt>=:from and p.status in :statuses")
    long countSpendSince(@Param("walletId") UUID walletId,@Param("from") java.time.Instant from,@Param("statuses") List<PaymentStatus> statuses);

    Optional<Payment> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.wallet.id = :walletId and p.id = :paymentId")
    Optional<Payment> findForUpdate(@Param("walletId") UUID walletId, @Param("paymentId") UUID paymentId);
}
