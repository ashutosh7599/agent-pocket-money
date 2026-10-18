package com.agentpocketmoney.agentwallet.controller;
import com.agentpocketmoney.agentwallet.dto.WebhookResponse;
import com.agentpocketmoney.agentwallet.service.MockWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/webhooks/mock-gateway")
public class MockWebhookController {
 private final MockWebhookService webhooks;
 public MockWebhookController(MockWebhookService webhooks){this.webhooks=webhooks;}
 @PostMapping
 public ResponseEntity<WebhookResponse> receive(@RequestHeader(value="X-Webhook-Signature",required=false) String signature,
                                                @RequestBody String rawBody){
  var result=webhooks.receive(rawBody,signature);
  return switch(result.outcome()){
   case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(new WebhookResponse("ACCEPTED",result.eventId()));
   case DUPLICATE -> ResponseEntity.ok(new WebhookResponse("DUPLICATE",result.eventId()));
   case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new WebhookResponse("INVALID_SIGNATURE",result.eventId()));
  };
 }
}
