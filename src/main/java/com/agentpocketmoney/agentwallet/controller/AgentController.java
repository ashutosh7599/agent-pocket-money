package com.agentpocketmoney.agentwallet.controller;

import com.agentpocketmoney.agentwallet.dto.*;
import com.agentpocketmoney.agentwallet.service.AgentPaymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}/mandates")
public class AgentController {
    private final AgentPaymentService agents;
    public AgentController(AgentPaymentService agents){this.agents=agents;}
    @PostMapping
    public ResponseEntity<SpendMandateResponse> create(@PathVariable UUID walletId,@Valid @RequestBody CreateSpendMandateRequest request){
        var mandate=agents.create(walletId,request);
        return ResponseEntity.created(URI.create("/api/v1/wallets/"+walletId+"/mandates/"+mandate.getId()))
                .body(SpendMandateResponse.from(mandate));
    }
    @PostMapping("/{mandateId}/payments")
    public PaymentResponse requestPayment(@PathVariable UUID walletId,@PathVariable UUID mandateId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,@Valid @RequestBody AgentPaymentRequest request){
        return PaymentResponse.from(agents.requestPayment(walletId,mandateId,idempotencyKey,request.amountMinorUnits()));
    }
}
