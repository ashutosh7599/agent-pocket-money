package com.agentpocketmoney.agentwallet.service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component public class AuthorizationHoldExpiryScheduler {
 private final WalletService wallets;
 public AuthorizationHoldExpiryScheduler(WalletService wallets){this.wallets=wallets;}
 @Scheduled(fixedDelayString = "${wallet.holds.expiry-poll-interval-ms:30000}")
 public void expireDueHolds(){wallets.expireDueHolds();}
}
