package com.agentpocketmoney.agentwallet.controller;

import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agentpocketmoney.agentwallet.dto.AuthorizationHoldPage;
import com.agentpocketmoney.agentwallet.dto.AuthorizationHoldResponse;
import com.agentpocketmoney.agentwallet.dto.CreateAuthorizationHoldRequest;
import com.agentpocketmoney.agentwallet.dto.CreatePaymentRequest;
import com.agentpocketmoney.agentwallet.dto.PaymentResponse;
import com.agentpocketmoney.agentwallet.dto.TopUpRequest;
import com.agentpocketmoney.agentwallet.dto.TopUpResponse;
import com.agentpocketmoney.agentwallet.dto.WalletResponse;
import com.agentpocketmoney.agentwallet.dto.WalletTransactionPage;
import com.agentpocketmoney.agentwallet.dto.WalletTransactionResponse;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHold;
import com.agentpocketmoney.agentwallet.model.wallet.AuthorizationHoldStatus;
import com.agentpocketmoney.agentwallet.model.wallet.TopUpResult;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.service.PaymentService;
import com.agentpocketmoney.agentwallet.service.WalletService;
import com.agentpocketmoney.agentwallet.model.wallet.WalletTransactionRecord;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletService wallets;
    private final PaymentService payments;

    public WalletController(WalletService wallets, PaymentService payments) {
        this.wallets = wallets;
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet() {
        WalletService.WalletResponseView wallet = wallets.createWallet();
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + wallet.id()))
                .body(toWalletResponse(wallet));
    }

    @GetMapping("/{walletId}")
    public WalletResponse getWallet(@PathVariable UUID walletId) {
        return toWalletResponse(wallets.getWallet(walletId));
    }

    @PostMapping("/{walletId}/holds")
    public ResponseEntity<AuthorizationHoldResponse> createHold(@PathVariable UUID walletId,
            @Valid @RequestBody CreateAuthorizationHoldRequest request) {
        AuthorizationHold hold = wallets.createHold(walletId, request.amountMinorUnits(), request.expiresAt());
        URI location = URI.create("/api/v1/wallets/" + walletId + "/holds/" + hold.getId());
        return ResponseEntity.created(location).body(AuthorizationHoldResponse.from(hold));
    }

    @GetMapping("/{walletId}/holds")
    public AuthorizationHoldPage listHolds(@PathVariable UUID walletId,
            @RequestParam(required = false, defaultValue = "ACTIVE") AuthorizationHoldStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        Page<AuthorizationHold> result = wallets.listHolds(walletId, status, PageRequest.of(page, size));
        return new AuthorizationHoldPage(result.getContent().stream()
                .map(AuthorizationHoldResponse::from).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/{walletId}/holds/{holdId}/release")
    public AuthorizationHoldResponse releaseHold(@PathVariable UUID walletId, @PathVariable UUID holdId) {
        return AuthorizationHoldResponse.from(wallets.releaseHold(walletId, holdId));
    }

    @PostMapping("/{walletId}/payments")
    public ResponseEntity<PaymentResponse> createPayment(@PathVariable UUID walletId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        var payment = payments.create(walletId, idempotencyKey, request.merchantId(),
                request.amountMinorUnits(), request.category(), request.purpose());
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + walletId + "/payments/" + payment.getId()))
                .body(PaymentResponse.from(payment));
    }

    @PostMapping("/{walletId}/payments/{paymentId}/authorize")
    public PaymentResponse authorizePayment(@PathVariable UUID walletId, @PathVariable UUID paymentId) {
        return PaymentResponse.from(payments.authorize(walletId, paymentId));
    }

    @GetMapping("/{walletId}/payments/{paymentId}")
    public PaymentResponse getPayment(@PathVariable UUID walletId, @PathVariable UUID paymentId) {
        return PaymentResponse.from(payments.get(walletId, paymentId));
    }

    @PostMapping("/{walletId}/top-ups")
    public TopUpResponse topUp(@PathVariable UUID walletId,
                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                               @Valid @RequestBody TopUpRequest request) {
        TopUpResult result = wallets.topUp(walletId, idempotencyKey, request.amountMinorUnits());
        return new TopUpResponse(result.id(), result.walletId(), result.journalEntryId(),
                result.transactionReference(), result.amountMinorUnits(), result.currency(), result.createdAt());
    }

    @GetMapping("/{walletId}/transactions")
    public WalletTransactionPage transactions(@PathVariable UUID walletId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        Page<WalletTransactionRecord> result = wallets.transactions(walletId, PageRequest.of(page, size));
        return new WalletTransactionPage(result.getContent().stream().map(WalletController::toTransaction).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static WalletResponse toWalletResponse(WalletService.WalletResponseView wallet) {
        return new WalletResponse(wallet.id(), wallet.status(), wallet.currency(),
                wallet.balanceMinorUnits(), wallet.heldBalanceMinorUnits(),
                wallet.availableBalanceMinorUnits(), wallet.createdAt());
    }

    private static WalletTransactionResponse toTransaction(WalletTransactionRecord transaction) {
        return new WalletTransactionResponse(transaction.journalEntryId(), transaction.transactionReference(),
                transaction.description(), transaction.side(), transaction.amountMinorUnits(),
                transaction.currency(), transaction.postedAt());
    }
}
