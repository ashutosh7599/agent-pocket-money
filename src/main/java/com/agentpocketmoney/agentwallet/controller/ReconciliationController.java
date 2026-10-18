package com.agentpocketmoney.agentwallet.controller;

import com.agentpocketmoney.agentwallet.dto.ReconciliationBatchResponse;
import com.agentpocketmoney.agentwallet.dto.SettlementReportRow;
import com.agentpocketmoney.agentwallet.model.reconciliation.MockReportScenario;
import com.agentpocketmoney.agentwallet.repository.ReconciliationBatchRepository;
import com.agentpocketmoney.agentwallet.service.ReconciliationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {
    private final ReconciliationService reconciliation;
    private final ReconciliationBatchRepository batches;
    public ReconciliationController(ReconciliationService reconciliation,ReconciliationBatchRepository batches){
        this.reconciliation=reconciliation; this.batches=batches;
    }
    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconciliationBatchResponse reconcile(@RequestBody List<SettlementReportRow> report){
        return ReconciliationBatchResponse.from(reconciliation.reconcile(report));
    }
    @PostMapping("/mock-batches")
    @ResponseStatus(HttpStatus.CREATED)
    public ReconciliationBatchResponse generate(@RequestParam(defaultValue="MATCH") MockReportScenario scenario){
        return ReconciliationBatchResponse.from(reconciliation.generateMockReport(scenario));
    }
    @GetMapping("/batches/{batchId}")
    public ReconciliationBatchResponse getBatch(@PathVariable UUID batchId){
        return batches.findById(batchId).map(ReconciliationBatchResponse::from)
                .orElseThrow(() -> new com.agentpocketmoney.agentwallet.exception.WalletException(
                        com.agentpocketmoney.agentwallet.exception.WalletErrorCode.RECONCILIATION_BATCH_NOT_FOUND,
                        "Reconciliation batch was not found."));
    }
    @GetMapping("/batches/{batchId}/breaks")
    public ReconciliationBatchResponse.BreakPage listBreaks(@PathVariable UUID batchId){
        return new ReconciliationBatchResponse.BreakPage(reconciliation.listBreaks(batchId).stream()
                .map(ReconciliationBatchResponse.Break::from).toList());
    }
}
