package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.audit.Audited;
import com.agentpocketmoney.agentwallet.dto.CreateSpendMandateRequest;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.agent.SpendMandate;
import com.agentpocketmoney.agentwallet.model.wallet.GatewayScenario;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.Wallet;
import com.agentpocketmoney.agentwallet.repository.SpendMandateRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentPaymentService {
    private final SpendMandateRepository mandates;private final WalletRepository wallets;
    private final PaymentService payments;private final Clock clock;
    public AgentPaymentService(SpendMandateRepository mandates,WalletRepository wallets,PaymentService payments,Clock clock){
        this.mandates=mandates;this.wallets=wallets;this.payments=payments;this.clock=clock;
    }
    @Transactional
    @Audited(action="SPEND_MANDATE_CREATED",entityType="spend_mandate")
    public SpendMandate create(UUID walletId,CreateSpendMandateRequest request){
        Wallet wallet=wallets.findByIdForUpdate(walletId).orElseThrow(()->new WalletException(WalletErrorCode.WALLET_NOT_FOUND,"Wallet not found."));
        Instant now=clock.instant();
        if(request.maxAmountMinorUnits()<=0||!request.expiresAt().isAfter(now)||!wallet.getCurrency().equals(request.currency()))
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Mandate amount, currency, or expiry is invalid.");
        return mandates.save(new SpendMandate(UUID.randomUUID(),wallet,request.purpose(),request.merchantId(),
                request.maxAmountMinorUnits(),request.currency(),request.category(),request.expiresAt(),now));
    }
    @Transactional
    public Payment requestPayment(UUID walletId,UUID mandateId,String idempotencyKey,long amountMinorUnits){
        SpendMandate mandate=mandates.findForUpdate(walletId,mandateId).orElseThrow(()->
                new WalletException(WalletErrorCode.MANDATE_NOT_FOUND,"Spend mandate not found."));
        Instant now=clock.instant();
        if(!mandate.getExpiresAt().isAfter(now))throw new WalletException(WalletErrorCode.MANDATE_EXPIRED,"Spend mandate has expired.");
        if(amountMinorUnits<=0||amountMinorUnits>mandate.getMaxAmountMinorUnits())
            throw new WalletException(WalletErrorCode.MANDATE_LIMIT_EXCEEDED,"Payment amount exceeds the spend mandate.");
        if(!mandate.getCurrency().equals(mandate.getWallet().getCurrency()))
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Spend mandate currency does not match wallet currency.");
        if(mandate.getStatus()!=com.agentpocketmoney.agentwallet.model.agent.SpendMandateStatus.ACTIVE){
            Payment previous=payments.get(walletId,mandate.getConsumedPaymentId());
            if(previous.getIdempotencyKey().equals(idempotencyKey)&&previous.getAmountMinorUnits()==amountMinorUnits)return previous;
            throw new WalletException(WalletErrorCode.MANDATE_CONSUMED,"Spend mandate has already been used.");
        }
        Payment payment=payments.create(walletId,idempotencyKey,mandate.getMerchantId(),amountMinorUnits,
                mandate.getCategory(),mandate.getPurpose(),GatewayScenario.APPROVE);
        if(payment.getStatus()==com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus.CREATED)
            payment=payments.authorize(walletId,payment.getId());
        mandate.consume(payment.getId());
        return payment;
    }
}
