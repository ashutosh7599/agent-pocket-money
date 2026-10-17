package com.agentpocketmoney.agentwallet.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentpocketmoney.agentwallet.config.AfterCommitAction;
import com.agentpocketmoney.agentwallet.exception.LedgerErrorCode;
import com.agentpocketmoney.agentwallet.exception.LedgerException;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import com.agentpocketmoney.agentwallet.model.ledger.JournalLine;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerPostingLine;
import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;
import com.agentpocketmoney.agentwallet.repository.JournalEntryRepository;
import com.agentpocketmoney.agentwallet.repository.JournalLineRepository;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;

@Service
public class LedgerService {
    private static final Logger logger = LoggerFactory.getLogger(LedgerService.class);
    private final LedgerAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;

    public LedgerService(LedgerAccountRepository accounts, JournalEntryRepository entries,
                         JournalLineRepository lines) {
        this.accounts = accounts;
        this.entries = entries;
        this.lines = lines;
    }

    @Transactional
    public LedgerAccount createAccount(String code, String name, AccountType type, String currency) {
        requireText(code, "Account code");
        requireText(name, "Account name");
        requireCurrency(currency);
        if (type == null) throw invalid("Account type is required");
        LedgerAccount account = accounts.save(new LedgerAccount(code, name, type, currency));
        AfterCommitAction.run(() -> logger.atInfo()
                .addKeyValue("ledger.account.id", account.getId())
                .addKeyValue("ledger.account.code", account.getCode())
                .addKeyValue("ledger.account.type", account.getAccountType())
                .addKeyValue("currency", account.getCurrency())
                .log("Ledger account created"));
        return account;
    }

    @Transactional
    public JournalEntry post(String transactionReference, String description,
                             List<LedgerPostingLine> postingLines) {
        requireText(transactionReference, "Transaction reference");
        requireText(description, "Description");
        if (postingLines == null || postingLines.size() < 2) {
            throw invalid("A journal entry requires at least two lines");
        }

        long debits = 0;
        long credits = 0;
        String currency = null;
        for (LedgerPostingLine line : postingLines) {
            if (line == null || line.accountId() == null || line.side() == null) {
                throw invalid("Each journal line must specify an account and side");
            }
            if (line.amountMinorUnits() <= 0) {
                throw invalid("Journal line amount must be positive minor units");
            }
            LedgerAccount account = accounts.findById(line.accountId())
                    .orElseThrow(() -> new LedgerException(LedgerErrorCode.LEDGER_ACCOUNT_NOT_FOUND,
                            "Ledger account not found."));
            if (currency == null) currency = account.getCurrency();
            if (!currency.equals(account.getCurrency())) {
                throw invalid("All journal lines must use the same currency");
            }
            try {
                if (line.side() == PostingSide.DEBIT) debits = Math.addExact(debits, line.amountMinorUnits());
                else credits = Math.addExact(credits, line.amountMinorUnits());
            } catch (ArithmeticException overflow) {
                throw new LedgerException(LedgerErrorCode.INVALID_LEDGER_REQUEST,
                        "Journal entry total exceeds the supported amount.", overflow);
            }
        }
        if (debits != credits) {
            throw invalid("Journal entry is unbalanced.");
        }

        String entryCurrency = currency;
        JournalEntry entry = entries.save(new JournalEntry(transactionReference, description));
        List<JournalLine> journalLines = postingLines.stream().map(line -> {
            LedgerAccount account = accounts.getReferenceById(line.accountId());
            return new JournalLine(entry, account, line.side(), line.amountMinorUnits(), entryCurrency);
        }).toList();
        lines.saveAll(journalLines);
        long postedAmountMinorUnits = debits;
        AfterCommitAction.run(() -> logger.atInfo()
                .addKeyValue("ledger.entry.id", entry.getId())
                .addKeyValue("transaction.reference", transactionReference)
                .addKeyValue("ledger.line.count", journalLines.size())
                .addKeyValue("amount.minor_units", postedAmountMinorUnits)
                .addKeyValue("currency", entryCurrency)
                .log("Journal entry posted"));
        return entry;
    }

    @Transactional(readOnly = true)
    public long accountBalanceMinorUnits(UUID accountId) {
        LedgerAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new LedgerException(LedgerErrorCode.LEDGER_ACCOUNT_NOT_FOUND,
                        "Ledger account not found."));
        BigDecimal signedDebitBalance = lines.signedBalance(accountId);
        BigDecimal naturalBalance = account.getAccountType().normalBalanceSide() == PostingSide.DEBIT
                ? signedDebitBalance : signedDebitBalance.negate();
        try {
            return naturalBalance.longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("Account balance exceeds the supported amount", overflow);
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw invalid(label + " is required.");
    }

    private static void requireCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw invalid("Currency must be a three-letter uppercase code.");
        }
    }

    private static LedgerException invalid(String message) {
        return new LedgerException(LedgerErrorCode.INVALID_LEDGER_REQUEST, message);
    }
}
