package com.agentpocketmoney.agentwallet.service;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agentpocketmoney.agentwallet.audit.Audited;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerPostingLine;
import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;
import com.agentpocketmoney.agentwallet.config.AfterCommitAction;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.TopUp;
import com.agentpocketmoney.agentwallet.model.wallet.TopUpResult;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.model.wallet.WalletStatus;
import com.agentpocketmoney.agentwallet.model.wallet.WalletTransactionRecord;
import com.agentpocketmoney.agentwallet.repository.AuthorizationHoldRepository;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;
import com.agentpocketmoney.agentwallet.repository.TopUpRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import com.agentpocketmoney.agentwallet.repository.WalletTransactionRepository;

@Service
public class WalletService {
    public static final String DEFAULT_CURRENCY = "INR";
    public static final String SANDBOX_SETTLEMENT_ACCOUNT_CODE = "sandbox-settlement-inr";
    public static final UUID SYSTEM_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);
    private final WalletRepository wallets;
    private final TopUpRepository topUps;
    private final WalletTransactionRepository transactions;
    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerService ledger;
    private final AuthorizationHoldRepository holds;
    private final Clock clock;
    private final OutboxService outbox;

    public WalletService(WalletRepository wallets, TopUpRepository topUps,
                         WalletTransactionRepository transactions,
                         LedgerAccountRepository ledgerAccounts,
                         LedgerService ledger, AuthorizationHoldRepository holds, Clock clock, OutboxService outbox) {
        this.wallets = wallets;
        this.topUps = topUps;
        this.transactions = transactions;
        this.ledgerAccounts = ledgerAccounts;
        this.ledger = ledger;
        this.holds = holds;
        this.clock = clock;
        this.outbox = outbox;
    }

    @Transactional
    @Audited(action="WALLET_CREATED",entityType="wallet")
    public WalletResponseView createWallet() {
        return createWallet(SYSTEM_OWNER_ID);
    }

    @Transactional
    @Audited(action="WALLET_CREATED",entityType="wallet")
    public WalletResponseView createWallet(UUID ownerId) {
        if (ownerId == null) ownerId = SYSTEM_OWNER_ID;
        UUID walletId = UUID.randomUUID();
        LedgerAccount walletLedgerAccount = ledger.createAccount(
                "wallet-" + walletId, "Wallet " + walletId,
                AccountType.LIABILITY, DEFAULT_CURRENCY);
        Wallet wallet = wallets.save(new Wallet(walletId, ownerId, walletLedgerAccount, DEFAULT_CURRENCY));
        AfterCommitAction.run(() -> logger.atInfo()
                .addKeyValue("wallet.id", walletId)
                .addKeyValue("currency", DEFAULT_CURRENCY)
                .log("Wallet created"));
        return walletView(wallet, 0, 0);
    }

    @Transactional
    public WalletResponseView getWallet(UUID walletId) {
        Wallet wallet = lockWallet(walletId);
        Instant now = clock.instant();
        expireWalletHolds(walletId, now);
        long balance = ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId());
        long held = holds.sumActiveAmount(walletId, AuthorizationHoldStatus.ACTIVE, now);
        return walletView(wallet, balance, held);
    }

    @Transactional
    @Audited(action="HOLD_CREATED",entityType="authorization_hold")
    public AuthorizationHold createHold(UUID walletId, long amountMinorUnits, Instant expiresAt) {
        Instant now = clock.instant();
        if (amountMinorUnits <= 0 || expiresAt == null || !expiresAt.isAfter(now)) {
            throw walletError(WalletErrorCode.INVALID_WALLET_REQUEST,
                    "Hold amount must be positive and expiry must be in the future.");
        }
        Wallet wallet = lockWallet(walletId);
        ensureActive(wallet);
        expireWalletHolds(walletId, now);
        long balance = ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId());
        long held = holds.sumActiveAmount(walletId, AuthorizationHoldStatus.ACTIVE, now);
        if (amountMinorUnits > balance - held) {
            throw walletError(WalletErrorCode.INSUFFICIENT_AVAILABLE_BALANCE,
                    "Wallet does not have enough available balance.");
        }
        AuthorizationHold hold = holds.save(new AuthorizationHold(UUID.randomUUID(), wallet,
                amountMinorUnits, expiresAt, now));
        AfterCommitAction.run(() -> logger.atInfo()
                .addKeyValue("wallet.id", walletId)
                .addKeyValue("authorization_hold.id", hold.getId())
                .addKeyValue("amount.minor_units", amountMinorUnits)
                .addKeyValue("expires_at", expiresAt)
                .log("Authorization hold created"));
        return hold;
    }

    @Transactional
    public Page<AuthorizationHold> listHolds(UUID walletId, AuthorizationHoldStatus status, Pageable pageable) {
        lockWallet(walletId);
        Instant now = clock.instant();
        expireWalletHolds(walletId, now);
        return holds.findWalletHolds(walletId, status, AuthorizationHoldStatus.ACTIVE, now, pageable);
    }

    @Transactional
    @Audited(action="HOLD_RELEASED",entityType="authorization_hold")
    public AuthorizationHold releaseHold(UUID walletId, UUID holdId) {
        lockWallet(walletId);
        Instant now = clock.instant();
        expireWalletHolds(walletId, now);
        AuthorizationHold hold = holds.findByWalletIdAndIdForUpdate(walletId, holdId)
                .orElseThrow(() -> walletError(WalletErrorCode.HOLD_NOT_FOUND, "Authorization hold not found."));
        if (hold.release(now)) {
            AfterCommitAction.run(() -> logger.atInfo()
                    .addKeyValue("wallet.id", walletId)
                    .addKeyValue("authorization_hold.id", holdId)
                    .log("Authorization hold released"));
        }
        return hold;
    }

    @Transactional
    public int expireDueHolds() {
        Instant now = clock.instant();
        int expired = 0;
        for (UUID walletId : holds.findWalletIdsWithExpiredHolds(AuthorizationHoldStatus.ACTIVE, now)) {
            lockWallet(walletId);
            expired += expireWalletHolds(walletId, now);
        }
        return expired;
    }

    private int expireWalletHolds(UUID walletId, Instant now) {
        List<AuthorizationHold> due = holds.findExpiredForUpdate(walletId, AuthorizationHoldStatus.ACTIVE, now);
        int expired = 0;
        for (AuthorizationHold hold : due) if (hold.expire(now)) expired++;
        if (expired > 0) {
            int count = expired;
            AfterCommitAction.run(() -> logger.atInfo()
                    .addKeyValue("wallet.id", walletId)
                    .addKeyValue("authorization_holds.expired_count", count)
                    .log("Expired authorization holds"));
        }
        return expired;
    }

    @Transactional
    @Audited(action="WALLET_TOP_UP",entityType="top_up")
    public TopUpResult topUp(UUID walletId, String idempotencyKey, long amountMinorUnits) {
        validateTopUp(idempotencyKey, amountMinorUnits);
        Wallet wallet = wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> walletError(WalletErrorCode.WALLET_NOT_FOUND, "Wallet not found."));

        TopUp priorTopUp = topUps.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey).orElse(null);
        if (priorTopUp != null) {
            if (priorTopUp.getAmountMinorUnits() != amountMinorUnits) {
                throw walletError(WalletErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency key was already used for a different request.");
            }
            return TopUpResult.from(priorTopUp);
        }
        ensureActive(wallet);

        LedgerAccount settlementAccount = ledgerAccounts.findByCode(SANDBOX_SETTLEMENT_ACCOUNT_CODE)
                .orElseThrow(() -> new IllegalStateException("Sandbox settlement account is not initialized."));
        UUID topUpId = UUID.randomUUID();
        String transactionReference = "topup-" + topUpId;
        JournalEntry entry = ledger.post(transactionReference, "Sandbox wallet top-up", List.of(
                new LedgerPostingLine(settlementAccount.getId(), PostingSide.DEBIT, amountMinorUnits),
                new LedgerPostingLine(wallet.getLedgerAccount().getId(), PostingSide.CREDIT, amountMinorUnits)));
        TopUp topUp = topUps.save(new TopUp(topUpId, wallet, entry, idempotencyKey,
                amountMinorUnits, wallet.getCurrency()));
        outbox.recordWalletTopUp(walletId,topUpId,amountMinorUnits,wallet.getCurrency(),topUp.getCreatedAt());

        AfterCommitAction.run(() -> logger.atInfo()
                .addKeyValue("wallet.id", walletId)
                .addKeyValue("wallet.top_up.id", topUpId)
                .addKeyValue("ledger.entry.id", entry.getId())
                .addKeyValue("amount.minor_units", amountMinorUnits)
                .addKeyValue("currency", wallet.getCurrency())
                .log("Wallet funded"));
        return TopUpResult.from(topUp);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionRecord> transactions(UUID walletId, Pageable pageable) {
        Wallet wallet = findWallet(walletId);
        return transactions.findWalletTransactions(wallet.getLedgerAccount().getId(), pageable);
    }

    private Wallet lockWallet(UUID walletId) {
        return wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> walletError(WalletErrorCode.WALLET_NOT_FOUND, "Wallet not found."));
    }

    private Wallet findWallet(UUID walletId) {
        return wallets.findById(walletId)
                .orElseThrow(() -> walletError(WalletErrorCode.WALLET_NOT_FOUND, "Wallet not found."));
    }

    private static void ensureActive(Wallet wallet) {
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw walletError(WalletErrorCode.WALLET_FROZEN, "Wallet is frozen.");
        }
        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw walletError(WalletErrorCode.WALLET_CLOSED, "Wallet is closed.");
        }
    }

    private static void validateTopUp(String idempotencyKey, long amountMinorUnits) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw walletError(WalletErrorCode.INVALID_WALLET_REQUEST,
                    "Idempotency-Key must contain between 1 and 128 characters.");
        }
        if (amountMinorUnits <= 0) {
            throw walletError(WalletErrorCode.INVALID_WALLET_REQUEST,
                    "Top-up amount must be positive minor units.");
        }
    }

    private static WalletResponseView walletView(Wallet wallet, long balanceMinorUnits, long heldBalanceMinorUnits) {
        return new WalletResponseView(wallet.getId(), wallet.getStatus(), wallet.getCurrency(),
                balanceMinorUnits, heldBalanceMinorUnits,
                balanceMinorUnits - heldBalanceMinorUnits, wallet.getCreatedAt());
    }

    private static WalletException walletError(WalletErrorCode code, String message) {
        return new WalletException(code, message);
    }

    public record WalletResponseView(UUID id, WalletStatus status, String currency,
                                     long balanceMinorUnits, long heldBalanceMinorUnits,
                                     long availableBalanceMinorUnits, java.time.Instant createdAt) {}
}
