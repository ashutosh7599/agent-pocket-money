package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.reconciliation.ReconciliationBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, UUID> {}
