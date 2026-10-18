package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.config.AfterCommitAction;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.model.wallet.WalletStatus;
import com.agentpocketmoney.agentwallet.repository.AuthorizationHoldRepository;
import com.agentpocketmoney.agentwallet.repository.PaymentRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private final WalletRepository wallets;
    private final PaymentRepository payments;
    private final AuthorizationHoldRepository holds;
    private final LedgerService ledger;
    private final Clock clock;
    private final long authorizationTtlSeconds;

    public PaymentService(WalletRepository wallets, PaymentRepository payments, AuthorizationHoldRepository holds,
                          LedgerService ledger, Clock clock,
                          @Value("${payments.authorization-ttl-seconds:900}") long authorizationTtlSeconds) {
        this.wallets=wallets; this.payments=payments; this.holds=holds; this.ledger=ledger;
        this.clock=clock; this.authorizationTtlSeconds=authorizationTtlSeconds;
    }

    @Transactional
    public Payment create(UUID walletId, String idempotencyKey, String merchantId, long amountMinorUnits,
                          String category, String purpose) {
        validateCreate(idempotencyKey, merchantId, amountMinorUnits, category, purpose);
        Wallet wallet = lockWallet(walletId);
        ensureActive(wallet);
        Payment prior = payments.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey).orElse(null);
        if (prior != null) {
            if (prior.getAmountMinorUnits() != amountMinorUnits || !prior.getMerchantId().equals(merchantId)
                    || !prior.getCategory().equals(category) || !prior.getPurpose().equals(purpose)) {
                throw new WalletException(WalletErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency key was already used for a different payment.");
            }
            return prior;
        }
        Instant now=clock.instant();
        Payment payment=payments.save(new Payment(UUID.randomUUID(), wallet, merchantId, amountMinorUnits,
                wallet.getCurrency(), category, purpose, idempotencyKey, now));
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id", payment.getId())
                .addKeyValue("wallet.id", walletId).addKeyValue("amount.minor_units", amountMinorUnits)
                .log("Payment created"));
        return payment;
    }

    @Transactional
    public Payment authorize(UUID walletId, UUID paymentId) {
        Wallet wallet=lockWallet(walletId);
        ensureActive(wallet);
        Payment payment=payments.findForUpdate(walletId, paymentId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.PAYMENT_NOT_FOUND, "Payment not found."));
        if (payment.getStatus() != PaymentStatus.CREATED) return payment;
        Instant now=clock.instant();
        expireDueWalletHolds(walletId, now);
        long balance=ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId());
        long held=holds.sumActiveAmount(walletId, AuthorizationHoldStatus.ACTIVE, now);
        if (payment.getAmountMinorUnits() > balance-held) {
            payment.decline("INSUFFICIENT_AVAILABLE_BALANCE", now);
            AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id", paymentId)
                    .addKeyValue("wallet.id", walletId).addKeyValue("reason", payment.getDeclineReason())
                    .log("Payment authorization declined"));
            return payment;
        }
        Instant expiresAt=now.plusSeconds(authorizationTtlSeconds);
        AuthorizationHold hold=holds.save(new AuthorizationHold(UUID.randomUUID(), wallet,
                payment.getAmountMinorUnits(), expiresAt, now));
        payment.authorize(hold.getId(), now);
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id", paymentId)
                .addKeyValue("wallet.id", walletId).addKeyValue("authorization_hold.id", hold.getId())
                .addKeyValue("amount.minor_units", payment.getAmountMinorUnits())
                .log("Payment authorized"));
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID walletId, UUID paymentId) {
        if (!wallets.existsById(walletId))
            throw new WalletException(WalletErrorCode.WALLET_NOT_FOUND, "Wallet not found.");
        return payments.findById(paymentId).filter(payment -> payment.getWallet().getId().equals(walletId))
                .orElseThrow(() -> new WalletException(WalletErrorCode.PAYMENT_NOT_FOUND, "Payment not found."));
    }

    private void expireDueWalletHolds(UUID walletId, Instant now) {
        List<AuthorizationHold> due=holds.findExpiredForUpdate(walletId, AuthorizationHoldStatus.ACTIVE, now);
        due.forEach(hold -> hold.expire(now));
    }
    private Wallet lockWallet(UUID walletId) {
        return wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND, "Wallet not found."));
    }
    private static void ensureActive(Wallet wallet) {
        if (wallet.getStatus()==WalletStatus.FROZEN)
            throw new WalletException(WalletErrorCode.WALLET_FROZEN, "Wallet is frozen.");
        if (wallet.getStatus()==WalletStatus.CLOSED)
            throw new WalletException(WalletErrorCode.WALLET_CLOSED, "Wallet is closed.");
    }
    private static void validateCreate(String key, String merchant, long amount, String category, String purpose) {
        if (key==null || key.isBlank() || key.length()>128 || merchant==null || merchant.isBlank()
                || merchant.length()>64 || category==null || category.isBlank() || category.length()>32
                || purpose==null || purpose.isBlank() || purpose.length()>64 || amount<=0)
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST, "Payment request is invalid.");
    }
}
