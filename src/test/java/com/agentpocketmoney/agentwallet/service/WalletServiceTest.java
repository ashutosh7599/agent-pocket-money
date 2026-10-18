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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.JournalEntry;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.TopUp;
import com.agentpocketmoney.agentwallet.model.wallet.TopUpResult;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.repository.AuthorizationHoldRepository;
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
    @Mock AuthorizationHoldRepository holds;
    @Mock OutboxService outbox;

    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");

    private WalletService service;
    private Wallet wallet;
    private LedgerAccount walletAccount;
    private LedgerAccount settlementAccount;

    @BeforeEach
    void setUp() {
        service = new WalletService(wallets, topUps, transactions, ledgerAccounts, ledger, holds,
                Clock.fixed(now, ZoneOffset.UTC), outbox);
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

    @Test
    void activeHoldsReduceAvailableBalance() {
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(walletAccount.getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any())).thenReturn(25000L);

        WalletService.WalletResponseView view = service.getWallet(wallet.getId());

        assertThat(view.balanceMinorUnits()).isEqualTo(65000);
        assertThat(view.heldBalanceMinorUnits()).isEqualTo(25000);
        assertThat(view.availableBalanceMinorUnits()).isEqualTo(40000);
    }

    @Test
    void createsHoldWhenAvailableBalanceIsSufficient() {
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(walletAccount.getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any())).thenReturn(25000L);
        when(holds.save(any(AuthorizationHold.class))).thenAnswer(call -> call.getArgument(0));

        AuthorizationHold hold = service.createHold(wallet.getId(), 20000, now.plusSeconds(600));

        assertThat(hold.getStatus()).isEqualTo(AuthorizationHoldStatus.ACTIVE);
        assertThat(hold.getAmountMinorUnits()).isEqualTo(20000);
        verify(holds).save(any(AuthorizationHold.class));
    }

    @Test
    void rejectsHoldThatExceedsAvailableBalance() {
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(ledger.accountBalanceMinorUnits(walletAccount.getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any())).thenReturn(25000L);

        assertThatThrownBy(() -> service.createHold(wallet.getId(), 40001, now.plusSeconds(600)))
                .isInstanceOf(WalletException.class)
                .extracting(exception -> ((WalletException) exception).getErrorCode())
                .isEqualTo(WalletErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        verify(holds, never()).save(any(AuthorizationHold.class));
    }

    @Test
    void releaseRestoresAvailableBalanceAndIsIdempotent() {
        AuthorizationHold hold = new AuthorizationHold(UUID.randomUUID(), wallet, 10000, now.plusSeconds(600), now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(eq(wallet.getId()), eq(AuthorizationHoldStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(holds.findByWalletIdAndIdForUpdate(wallet.getId(), hold.getId())).thenReturn(Optional.of(hold));

        assertThat(service.releaseHold(wallet.getId(), hold.getId()).getStatus())
                .isEqualTo(AuthorizationHoldStatus.RELEASED);
        assertThat(service.releaseHold(wallet.getId(), hold.getId()).getStatus())
                .isEqualTo(AuthorizationHoldStatus.RELEASED);
    }

    @Test
    void expiryMarksDueHoldsAndRestoresAvailableFunds() {
        AuthorizationHold hold = new AuthorizationHold(UUID.randomUUID(), wallet, 10000, now, now.minusSeconds(60));
        when(holds.findWalletIdsWithExpiredHolds(AuthorizationHoldStatus.ACTIVE, now)).thenReturn(List.of(wallet.getId()), List.of());
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(wallet.getId(), AuthorizationHoldStatus.ACTIVE, now)).thenReturn(List.of(hold));

        assertThat(service.expireDueHolds()).isEqualTo(1);
        assertThat(hold.getStatus()).isEqualTo(AuthorizationHoldStatus.EXPIRED);
        assertThat(service.expireDueHolds()).isZero();
    }
    @Test
    void expiredHoldIsRemovedFromAvailableBalance() {
        AuthorizationHold expired = new AuthorizationHold(UUID.randomUUID(), wallet, 10000, now, now.minusSeconds(60));
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(wallet.getId(), AuthorizationHoldStatus.ACTIVE, now))
                .thenReturn(List.of(expired));
        when(ledger.accountBalanceMinorUnits(walletAccount.getId())).thenReturn(65000L);
        when(holds.sumActiveAmount(wallet.getId(), AuthorizationHoldStatus.ACTIVE, now)).thenReturn(0L);

        WalletService.WalletResponseView view = service.getWallet(wallet.getId());

        assertThat(expired.getStatus()).isEqualTo(AuthorizationHoldStatus.EXPIRED);
        assertThat(view.heldBalanceMinorUnits()).isZero();
        assertThat(view.availableBalanceMinorUnits()).isEqualTo(65000);
    }

    @Test
    void listsWalletHoldsForRelease() {
        AuthorizationHold hold = new AuthorizationHold(UUID.randomUUID(), wallet, 12000, now.plusSeconds(600), now);
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(holds.findExpiredForUpdate(wallet.getId(), AuthorizationHoldStatus.ACTIVE, now)).thenReturn(List.of());
        when(holds.findWalletHolds(wallet.getId(), AuthorizationHoldStatus.ACTIVE,
                AuthorizationHoldStatus.ACTIVE, now, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(hold)));

        var result = service.listHolds(wallet.getId(), AuthorizationHoldStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(hold);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

}
