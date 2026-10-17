package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.agentpocketmoney.agentwallet.model.wallet.TopUp;
import com.agentpocketmoney.agentwallet.model.wallet.TopUpResult;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.repository.LedgerAccountRepository;
import com.agentpocketmoney.agentwallet.repository.TopUpRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import com.agentpocketmoney.agentwallet.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {
    @Mock WalletRepository wallets;
    @Mock TopUpRepository topUps;
    @Mock WalletTransactionRepository transactions;
    @Mock LedgerAccountRepository ledgerAccounts;
    @Mock LedgerService ledger;

    private WalletService service;
    private Wallet wallet;
    private LedgerAccount walletAccount;
    private LedgerAccount settlementAccount;

    @BeforeEach
    void setUp() {
        service = new WalletService(wallets, topUps, transactions, ledgerAccounts, ledger);
        walletAccount = new LedgerAccount("wallet-test", "Test wallet", AccountType.LIABILITY, "INR");
        settlementAccount = new LedgerAccount("sandbox-settlement-inr", "Settlement", AccountType.ASSET, "INR");
        wallet = new Wallet(UUID.randomUUID(), walletAccount, "INR");
    }

    @Test
    void postsTopUpToLedgerAndStoresItsIdempotencyKey() {
        UUID entryId = UUID.randomUUID();
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(topUps.findByWalletIdAndIdempotencyKey(wallet.getId(), "request-1")).thenReturn(Optional.empty());
        when(ledgerAccounts.findByCode(WalletService.SANDBOX_SETTLEMENT_ACCOUNT_CODE))
                .thenReturn(Optional.of(settlementAccount));
        when(ledger.post(anyString(), eq("Sandbox wallet top-up"), anyList()))
                .thenAnswer(call -> new JournalEntry((String) call.getArgument(0), "Sandbox wallet top-up") {
                    @Override public UUID getId() { return entryId; }
                });
        when(topUps.save(any(TopUp.class))).thenAnswer(call -> call.getArgument(0));

        TopUpResult result = service.topUp(wallet.getId(), "request-1", 65000);

        assertThat(result.walletId()).isEqualTo(wallet.getId());
        assertThat(result.amountMinorUnits()).isEqualTo(65000);
        assertThat(result.currency()).isEqualTo("INR");
        verify(ledger).post(anyString(), eq("Sandbox wallet top-up"), anyList());
        verify(topUps).save(any(TopUp.class));
    }

    @Test
    void repeatedKeyWithSameAmountReturnsOriginalTopUpWithoutPostingAgain() {
        JournalEntry entry = new JournalEntry("topup-original", "Sandbox wallet top-up");
        TopUp original = new TopUp(UUID.randomUUID(), wallet, entry, "request-2", 1200, "INR");
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(topUps.findByWalletIdAndIdempotencyKey(wallet.getId(), "request-2"))
                .thenReturn(Optional.of(original));

        TopUpResult retry = service.topUp(wallet.getId(), "request-2", 1200);

        assertThat(retry.id()).isEqualTo(original.getId());
        assertThat(retry.journalEntryId()).isEqualTo(entry.getId());
        assertThat(retry).isEqualTo(TopUpResult.from(original));
        verify(ledger, never()).post(anyString(), anyString(), anyList());
        verify(topUps, never()).save(any(TopUp.class));
    }

    @Test
    void rejectsReusingIdempotencyKeyWithDifferentAmount() {
        TopUp original = new TopUp(UUID.randomUUID(), wallet,
                new JournalEntry("topup-original", "Sandbox wallet top-up"), "request-3", 1200, "INR");
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(topUps.findByWalletIdAndIdempotencyKey(wallet.getId(), "request-3"))
                .thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.topUp(wallet.getId(), "request-3", 1300))
                .isInstanceOf(WalletException.class)
                .extracting(exception -> ((WalletException) exception).getErrorCode())
                .isEqualTo(WalletErrorCode.IDEMPOTENCY_KEY_REUSED);
        verify(ledger, never()).post(anyString(), anyString(), anyList());
    }

    @Test
    void rejectsNonPositiveTopUpBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.topUp(wallet.getId(), "request-4", 0))
                .isInstanceOf(WalletException.class)
                .hasMessageContaining("positive");
        verify(wallets, never()).findByIdForUpdate(any());
    }
}
