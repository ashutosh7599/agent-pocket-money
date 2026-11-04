package com.agentpocketmoney.agentwallet.mock;

import static org.assertj.core.api.Assertions.assertThat;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {
    private final MockPaymentGateway gateway=new MockPaymentGateway(new MockCardNetwork(new MockIssuer()));
    private Payment payment(GatewayScenario scenario){
        Wallet wallet=new Wallet(UUID.randomUUID(),new LedgerAccount("wallet-test","Wallet",AccountType.LIABILITY,"INR"),"INR");
        return new Payment(UUID.randomUUID(),wallet,"merchant-a",1000,"INR","FOOD","test","key",scenario,Instant.now());
    }
    @Test void approvedFlowHasDeterministicGatewayNetworkAndIssuerIds(){
        Payment p=payment(GatewayScenario.APPROVE);
        GatewayResult r=gateway.authorize(p);
        assertThat(r.outcome()).isEqualTo(GatewayResult.Outcome.APPROVED);
        assertThat(r.gatewayPaymentId()).isEqualTo("gw-"+p.getId());
        assertThat(r.networkTransactionId()).isEqualTo("net-"+p.getId());
        assertThat(r.issuerResponseCode()).isEqualTo("00");
    }
    @Test void issuerDeclineIsDeterministic(){
        assertThat(gateway.authorize(payment(GatewayScenario.DECLINE)).issuerResponseCode()).isEqualTo("05");
    }
    @Test void otpScenarioReturnsIssuerChallenge(){
        assertThat(gateway.authorize(payment(GatewayScenario.OTP_REQUIRED)).outcome()).isEqualTo(GatewayResult.Outcome.OTP_REQUIRED);
    }
    @Test void timeoutStopsBeforeNetworkAndIssuer(){
        GatewayResult r=gateway.authorize(payment(GatewayScenario.TIMEOUT));
        assertThat(r.outcome()).isEqualTo(GatewayResult.Outcome.TIMEOUT);
        assertThat(r.networkTransactionId()).isNull();
        assertThat(r.issuerResponseCode()).isEqualTo("GW_TIMEOUT");
    }
}
