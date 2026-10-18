package com.agentpocketmoney.agentwallet.mock;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import org.springframework.stereotype.Component;
@Component
public class MockIssuer {
 public IssuerResult authorize(Payment payment) {
  return switch(payment.getGatewayScenario()) {
   case APPROVE -> new IssuerResult(IssuerResult.Outcome.APPROVED,"00");
   case DECLINE -> new IssuerResult(IssuerResult.Outcome.DECLINED,"05");
   case OTP_REQUIRED -> new IssuerResult(IssuerResult.Outcome.OTP_REQUIRED,"65");
   case TIMEOUT -> throw new IllegalStateException("Timeout scenario must be handled by the mock gateway.");
  };
 }
}
