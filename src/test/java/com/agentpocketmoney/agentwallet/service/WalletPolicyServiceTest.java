package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.wallet.*;
import com.agentpocketmoney.agentwallet.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletPolicyServiceTest {
    @Mock WalletRepository wallets;
    @Mock WalletPolicyRuleRepository rules;
    @Mock PolicyEvaluationRepository evaluations;
    @Mock PaymentRepository payments;
    private final Instant now=Instant.parse("2026-10-18T12:00:00Z");
    private WalletPolicyService service;
    private Wallet wallet;
    private Payment payment;

    @BeforeEach void setUp(){
        service=new WalletPolicyService(wallets,rules,evaluations,payments,Clock.fixed(now,ZoneOffset.UTC));
        wallet=new Wallet(UUID.randomUUID(),new LedgerAccount("wallet-test","Wallet",AccountType.LIABILITY,"INR"),"INR");
        payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",20000,"INR","FOOD","lunch","p-1",now);
    }
    private WalletPolicyRule rule(PolicyRuleType type,String key,Long amount,Integer count,Long seconds){
        return new WalletPolicyRule(UUID.randomUUID(),wallet,type,key,amount,count,seconds,now);
    }

    @Test void allowlistedPaymentWithinLimitsIsApproved(){
        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of(
                rule(PolicyRuleType.MERCHANT_ALLOWLIST,"merchant-a",1L,null,null),
                rule(PolicyRuleType.MAX_TRANSACTION_AMOUNT,null,30000L,null,null)));
        WalletPolicyService.Decision result=service.evaluate(wallet,payment,now);
        assertThat(result.result()).isEqualTo(PolicyDecision.APPROVED);
        verify(evaluations).save(any(PolicyEvaluation.class));
    }

    @Test void merchantIsDeniedWhenNotAllowlisted(){
        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of());
        assertThat(service.evaluate(wallet,payment,now))
                .isEqualTo(new WalletPolicyService.Decision(PolicyDecision.DECLINED,"MERCHANT_NOT_ALLOWED"));
    }

    @Test void maximumAmountLimitDeclinesPayment(){
        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of(
                rule(PolicyRuleType.MERCHANT_ALLOWLIST,"merchant-a",1L,null,null),
                rule(PolicyRuleType.MAX_TRANSACTION_AMOUNT,null,10000L,null,null)));
        assertThat(service.evaluate(wallet,payment,now).reasonCode()).isEqualTo("TRANSACTION_LIMIT_EXCEEDED");
    }

    @Test void dailySpendLimitIncludesEarlierAuthorizedSpend(){
        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of(
                rule(PolicyRuleType.MERCHANT_ALLOWLIST,"merchant-a",1L,null,null),
                rule(PolicyRuleType.DAILY_SPEND_LIMIT,null,30000L,null,null)));
        when(payments.sumSpendSince(eq(wallet.getId()),eq(now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)),any()))
                .thenReturn(15000L);
        assertThat(service.evaluate(wallet,payment,now).reasonCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
    }

    @Test void categoryBudgetDeclinesAndApprovalThresholdReturnsPending(){
        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of(
                rule(PolicyRuleType.MERCHANT_ALLOWLIST,"merchant-a",1L,null,null),
                rule(PolicyRuleType.CATEGORY_BUDGET,"FOOD",10000L,null,null)));
        when(payments.sumCategorySpendSince(eq(wallet.getId()),eq("FOOD"),
                eq(now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)),any())).thenReturn(0L);
        assertThat(service.evaluate(wallet,payment,now).reasonCode()).isEqualTo("CATEGORY_BUDGET_EXCEEDED");

        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of(
                rule(PolicyRuleType.MERCHANT_ALLOWLIST,"merchant-a",1L,null,null),
                rule(PolicyRuleType.MANUAL_APPROVAL_THRESHOLD,null,15000L,null,null)));
        WalletPolicyService.Decision decision=service.evaluate(wallet,payment,now);
        assertThat(decision.result()).isEqualTo(PolicyDecision.PENDING_APPROVAL);
        assertThat(decision.reasonCode()).isEqualTo("MANUAL_APPROVAL_REQUIRED");
    }

    @Test void velocityLimitDeclinesExcessRequestRate(){
        when(rules.findByWalletId(wallet.getId())).thenReturn(List.of(
                rule(PolicyRuleType.MERCHANT_ALLOWLIST,"merchant-a",1L,null,null),
                rule(PolicyRuleType.VELOCITY_LIMIT,null,null,2,60L)));
        when(payments.countSpendSince(eq(wallet.getId()),eq(now.minusSeconds(60)),any())).thenReturn(2L);
        assertThat(service.evaluate(wallet,payment,now).reasonCode()).isEqualTo("VELOCITY_LIMIT_EXCEEDED");
    }
}
