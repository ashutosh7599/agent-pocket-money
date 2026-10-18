package com.agentpocketmoney.agentwallet.controller;

import com.agentpocketmoney.agentwallet.dto.WalletPolicyRequest;
import com.agentpocketmoney.agentwallet.dto.WalletPolicyResponse;
import com.agentpocketmoney.agentwallet.service.WalletPolicyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}/policy")
public class WalletPolicyController {
    private final WalletPolicyService policies;
    public WalletPolicyController(WalletPolicyService policies){this.policies=policies;}
    @PutMapping
    public WalletPolicyResponse replace(@PathVariable UUID walletId,@Valid @RequestBody WalletPolicyRequest request){
        return policies.replace(walletId,request);
    }
}
