package com.agentpocketmoney.agentwallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentpocketmoney.agentwallet.dto.SettlementReportRow;
import com.agentpocketmoney.agentwallet.model.ledger.AccountType;
import com.agentpocketmoney.agentwallet.model.ledger.LedgerAccount;
import com.agentpocketmoney.agentwallet.model.reconciliation.*;
import com.agentpocketmoney.agentwallet.model.wallet.*;
import com.agentpocketmoney.agentwallet.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {
    @Mock PaymentRepository payments;
    @Mock ReconciliationBatchRepository batches;
    @Mock ReconciliationBreakRepository breaks;
    private final Instant now=Instant.parse("2026-10-18T12:00:00Z");
    private ReconciliationService service;
    private Payment captured;

    @BeforeEach void setUp(){
        service=new ReconciliationService(payments,batches,breaks,Clock.fixed(now,ZoneOffset.UTC));
        Wallet wallet=new Wallet(UUID.randomUUID(),new LedgerAccount("wallet-recon","Wallet",AccountType.LIABILITY,"INR"),"INR");
        captured=new Payment(UUID.randomUUID(),wallet,"merchant-a",5000,"INR","FOOD","meal","recon-key",now);
        captured.authorize(UUID.randomUUID(),now); captured.capture(UUID.randomUUID(),"capture-key",now);
        when(payments.findByStatusIn(any())).thenReturn(List.of(captured));
        when(batches.save(any(ReconciliationBatch.class))).thenAnswer(call->call.getArgument(0));
    }

    @Test void reportsMatchedCapturedPayment(){
        ReconciliationBatch batch=service.reconcile(List.of(new SettlementReportRow(captured.getId(),5000,"CAPTURED")));
        assertThat(batch.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(batch.getMatchedCount()).isEqualTo(1);
        assertThat(batch.getBreakCount()).isZero();
    }

    @Test void detectsAmountStatusMissingAndUnknownPaymentBreaks(){
        List<SettlementReportRow> report=List.of(
                new SettlementReportRow(captured.getId(),4999,"SETTLED"),
                new SettlementReportRow(UUID.randomUUID(),100,"SETTLED"));
        ReconciliationBatch batch=service.reconcile(report);
        assertThat(batch.getStatus()).isEqualTo(ReconciliationStatus.BREAKS_FOUND);
        assertThat(batch.getBreakCount()).isEqualTo(3);
        @SuppressWarnings("unchecked") ArgumentCaptor<Iterable<ReconciliationBreak>> captor=ArgumentCaptor.forClass(Iterable.class);
        verify(breaks).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ReconciliationBreak::getType)
                .containsExactlyInAnyOrder(ReconciliationBreakType.AMOUNT_MISMATCH,
                        ReconciliationBreakType.STATUS_MISMATCH,ReconciliationBreakType.PAYMENT_NOT_FOUND);
    }

    @Test void detectsCapturedPaymentMissingFromReport(){
        ReconciliationBatch batch=service.reconcile(List.of());
        assertThat(batch.getBreakCount()).isEqualTo(1);
        verify(breaks).saveAll(any());
    }

    @Test void mockScenarioProducesPersistedBreaks(){
        ReconciliationBatch batch=service.generateMockReport(MockReportScenario.AMOUNT_MISMATCH);
        assertThat(batch.getBreakCount()).isEqualTo(1);
        verify(breaks).saveAll(any());
    }
}
