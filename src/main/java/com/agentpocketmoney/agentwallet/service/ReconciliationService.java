package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.dto.SettlementReportRow;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.reconciliation.*;
import com.agentpocketmoney.agentwallet.model.wallet.Payment;
import com.agentpocketmoney.agentwallet.model.wallet.PaymentStatus;
import com.agentpocketmoney.agentwallet.repository.PaymentRepository;
import com.agentpocketmoney.agentwallet.repository.ReconciliationBatchRepository;
import com.agentpocketmoney.agentwallet.repository.ReconciliationBreakRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agentpocketmoney.agentwallet.audit.Audited;

@Service
public class ReconciliationService {
    private static final List<PaymentStatus> SETTLEABLE = List.of(PaymentStatus.CAPTURED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    private final PaymentRepository payments;
    private final ReconciliationBatchRepository batches;
    private final ReconciliationBreakRepository breaks;
    private final Clock clock;
    public ReconciliationService(PaymentRepository payments, ReconciliationBatchRepository batches,
            ReconciliationBreakRepository breaks, Clock clock) {
        this.payments=payments; this.batches=batches; this.breaks=breaks; this.clock=clock;
    }

    @Transactional
    @Audited(action="RECONCILIATION_COMPLETED",entityType="reconciliation_batch")
    public ReconciliationBatch reconcile(List<SettlementReportRow> report) {
        if (report == null) throw invalid("Settlement report is required.");
        Instant now=clock.instant(); UUID batchId=UUID.randomUUID();
        List<Payment> expected=payments.findByStatusIn(SETTLEABLE);
        Map<UUID,Payment> byId=new HashMap<>(); expected.forEach(payment -> byId.put(payment.getId(),payment));
        Map<UUID,SettlementReportRow> reported=new HashMap<>();
        List<ReconciliationBreak> found=new ArrayList<>(); int matched=0;
        for (SettlementReportRow row: report) {
            if (row==null || row.paymentId()==null || row.status()==null || row.amountMinorUnits()<=0)
                throw invalid("Settlement report rows require paymentId, positive amount, and status.");
            if (reported.putIfAbsent(row.paymentId(),row)!=null)
                throw invalid("Settlement report contains a duplicate payment ID.");
            Payment payment=byId.get(row.paymentId());
            if (payment==null) {
                found.add(make(batchId,ReconciliationBreakType.PAYMENT_NOT_FOUND,null,row.paymentId(),null,
                        row.amountMinorUnits(),null,row.status(),"Report references a payment that is not settleable." ,now));
            } else {
                boolean amountMatches=payment.getAmountMinorUnits()==row.amountMinorUnits();
                boolean statusMatches=payment.getStatus().name().equals(row.status());
                if (!amountMatches) found.add(make(batchId,ReconciliationBreakType.AMOUNT_MISMATCH,payment.getId(),row.paymentId(),
                        payment.getAmountMinorUnits(),row.amountMinorUnits(),payment.getStatus().name(),row.status(),"Reported amount differs from captured payment amount.",now));
                if (!statusMatches) found.add(make(batchId,ReconciliationBreakType.STATUS_MISMATCH,payment.getId(),row.paymentId(),
                        payment.getAmountMinorUnits(),row.amountMinorUnits(),payment.getStatus().name(),row.status(),"Reported status differs from payment status.",now));
                if (amountMatches&&statusMatches) matched++;
            }
        }
        for (Payment payment:expected) if (!reported.containsKey(payment.getId()))
            found.add(make(batchId,ReconciliationBreakType.PAYMENT_MISSING_FROM_REPORT,payment.getId(),null,
                    payment.getAmountMinorUnits(),null,payment.getStatus().name(),null,"Settleable payment is missing from the report.",now));
        ReconciliationBatch batch=batches.save(new ReconciliationBatch(batchId,report.size(),matched,found.size(),now));
        breaks.saveAll(found);
        return batch;
    }

    @Transactional
    @Audited(action="MOCK_RECONCILIATION_COMPLETED",entityType="reconciliation_batch")
    public ReconciliationBatch generateMockReport(MockReportScenario scenario) {
        List<Payment> expected=payments.findByStatusIn(SETTLEABLE);
        List<SettlementReportRow> report=new ArrayList<>();
        for (Payment payment:expected) report.add(new SettlementReportRow(payment.getId(),payment.getAmountMinorUnits(),payment.getStatus().name()));
        if (scenario==MockReportScenario.MISSING&&!report.isEmpty()) report.remove(0);
        else if (scenario==MockReportScenario.AMOUNT_MISMATCH&&!report.isEmpty()) {
            SettlementReportRow first=report.get(0); report.set(0,new SettlementReportRow(first.paymentId(),first.amountMinorUnits()+1,first.status()));
        } else if (scenario==MockReportScenario.STATUS_MISMATCH&&!report.isEmpty()) {
            SettlementReportRow first=report.get(0); report.set(0,new SettlementReportRow(first.paymentId(),first.amountMinorUnits(),"SETTLED"));
        } else if (scenario==MockReportScenario.UNKNOWN_PAYMENT) {
            report.add(new SettlementReportRow(UUID.randomUUID(),100,"SETTLED"));
        }
        return reconcile(report);
    }

    @Transactional(readOnly=true)
    public List<ReconciliationBreak> listBreaks(UUID batchId) {
        if (!batches.existsById(batchId)) throw new WalletException(WalletErrorCode.RECONCILIATION_BATCH_NOT_FOUND,"Reconciliation batch was not found.");
        return breaks.findByBatchIdOrderByCreatedAtAsc(batchId);
    }
    private static ReconciliationBreak make(UUID batch,ReconciliationBreakType type,UUID payment,UUID reportPayment,
            Long expected,Long reported,String expectedStatus,String reportedStatus,String detail,java.time.Instant now) {
        return new ReconciliationBreak(UUID.randomUUID(),batch,type,payment,reportPayment,expected,reported,expectedStatus,reportedStatus,detail,now);
    }
    private static WalletException invalid(String message) { return new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,message); }
}
