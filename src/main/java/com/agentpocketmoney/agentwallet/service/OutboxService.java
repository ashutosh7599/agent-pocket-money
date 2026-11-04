package com.agentpocketmoney.agentwallet.service;
import com.agentpocketmoney.agentwallet.model.event.OutboxEvent;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OutboxService {
 private final OutboxEventRepository events;private final ObjectMapper json;
 public OutboxService(OutboxEventRepository events,ObjectMapper json){this.events=events;this.json=json;}
 @Transactional
 public OutboxEvent recordWalletTopUp(UUID walletId,UUID topUpId,long amount,String currency,Instant at){
  Map<String,Object> payload=Map.of("wallet_id",walletId,"top_up_id",topUpId,
      "amount_minor_units",amount,"currency",currency);
  try{return events.save(new OutboxEvent(UUID.randomUUID(),"WALLET",walletId,"wallet.topup.completed",
      json.writeValueAsString(payload),at));}
  catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize wallet top-up event.",e);}
 }
 @Transactional
 public OutboxEvent recordPayment(Payment payment,String type,Instant at){
  Map<String,Object> payload=new LinkedHashMap<>();
  payload.put("payment_id",payment.getId());payload.put("wallet_id",payment.getWallet().getId());
  payload.put("merchant_id",payment.getMerchantId());payload.put("amount_minor_units",payment.getAmountMinorUnits());
  payload.put("currency",payment.getCurrency());payload.put("status",payment.getStatus().name());
  payload.put("gateway_payment_id",payment.getGatewayPaymentId());payload.put("network_transaction_id",payment.getNetworkTransactionId());
  try{
   String serialized=json.writeValueAsString(payload);
   return events.save(new OutboxEvent(UUID.randomUUID(),"PAYMENT",payment.getId(),type,serialized,at));
  }catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize payment event.",e);}
 }
}
