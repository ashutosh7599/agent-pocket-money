package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
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
    @Mock AuthorizationHoldRepository holds;
    @Mock LedgerService ledger;
    private final Instant now=Instant.parse("2026-10-18T12:00:00Z");
    private PaymentService service;
    private Wallet wallet;

    @BeforeEach void setUp() {
        service=new PaymentService(wallets,payments,holds,ledger,Clock.fixed(now, ZoneOffset.UTC),900);
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
        when(holds.findExpiredForUpdate(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(10000L);
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
        when(holds.findExpiredForUpdate(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(wallet.getId(),AuthorizationHoldStatus.ACTIVE,now)).thenReturn(0L);
        assertThat(service.authorize(wallet.getId(),payment.getId()).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.getDeclineReason()).isEqualTo("INSUFFICIENT_AVAILABLE_BALANCE");
        verify(holds,never()).save(any(AuthorizationHold.class));
    }
}
