package com.agentpocketmoney.agentwallet.controller;

import com.agentpocketmoney.agentwallet.dto.AuditEventPage;
import com.agentpocketmoney.agentwallet.dto.AuditEventResponse;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.repository.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AuditController {
    private final AuditEventRepository events;
    public AuditController(AuditEventRepository events){this.events=events;}
    @GetMapping
    public AuditEventPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        if(page<0||size<1||size>100)throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST,"Page must be non-negative and size must be between 1 and 100.");
        var result=events.findAllByOrderByCreatedAtDesc(PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt")));
        return new AuditEventPage(result.getContent().stream().map(AuditEventResponse::from).toList(),
                result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }
}
