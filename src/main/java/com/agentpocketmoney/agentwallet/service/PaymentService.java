package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.config.AfterCommitAction;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import com.agentpocketmoney.agentwallet.model.wallet.PolicyDecision;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentRefund;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerPostingLine;
import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.model.wallet.WalletStatus;
import com.agentpocketmoney.agentwallet.repository.AuthorizationHoldRepository;
import com.agentpocketmoney.agentwallet.repository.PaymentRepository;
import com.agentpocketmoney.agentwallet.repository.PaymentRefundRepository;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import com.agentpocketmoney.agentwallet.mock.GatewayResult;
import com.agentpocketmoney.agentwallet.mock.MockPaymentGateway;
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
    private final MockPaymentGateway gateway;
    private final PaymentRepository payments;
    private final AuthorizationHoldRepository holds;
    private final LedgerService ledger;
    private final WalletPolicyService policy;
    private final PaymentRefundRepository refunds;
    private final LedgerAccountRepository ledgerAccounts;
    private final Clock clock;
    private final long authorizationTtlSeconds;

    public PaymentService(WalletRepository wallets, PaymentRepository payments, AuthorizationHoldRepository holds, MockPaymentGateway gateway, WalletPolicyService policy, PaymentRefundRepository refunds,
                          LedgerAccountRepository ledgerAccounts, LedgerService ledger, Clock clock,
                          @Value("${payments.authorization-ttl-seconds:900}") long authorizationTtlSeconds) {
        this.wallets=wallets; this.payments=payments; this.holds=holds; this.gateway=gateway; this.policy=policy; this.refunds=refunds;
        this.ledgerAccounts=ledgerAccounts; this.ledger=ledger;
        this.clock=clock; this.authorizationTtlSeconds=authorizationTtlSeconds;
    }

    @Transactional
    public Payment create(UUID walletId, String idempotencyKey, String merchantId, long amountMinorUnits,
                          String category, String purpose) {
        return create(walletId,idempotencyKey,merchantId,amountMinorUnits,category,purpose,GatewayScenario.APPROVE);
    }

    @Transactional
    public Payment create(UUID walletId, String idempotencyKey, String merchantId, long amountMinorUnits,
                          String category, String purpose, GatewayScenario gatewayScenario) {
        validateCreate(idempotencyKey, merchantId, amountMinorUnits, category, purpose);
        if(gatewayScenario==null) gatewayScenario=GatewayScenario.APPROVE;
        Wallet wallet = lockWallet(walletId);
        ensureActive(wallet);
        Payment prior = payments.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey).orElse(null);
        if (prior != null) {
            if (prior.getAmountMinorUnits() != amountMinorUnits || !prior.getMerchantId().equals(merchantId)
                    || !prior.getCategory().equals(category) || !prior.getPurpose().equals(purpose)
                    || prior.getGatewayScenario()!=gatewayScenario) {
                throw new WalletException(WalletErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency key was already used for a different payment.");
            }
            return prior;
        }
        Instant now=clock.instant();
        Payment payment=payments.save(new Payment(UUID.randomUUID(), wallet, merchantId, amountMinorUnits,
                wallet.getCurrency(), category, purpose, idempotencyKey, gatewayScenario, now));
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
        WalletPolicyService.Decision decision=policy.evaluate(wallet,payment,now);
        if (decision.result()==PolicyDecision.DECLINED) {
            payment.decline(decision.reasonCode(),now);
            logDecline(walletId,payment);
            return payment;
        }
        long balance=ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId());
        long held=holds.sumActiveAmount(walletId, AuthorizationHoldStatus.ACTIVE, now);
        if (payment.getAmountMinorUnits() > balance-held) {
            payment.decline("INSUFFICIENT_AVAILABLE_BALANCE", now);
            logDecline(walletId,payment);
            return payment;
        }
        GatewayResult gatewayResult=gateway.authorize(payment);
        payment.recordGatewayResult(gatewayResult.gatewayPaymentId(),gatewayResult.networkTransactionId(),
                gatewayResult.issuerResponseCode());
        if(gatewayResult.outcome()==GatewayResult.Outcome.DECLINED){
            payment.decline("ISSUER_DECLINED",now); logDecline(walletId,payment); return payment;
        }
        if(gatewayResult.outcome()==GatewayResult.Outcome.TIMEOUT){
            payment.fail("GATEWAY_TIMEOUT",now); logDecline(walletId,payment); return payment;
        }
        Instant expiresAt=now.plusSeconds(authorizationTtlSeconds);
        AuthorizationHold hold=holds.save(new AuthorizationHold(UUID.randomUUID(), wallet,
                payment.getAmountMinorUnits(), expiresAt, now));
        boolean pending=decision.result()==PolicyDecision.PENDING_APPROVAL
                || gatewayResult.outcome()==GatewayResult.Outcome.OTP_REQUIRED;
        if(pending) payment.requestApproval(hold.getId(),
                gatewayResult.outcome()==GatewayResult.Outcome.OTP_REQUIRED ? "OTP_REQUIRED" : decision.reasonCode(),now);
        else payment.authorize(hold.getId(),now);
        String event=pending ? "Payment awaiting approval" : "Payment authorized";
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id",paymentId)
                .addKeyValue("wallet.id",walletId).addKeyValue("authorization_hold.id",hold.getId())
                .addKeyValue("amount.minor_units",payment.getAmountMinorUnits()).log(event));
        return payment;
    }

    @Transactional
    public Payment capture(UUID walletId, UUID paymentId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        Wallet wallet=lockWallet(walletId);
        Payment payment=lockPayment(walletId,paymentId);
        if (payment.getCaptureIdempotencyKey()!=null) {
            if (idempotencyKey.equals(payment.getCaptureIdempotencyKey())) return payment;
            if (payment.getStatus()==PaymentStatus.CAPTURED
                    || payment.getStatus()==PaymentStatus.PARTIALLY_REFUNDED
                    || payment.getStatus()==PaymentStatus.REFUNDED)
                throw stateConflict("Payment was already captured with another idempotency key.");
        }
        if (payment.getStatus()!=PaymentStatus.AUTHORIZED)
            throw stateConflict("Only an authorized payment can be captured.");
        Instant now=clock.instant();
        AuthorizationHold hold=holdForPayment(walletId,payment);
        if (hold.getStatus()!=AuthorizationHoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(now)) {
            hold.expire(now);
            payment.expire(now);
            return payment;
        }
        LedgerAccount merchantAccount=ledger.createAccount("merchant-payable-"+paymentId,
                "Merchant payable for "+payment.getMerchantId(), AccountType.LIABILITY, payment.getCurrency());
        ledger.post("capture-"+paymentId,"Payment capture",List.of(
                new LedgerPostingLine(wallet.getLedgerAccount().getId(), PostingSide.DEBIT, payment.getAmountMinorUnits()),
                new LedgerPostingLine(merchantAccount.getId(), PostingSide.CREDIT, payment.getAmountMinorUnits())));
        hold.capture(now);
        payment.capture(merchantAccount.getId(),idempotencyKey,now);
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id",paymentId)
                .addKeyValue("wallet.id",walletId).addKeyValue("amount.minor_units",payment.getAmountMinorUnits())
                .log("Payment captured"));
        return payment;
    }

    @Transactional
    public Payment voidPayment(UUID walletId, UUID paymentId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        lockWallet(walletId);
        Payment payment=lockPayment(walletId,paymentId);
        if (payment.getStatus()==PaymentStatus.VOIDED) {
            if (idempotencyKey.equals(payment.getVoidIdempotencyKey())) return payment;
            throw stateConflict("Payment was already voided with another idempotency key.");
        }
        if (payment.getStatus()!=PaymentStatus.AUTHORIZED)
            throw stateConflict("Only an authorized payment can be voided.");
        Instant now=clock.instant();
        AuthorizationHold hold=holdForPayment(walletId,payment);
        if (hold.getStatus()!=AuthorizationHoldStatus.ACTIVE || !hold.getExpiresAt().isAfter(now)) {
            hold.expire(now);
            payment.expire(now);
            return payment;
        }
        hold.release(now);
        payment.voidPayment(idempotencyKey,now);
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id",paymentId)
                .addKeyValue("wallet.id",walletId).log("Payment voided"));
        return payment;
    }

    @Transactional
    public PaymentRefund refund(UUID walletId, UUID paymentId, String idempotencyKey, long amountMinorUnits) {
        requireIdempotencyKey(idempotencyKey);
        if (amountMinorUnits<=0) throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Refund amount must be positive.");
        Wallet wallet=lockWallet(walletId);
        Payment payment=lockPayment(walletId,paymentId);
        PaymentRefund prior=refunds.findByPaymentIdAndIdempotencyKey(paymentId,idempotencyKey).orElse(null);
        if (prior!=null) {
            if (prior.getAmountMinorUnits()!=amountMinorUnits)
                throw new WalletException(WalletErrorCode.IDEMPOTENCY_KEY_REUSED,"Refund idempotency key was reused with a different amount.");
            return prior;
        }
        if (payment.getStatus()!=PaymentStatus.CAPTURED && payment.getStatus()!=PaymentStatus.PARTIALLY_REFUNDED)
            throw stateConflict("Only a captured payment can be refunded.");
        long remaining=payment.getAmountMinorUnits()-payment.getAmountRefundedMinorUnits();
        if (amountMinorUnits>remaining)
            throw new WalletException(WalletErrorCode.REFUND_EXCEEDS_CAPTURED_AMOUNT,"Refund exceeds the remaining captured amount.");
        if (payment.getMerchantLedgerAccountId()==null) throw new IllegalStateException("Captured payment has no merchant ledger account.");
        UUID refundId=UUID.randomUUID();
        var entry=ledger.post("refund-"+refundId,"Payment refund",List.of(
                new LedgerPostingLine(payment.getMerchantLedgerAccountId(),PostingSide.DEBIT,amountMinorUnits),
                new LedgerPostingLine(wallet.getLedgerAccount().getId(),PostingSide.CREDIT,amountMinorUnits)));
        payment.refund(amountMinorUnits,clock.instant());
        PaymentRefund refund=refunds.save(new PaymentRefund(refundId,payment,entry,idempotencyKey,amountMinorUnits,clock.instant()));
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id",paymentId)
                .addKeyValue("payment.refund.id",refundId).addKeyValue("amount.minor_units",amountMinorUnits)
                .log("Payment refunded"));
        return refund;
    }

    private Payment lockPayment(UUID walletId,UUID paymentId) {
        return payments.findForUpdate(walletId,paymentId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.PAYMENT_NOT_FOUND,"Payment not found."));
    }
    private AuthorizationHold holdForPayment(UUID walletId,Payment payment) {
        return holds.findByWalletIdAndIdForUpdate(walletId,payment.getAuthorizationHoldId())
                .orElseThrow(() -> stateConflict("Payment authorization hold is missing."));
    }
    private static void requireIdempotencyKey(String key) {
        if(key==null||key.isBlank()||key.length()>128)
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Idempotency-Key must contain 1 to 128 characters.");
    }
    private static WalletException stateConflict(String message) {
        return new WalletException(WalletErrorCode.PAYMENT_STATE_CONFLICT,message);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID walletId, UUID paymentId) {
        if (!wallets.existsById(walletId))
            throw new WalletException(WalletErrorCode.WALLET_NOT_FOUND, "Wallet not found.");
        return payments.findById(paymentId).filter(payment -> payment.getWallet().getId().equals(walletId))
                .orElseThrow(() -> new WalletException(WalletErrorCode.PAYMENT_NOT_FOUND, "Payment not found."));
    }

    private void logDecline(UUID walletId,Payment payment) {
        AfterCommitAction.run(() -> logger.atInfo().addKeyValue("payment.id",payment.getId())
                .addKeyValue("wallet.id",walletId).addKeyValue("reason",payment.getDeclineReason())
                .log("Payment authorization declined"));
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
