package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.reconciliation.ReconciliationBreak;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, UUID> {
    List<ReconciliationBreak> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
}
