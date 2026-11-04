package com.agentpocketmoney.agentwallet.model.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentApprovalTest {
    private final Instant now=Instant.parse("2026-10-18T12:00:00Z");
    private Payment pending(){
        Wallet wallet=new Wallet(UUID.randomUUID(),new LedgerAccount("approval-wallet","Wallet",AccountType.LIABILITY,"INR"),"INR");
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant",1000,"INR","FOOD","meal","key",now);
        payment.requestApproval(UUID.randomUUID(),"OTP_REQUIRED",now);return payment;
    }
    @Test void approvalIsAnIdempotentStateTransition(){
        Payment payment=pending();
        assertThat(payment.approvePending("decision-key",now.plusSeconds(1))).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getApprovalDecisionKey()).isEqualTo("decision-key");
        assertThat(payment.approvePending("other-key",now.plusSeconds(2))).isFalse();
    }
    @Test void rejectionRecordsAStableReasonAndDecisionKey(){
        Payment payment=pending();
        assertThat(payment.rejectPending("decision-key",now.plusSeconds(1))).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getDeclineReason()).isEqualTo("HUMAN_REJECTED");
        assertThat(payment.getApprovalDecision()).isEqualTo("REJECTED");
    }
}
