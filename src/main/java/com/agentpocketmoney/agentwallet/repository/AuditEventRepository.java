package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.audit.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditEventRepository extends JpaRepository<AuditEvent,UUID> {
    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
