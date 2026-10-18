package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.agentpocketmoney.agentwallet.dto.CreateSpendMandateRequest;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.agent.*;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.wallet.*;
import com.agentpocketmoney.agentwallet.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentPaymentServiceTest {
    @Mock SpendMandateRepository mandates; @Mock WalletRepository wallets; @Mock PaymentService payments;
    private final Instant now=Instant.parse("2026-10-18T12:00:00Z");
    private AgentPaymentService service; private Wallet wallet; private SpendMandate mandate;
    @BeforeEach void setUp(){
        service=new AgentPaymentService(mandates,wallets,payments,Clock.fixed(now,ZoneOffset.UTC));
        wallet=new Wallet(UUID.randomUUID(),new LedgerAccount("wallet-agent","Wallet",AccountType.LIABILITY,"INR"),"INR");
        mandate=new SpendMandate(UUID.randomUUID(),wallet,"FOOD_DELIVERY","foodapp-demo",12000,"INR","FOOD",now.plusSeconds(3600),now);
    }
    @Test void createsMandateBoundToWalletAndExpiresInFuture(){
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(mandates.save(any(SpendMandate.class))).thenAnswer(call->call.getArgument(0));
        var request=new CreateSpendMandateRequest("FOOD_DELIVERY","foodapp-demo",12000,"INR","FOOD",now.plusSeconds(3600));
        assertThat(service.create(wallet.getId(),request).getWallet().getId()).isEqualTo(wallet.getId());
    }
    @Test void refusesAmountAboveMandateBeforeCreatingPayment(){
        when(mandates.findForUpdate(wallet.getId(),mandate.getId())).thenReturn(Optional.of(mandate));
        assertThatThrownBy(()->service.requestPayment(wallet.getId(),mandate.getId(),"agent-key",12001))
                .isInstanceOf(WalletException.class).extracting("errorCode").isEqualTo(WalletErrorCode.MANDATE_LIMIT_EXCEEDED);
        verify(payments,never()).create(any(),any(),any(),anyLong(),any(),any(),any());
    }
    @Test void usesMandateAndAuthorizesPaymentThenMakesRetryIdempotent(){
        when(mandates.findForUpdate(wallet.getId(),mandate.getId())).thenReturn(Optional.of(mandate));
        Payment payment=new Payment(UUID.randomUUID(),wallet,"foodapp-demo",10000,"INR","FOOD","FOOD_DELIVERY","agent-key",now);
        when(payments.create(wallet.getId(),"agent-key","foodapp-demo",10000,"FOOD","FOOD_DELIVERY",GatewayScenario.APPROVE)).thenReturn(payment);
        when(payments.authorize(wallet.getId(),payment.getId())).thenAnswer(call->{payment.authorize(UUID.randomUUID(),now);return payment;});
        when(payments.get(wallet.getId(),payment.getId())).thenReturn(payment);
        assertThat(service.requestPayment(wallet.getId(),mandate.getId(),"agent-key",10000).getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(mandate.getStatus()).isEqualTo(SpendMandateStatus.CONSUMED);
        assertThat(service.requestPayment(wallet.getId(),mandate.getId(),"agent-key",10000).getId()).isEqualTo(payment.getId());
        verify(payments,times(1)).create(eq(wallet.getId()),eq("agent-key"),eq("foodapp-demo"),eq(10000L),eq("FOOD"),eq("FOOD_DELIVERY"),eq(GatewayScenario.APPROVE));
    }
    @Test void rejectsReuseOfConsumedMandateWithDifferentKey(){
        mandate.consume(UUID.randomUUID());when(mandates.findForUpdate(wallet.getId(),mandate.getId())).thenReturn(Optional.of(mandate));
        Payment previous=new Payment(mandate.getConsumedPaymentId(),wallet,"foodapp-demo",10000,"INR","FOOD","FOOD_DELIVERY","old-key",now);
        when(payments.get(wallet.getId(),previous.getId())).thenReturn(previous);
        assertThatThrownBy(()->service.requestPayment(wallet.getId(),mandate.getId(),"new-key",10000))
                .isInstanceOf(WalletException.class).extracting("errorCode").isEqualTo(WalletErrorCode.MANDATE_CONSUMED);
    }
}
