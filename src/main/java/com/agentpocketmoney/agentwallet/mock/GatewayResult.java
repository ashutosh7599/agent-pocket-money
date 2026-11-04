package com.agentpocketmoney.agentwallet.mock;
public record GatewayResult(String gatewayPaymentId,String networkTransactionId,String issuerResponseCode,Outcome outcome) {
 public enum Outcome { APPROVED, DECLINED, OTP_REQUIRED, TIMEOUT }
}
