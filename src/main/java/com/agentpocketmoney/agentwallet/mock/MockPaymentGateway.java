package com.agentpocketmoney.agentwallet.mock;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import org.springframework.stereotype.Component;
@Component
public class MockPaymentGateway {
 private final MockCardNetwork network;
 public MockPaymentGateway(MockCardNetwork network){this.network=network;}
 public GatewayResult authorize(Payment payment){
  String gatewayId="gw-"+payment.getId();
  if(payment.getGatewayScenario()==GatewayScenario.TIMEOUT)
   return new GatewayResult(gatewayId,null,"GW_TIMEOUT",GatewayResult.Outcome.TIMEOUT);
  NetworkResult result=network.authorize(payment);
  IssuerResult issuer=result.issuerResult();
  GatewayResult.Outcome outcome=switch(issuer.outcome()){
   case APPROVED -> GatewayResult.Outcome.APPROVED;
   case DECLINED -> GatewayResult.Outcome.DECLINED;
   case OTP_REQUIRED -> GatewayResult.Outcome.OTP_REQUIRED;
  };
  return new GatewayResult(gatewayId,result.networkTransactionId(),issuer.responseCode(),outcome);
 }
}
