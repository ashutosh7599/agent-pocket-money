package com.agentpocketmoney.agentwallet.mock;
public record IssuerResult(Outcome outcome,String responseCode) {
 public enum Outcome { APPROVED, DECLINED, OTP_REQUIRED }
}
