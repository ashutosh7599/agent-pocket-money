package com.agentpocketmoney.agentwallet.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerPostingLine;
import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;
import com.agentpocketmoney.agentwallet.config.AfterCommitAction;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.wallet.TopUp;
import com.agentpocketmoney.agentwallet.model.wallet.TopUpResult;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.model.wallet.WalletStatus;
import com.agentpocketmoney.agentwallet.model.wallet.WalletTransactionRecord;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;
import com.agentpocketmoney.agentwallet.repository.TopUpRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import com.agentpocketmoney.agentwallet.repository.WalletTransactionRepository;

@Service
public class WalletService {
    public static final String DEFAULT_CURRENCY = "INR";
    public static final String SANDBOX_SETTLEMENT_ACCOUNT_CODE = "sandbox-settlement-inr";

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);
    private final WalletRepository wallets;
    private final TopUpRepository topUps;
    private final WalletTransactionRepository transactions;
    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerService ledger;

    public WalletService(WalletRepository wallets, TopUpRepository topUps,
                         WalletTransactionRepository transactions,
                         LedgerAccountRepository ledgerAccounts,
                         LedgerService ledger) {
        this.wallets = wallets;
        this.topUps = topUps;
        this.transactions = transactions;
        this.ledgerAccounts = ledgerAccounts;
        this.ledger = ledger;
    }

    @Transactional
    public WalletResponseView createWallet() {
        UUID walletId = UUID.randomUUID();
        LedgerAccount walletLedgerAccount = ledger.createAccount(
                "wallet-" + walletId, "Wallet " + walletId,
                AccountType.LIABILITY, DEFAULT_CURRENCY);
        Wallet wallet = wallets.save(new Wallet(walletId, walletLedgerAccount, DEFAULT_CURRENCY));
        AfterCommitAction.run(() -> logger.atInfo()
                .addKeyValue("wallet.id", walletId)
                .addKeyValue("currency", DEFAULT_CURRENCY)
                .log("Wallet created"));
        return walletView(wallet, 0);
    }

    @Transactional(readOnly = true)
    public WalletResponseView getWallet(UUID walletId) {
        Wallet wallet = findWallet(walletId);
        long balance = ledger.accountBalanceMinorUnits(wallet.getLedgerAccount().getId());
        return walletView(wallet, balance);
    }

    @Transactional
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

    private static WalletResponseView walletView(Wallet wallet, long balanceMinorUnits) {
        return new WalletResponseView(wallet.getId(), wallet.getStatus(), wallet.getCurrency(),
                balanceMinorUnits, wallet.getCreatedAt());
    }

    private static WalletException walletError(WalletErrorCode code, String message) {
        return new WalletException(code, message);
    }

    public record WalletResponseView(UUID id, WalletStatus status, String currency,
                                     long balanceMinorUnits, java.time.Instant createdAt) {}
}
