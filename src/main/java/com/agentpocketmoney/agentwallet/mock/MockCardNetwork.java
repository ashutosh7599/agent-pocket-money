package com.agentpocketmoney.agentwallet.mock;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import org.springframework.stereotype.Component;
@Component
public class MockCardNetwork {
 private final MockIssuer issuer;
 public MockCardNetwork(MockIssuer issuer){this.issuer=issuer;}
 public NetworkResult authorize(Payment payment){
  return new NetworkResult("net-"+payment.getId(),issuer.authorize(payment));
 }
}
