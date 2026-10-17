package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.exception.LedgerException;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerPostingLine;
import com.agentpocketmoney.agentwallet.model.ledger.PostingSide;
import com.agentpocketmoney.agentwallet.repository.JournalEntryRepository;
import com.agentpocketmoney.agentwallet.repository.JournalLineRepository;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock LedgerAccountRepository accounts;
    @Mock JournalEntryRepository entries;
    @Mock JournalLineRepository lines;
    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerService(accounts, entries, lines);
    }

    @Test
    void postsBalancedEntryWithBusinessReference() {
        LedgerAccount settlement = account("settlement", AccountType.ASSET);
        LedgerAccount wallet = account("wallet", AccountType.LIABILITY);
        when(accounts.findById(settlement.getId())).thenReturn(Optional.of(settlement));
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(entries.save(any(JournalEntry.class))).thenAnswer(call -> call.getArgument(0));

        JournalEntry entry = ledger.post("topup-123", "Sandbox top-up", List.of(
                new LedgerPostingLine(settlement.getId(), PostingSide.DEBIT, 50000),
                new LedgerPostingLine(wallet.getId(), PostingSide.CREDIT, 50000)));

        assertThat(entry.getTransactionReference()).isEqualTo("topup-123");
        verify(lines).saveAll(any());
    }

    @Test
    void rejectsUnbalancedEntryBeforePersistingAnything() {
        LedgerAccount debit = account("debit", AccountType.ASSET);
        LedgerAccount credit = account("credit", AccountType.LIABILITY);
        when(accounts.findById(debit.getId())).thenReturn(Optional.of(debit));
        when(accounts.findById(credit.getId())).thenReturn(Optional.of(credit));

        assertThatThrownBy(() -> ledger.post("payment-1", "Invalid", List.of(
                new LedgerPostingLine(debit.getId(), PostingSide.DEBIT, 1000),
                new LedgerPostingLine(credit.getId(), PostingSide.CREDIT, 999))))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("unbalanced");

        verify(entries, never()).save(any());
        verify(lines, never()).saveAll(any());
    }

    @Test
    void calculatesLiabilityBalanceUsingItsNormalCreditSide() {
        LedgerAccount wallet = account("wallet", AccountType.LIABILITY);
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(lines.signedBalance(wallet.getId())).thenReturn(new BigDecimal("-4200"));

        assertThat(ledger.accountBalanceMinorUnits(wallet.getId())).isEqualTo(4200);
    }

    @Test
    void rejectsMixedCurrenciesAndNonPositiveAmounts() {
        LedgerAccount inr = account("inr", AccountType.ASSET, "INR");
        LedgerAccount usd = account("usd", AccountType.LIABILITY, "USD");
        when(accounts.findById(inr.getId())).thenReturn(Optional.of(inr));
        when(accounts.findById(usd.getId())).thenReturn(Optional.of(usd));

        assertThatThrownBy(() -> ledger.post("mixed", "Mixed currencies", List.of(
                new LedgerPostingLine(inr.getId(), PostingSide.DEBIT, 100),
                new LedgerPostingLine(usd.getId(), PostingSide.CREDIT, 100))))
                .isInstanceOf(LedgerException.class).hasMessageContaining("same currency");
        assertThatThrownBy(() -> ledger.post("zero", "Zero amount", List.of(
                new LedgerPostingLine(inr.getId(), PostingSide.DEBIT, 0),
                new LedgerPostingLine(inr.getId(), PostingSide.CREDIT, 1))))
                .isInstanceOf(LedgerException.class).hasMessageContaining("positive");
    }

    private static LedgerAccount account(String code, AccountType type) {
        return account(code, type, "INR");
    }

    private static LedgerAccount account(String code, AccountType type, String currency) {
        return new LedgerAccount(code, code, type, currency);
    }
}
