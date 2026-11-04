package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.model.audit.AuditEvent;
import com.agentpocketmoney.agentwallet.repository.AuditEventRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final Logger logger=LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository events; private final Clock clock;
    public AuditService(AuditEventRepository events,Clock clock){this.events=events;this.clock=clock;}
    @Transactional
    public void record(String action,String entityType,UUID entityId){
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        String actor=auth==null||!auth.isAuthenticated()?"system":auth.getName();
        String requestId=MDC.get("trace_id");
        events.save(new AuditEvent(UUID.randomUUID(),actor,action,entityType,entityId,requestId,clock.instant()));
        logger.atInfo().addKeyValue("audit.action",action).addKeyValue("audit.actor",actor)
                .addKeyValue("audit.entity.type",entityType).addKeyValue("audit.entity.id",entityId)
                .addKeyValue("request.id",requestId).log("Audit event recorded");
    }
}
