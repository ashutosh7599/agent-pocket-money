package com.agentpocketmoney.agentwallet.controller;

import com.agentpocketmoney.agentwallet.dto.CreateCredentialRequest;
import com.agentpocketmoney.agentwallet.dto.CreateUserRequest;
import com.agentpocketmoney.agentwallet.dto.CredentialResponse;
import com.agentpocketmoney.agentwallet.dto.UserResponse;
import com.agentpocketmoney.agentwallet.service.IdentityService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class IdentityAdminController {
    private final IdentityService identities;

    public IdentityAdminController(IdentityService identities) {
        this.identities = identities;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = identities.createUser(request.username());
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + user.id())).body(user);
    }

    @PostMapping("/{userId}/credentials")
    public ResponseEntity<CredentialResponse> createCredential(@PathVariable UUID userId,
            @Valid @RequestBody CreateCredentialRequest request) {
        CredentialResponse credential = identities.createCredential(
                userId,
                request.role(),
                request.walletScope());
        return ResponseEntity.created(URI.create(
                "/api/v1/admin/users/" + userId + "/credentials/" + credential.id()))
                .body(credential);
    }

    @DeleteMapping("/{userId}/credentials/{credentialId}")
    public ResponseEntity<Void> revokeCredential(@PathVariable UUID userId, @PathVariable UUID credentialId) {
        identities.revokeCredential(userId, credentialId);
        return ResponseEntity.noContent().build();
    }
}
