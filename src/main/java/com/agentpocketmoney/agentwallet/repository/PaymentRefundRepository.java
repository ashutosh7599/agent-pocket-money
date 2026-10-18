package com.agentpocketmoney.agentwallet.repository;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentRefund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    Optional<PaymentRefund> findByPaymentIdAndIdempotencyKey(UUID paymentId,String idempotencyKey);
}
