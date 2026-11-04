package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;
import com.agentpocketmoney.agentwallet.repository.PaymentRefundRepository;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentRefund;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import com.agentpocketmoney.agentwallet.model.wallet.PolicyDecision;
import com.agentpocketmoney.agentwallet.mock.GatewayResult;
import com.agentpocketmoney.agentwallet.mock.MockPaymentGateway;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.repository.AuthorizationHoldRepository;
import com.agentpocketmoney.agentwallet.repository.PaymentRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
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
class PaymentServiceTest {
    @Mock WalletRepository wallets;
    @Mock PaymentRepository payments;
    @Mock MockPaymentGateway gateway;
    @Mock OutboxService outbox;
    @Mock WalletPolicyService policy;
    @Mock AuthorizationHoldRepository holds;
    @Mock PaymentRefundRepository refunds;
    @Mock LedgerAccountRepository ledgerAccounts;
    @Mock LedgerService ledger;
    private final Instant now=Instant.parse("2026-10-18T12:00:00Z");
    private PaymentService service;
    private Wallet wallet;

    @BeforeEach void setUp() {
        service=new PaymentService(wallets,payments,holds,gateway,policy,outbox,refunds,ledgerAccounts,ledger,Clock.fixed(now, ZoneOffset.UTC),900);
        wallet=new Wallet(UUID.randomUUID(),new LedgerAccount("wallet-test","Wallet", AccountType.LIABILITY,"INR"),"INR");
    }

    @Test void createsPaymentAndReturnsSamePaymentOnMatchingIdempotentRetry() {
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findByWalletIdAndIdempotencyKey(wallet.getId(),"p-1"))
                .thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
        Payment first=service.create(wallet.getId(),"p-1","merchant-a",2000,"FOOD","lunch");
        when(payments.findByWalletIdAndIdempotencyKey(wallet.getId(),"p-1")).thenReturn(Optional.of(first));
        Payment retry=service.create(wallet.getId(),"p-1","merchant-a",2000,"FOOD","lunch");
        assertThat(retry.getId()).isEqualTo(first.getId());
        verify(payments).save(any(Payment.class));
    }

    @Test void rejectsConflictingPaymentIdempotencyKey() {
        Payment prior=new Payment(UUID.randomUUID(),wallet,"merchant-a",2000,"INR","FOOD","lunch","p-2",now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findByWalletIdAndIdempotencyKey(wallet.getId(),"p-2")).thenReturn(Optional.of(prior));
        assertThatThrownBy(() -> service.create(wallet.getId(),"p-2","merchant-a",3000,"FOOD","lunch"))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException)e).getErrorCode()).isEqualTo(WalletErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test void authorizesAndCreatesFundsHoldOnlyOnce() {
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",20000,"INR","FOOD","lunch","p-3",now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),payment.getId())).thenReturn(Optional.of(payment));
        allowPolicy(payment);
        when(holds.findExpiredForUpdate(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(10000L);
        when(gateway.authorize(payment)).thenReturn(new GatewayResult("gw-"+payment.getId(),"net-"+payment.getId(),"00",GatewayResult.Outcome.APPROVED));
        when(holds.save(any(AuthorizationHold.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.authorize(wallet.getId(),payment.getId()).getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        UUID holdId=payment.getAuthorizationHoldId();
        assertThat(holdId).isNotNull();
        assertThat(service.authorize(wallet.getId(),payment.getId()).getAuthorizationHoldId()).isEqualTo(holdId);
        verify(holds).save(any(AuthorizationHold.class));
    }

    @Test void insufficientFundsDeclinesPaymentAndDoesNotCreateHold() {
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",70000,"INR","FOOD","lunch","p-4",now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),payment.getId())).thenReturn(Optional.of(payment));
        allowPolicy(payment);
        when(holds.findExpiredForUpdate(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(0L);
        assertThat(service.authorize(wallet.getId(),payment.getId()).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getDeclineReason()).isEqualTo("INSUFFICIENT_AVAILABLE_BALANCE");
        verify(holds,never()).save(any(AuthorizationHold.class));
    }
    @Test void capturePostsLedgerOnceAndCapturesAuthorizationHold() {
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",20000,"INR","FOOD","lunch","p-5",now);
        AuthorizationHold hold=new AuthorizationHold(UUID.randomUUID(),wallet,20000,now.plusSeconds(600),now);
        payment.authorize(hold.getId(),now);
        LedgerAccount merchantAccount=new LedgerAccount("merchant-payable-test","Merchant",AccountType.LIABILITY,"INR");
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),payment.getId())).thenReturn(Optional.of(payment));
        when(holds.findByWalletIdAndIdForUpdate(wallet.getId(),hold.getId())).thenReturn(Optional.of(hold));
        when(ledger.createAccount(anyString(),anyString(),eq(AccountType.LIABILITY),eq("INR"))).thenReturn(merchantAccount);
        when(ledger.post(anyString(),anyString(),anyList())).thenReturn(new JournalEntry("capture", "capture"));
        assertThat(service.capture(wallet.getId(),payment.getId(),"capture-key").getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(hold.getStatus()).isEqualTo(AuthorizationHoldStatus.CAPTURED);
        service.capture(wallet.getId(),payment.getId(),"capture-key");
        verify(ledger).post(anyString(),anyString(),anyList());
    }

    @Test void voidReleasesTheAuthorizationHoldAndCanBeRetried() {
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",20000,"INR","FOOD","lunch","p-6",now);
        AuthorizationHold hold=new AuthorizationHold(UUID.randomUUID(),wallet,20000,now.plusSeconds(600),now);
        payment.authorize(hold.getId(),now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),payment.getId())).thenReturn(Optional.of(payment));
        when(holds.findByWalletIdAndIdForUpdate(wallet.getId(),hold.getId())).thenReturn(Optional.of(hold));
        assertThat(service.voidPayment(wallet.getId(),payment.getId(),"void-key").getStatus()).isEqualTo(PaymentStatus.VOIDED);
        service.voidPayment(wallet.getId(),payment.getId(),"void-key");
        assertThat(hold.getStatus()).isEqualTo(AuthorizationHoldStatus.RELEASED);
    }

    @Test void partialRefundsAreIdempotentAndCannotExceedCapturedAmount() {
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",10000,"INR","FOOD","lunch","p-7",now);
        UUID merchantId=UUID.randomUUID();
        payment.authorize(UUID.randomUUID(),now); payment.capture(merchantId,"capture-key",now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),payment.getId())).thenReturn(Optional.of(payment));
        when(refunds.findByPaymentIdAndIdempotencyKey(payment.getId(),"r-1")).thenReturn(Optional.empty());
        when(refunds.save(any(PaymentRefund.class))).thenAnswer(call -> call.getArgument(0));
        when(ledger.post(anyString(),anyString(),anyList())).thenReturn(new JournalEntry("refund","refund"));
        PaymentRefund refund=service.refund(wallet.getId(),payment.getId(),"r-1",4000);
        when(refunds.findByPaymentIdAndIdempotencyKey(payment.getId(),"r-1")).thenReturn(Optional.of(refund));
        assertThat(service.refund(wallet.getId(),payment.getId(),"r-1",4000).getId()).isEqualTo(refund.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThatThrownBy(() -> service.refund(wallet.getId(),payment.getId(),"r-2",7000))
                .isInstanceOf(WalletException.class)
                .extracting(e -> ((WalletException)e).getErrorCode()).isEqualTo(WalletErrorCode.REFUND_EXCEEDS_CAPTURED_AMOUNT);
        verify(ledger).post(anyString(),anyString(),anyList());
    }

    @Test void issuerDeclineAndGatewayTimeoutHaveDeterministicFailureStates() {
        Payment declined=new Payment(UUID.randomUUID(),wallet,"merchant-a",1000,"INR","FOOD","lunch","p-d",GatewayScenario.DECLINE,now);
        Payment timedOut=new Payment(UUID.randomUUID(),wallet,"merchant-a",1000,"INR","FOOD","lunch","p-t",GatewayScenario.TIMEOUT,now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),declined.getId())).thenReturn(Optional.of(declined));
        when(payments.findForUpdate(wallet.getId(),timedOut.getId())).thenReturn(Optional.of(timedOut));
        when(holds.findExpiredForUpdate(eq(wallet.getId()),eq(AuthorizationHoldStatus.ACTIVE),eq(now))).thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId())).thenReturn(50000L);
        when(holds.sumActiveAmount(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(0L);
        allowPolicy(declined); allowPolicy(timedOut);
        when(gateway.authorize(declined)).thenReturn(new GatewayResult("gw-"+declined.getId(),"net-"+declined.getId(),"05",GatewayResult.Outcome.DECLINED));
        when(gateway.authorize(timedOut)).thenReturn(new GatewayResult("gw-"+timedOut.getId(),null,"GW_TIMEOUT",GatewayResult.Outcome.TIMEOUT));
        assertThat(service.authorize(wallet.getId(),declined.getId()).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(declined.getIssuerResponseCode()).isEqualTo("05");
        assertThat(service.authorize(wallet.getId(),timedOut.getId()).getStatus()).isEqualTo(PaymentStatus.AUTHORIZATION_FAILED);
        assertThat(timedOut.getDeclineReason()).isEqualTo("GATEWAY_TIMEOUT");
        verify(holds,never()).save(any(AuthorizationHold.class));
    }

    @Test void otpRequiredCreatesHoldAndLeavesPaymentPending() {
        Payment payment=new Payment(UUID.randomUUID(),wallet,"merchant-a",10000,"INR","FOOD","lunch","p-otp",GatewayScenario.OTP_REQUIRED,now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(payments.findForUpdate(wallet.getId(),payment.getId())).thenReturn(Optional.of(payment));
        when(holds.findExpiredForUpdate(eq(wallet.getId()),eq(AuthorizationHoldStatus.ACTIVE),eq(now))).thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId())).thenReturn(50000L);
        when(holds.sumActiveAmount(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(0L);
        allowPolicy(payment);
        when(gateway.authorize(payment)).thenReturn(new GatewayResult("gw-"+payment.getId(),"net-"+payment.getId(),"65",GatewayResult.Outcome.OTP_REQUIRED));
        when(holds.save(any(AuthorizationHold.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.authorize(wallet.getId(),payment.getId()).getStatus()).isEqualTo(PaymentStatus.PENDING_APPROVAL);
        assertThat(payment.getApprovalReason()).isEqualTo("OTP_REQUIRED");
        assertThat(payment.getNetworkTransactionId()).isNotBlank();
        verify(holds).save(any(AuthorizationHold.class));
    }

    private void allowPolicy(Payment payment) {
        when(policy.evaluate(eq(wallet),eq(payment),eq(now)))
                .thenReturn(new WalletPolicyService.Decision(PolicyDecision.APPROVED,null));
    }

}
