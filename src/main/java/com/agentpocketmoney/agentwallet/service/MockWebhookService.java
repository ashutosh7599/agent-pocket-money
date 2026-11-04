package com.agentpocketmoney.agentwallet.service;
import com.agentpocketmoney.agentwallet.dto.MockGatewayWebhookRequest;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.mock.webhook.HmacSignatureVerifier;
import com.agentpocketmoney.agentwallet.model.event.ReceivedWebhook;
import com.agentpocketmoney.agentwallet.repository.ReceivedWebhookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class MockWebhookService {
 private final ReceivedWebhookRepository webhooks;private final HmacSignatureVerifier signatures;
 private final ObjectMapper json;private final Clock clock;
 public MockWebhookService(ReceivedWebhookRepository webhooks,HmacSignatureVerifier signatures,ObjectMapper json,Clock clock){
  this.webhooks=webhooks;this.signatures=signatures;this.json=json;this.clock=clock;
 }
 @Transactional
 public Result receive(String rawBody,String signature){
  MockGatewayWebhookRequest payload;
  try{payload=json.readValue(rawBody,MockGatewayWebhookRequest.class);}
  catch(JsonProcessingException e){throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Webhook payload is invalid.");}
  if(payload.eventId()==null||payload.eventType()==null||payload.eventType().isBlank()
      ||payload.paymentId()==null||payload.status()==null||payload.occurredAt()==null)
   throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Webhook payload is incomplete.");
  boolean valid=signatures.verify(rawBody,signature);
  if(!valid)return new Result(Outcome.INVALID_SIGNATURE,payload.eventId());
  if(webhooks.findByEventId(payload.eventId()).isPresent())return new Result(Outcome.DUPLICATE,payload.eventId());
  webhooks.save(new ReceivedWebhook(payload.eventId(),payload.eventType(),valid,rawBody,clock.instant()));
  return new Result(valid?Outcome.ACCEPTED:Outcome.INVALID_SIGNATURE,payload.eventId());
 }
 public enum Outcome{ACCEPTED,DUPLICATE,INVALID_SIGNATURE}
 public record Result(Outcome outcome,UUID eventId){}
}
