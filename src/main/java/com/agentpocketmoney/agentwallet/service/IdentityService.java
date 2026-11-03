package com.agentpocketmoney.agentwallet.service;

import com.agentpocketmoney.agentwallet.audit.Audited;
import com.agentpocketmoney.agentwallet.dto.CredentialResponse;
import com.agentpocketmoney.agentwallet.dto.UserResponse;
import com.agentpocketmoney.agentwallet.exception.WalletErrorCode;
import com.agentpocketmoney.agentwallet.exception.WalletException;
import com.agentpocketmoney.agentwallet.model.identity.ApiCredential;
import com.agentpocketmoney.agentwallet.model.identity.AppUser;
import com.agentpocketmoney.agentwallet.model.identity.CredentialRole;
import com.agentpocketmoney.agentwallet.repository.ApiCredentialRepository;
import com.agentpocketmoney.agentwallet.repository.AppUserRepository;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import com.agentpocketmoney.agentwallet.security.ApiKeyRegistry;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AppUserRepository users;
    private final ApiCredentialRepository credentials;
    private final WalletRepository wallets;
    private final Clock clock;

    public IdentityService(AppUserRepository users, ApiCredentialRepository credentials,
            WalletRepository wallets, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    @Audited(action = "USER_CREATED", entityType = "user")
    public UserResponse createUser(String username) {
        if (users.findByUsername(username).isPresent()) {
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST, "Username is already in use.");
        }
        Instant now = clock.instant();
        AppUser user = users.save(new AppUser(UUID.randomUUID(), username, now));
        return new UserResponse(user.getId(), user.getUsername(), user.isActive(), user.getCreatedAt());
    }

    @Transactional
    @Audited(action = "API_CREDENTIAL_CREATED", entityType = "api_credential")
    public CredentialResponse createCredential(UUID userId, CredentialRole role, UUID walletScope) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST, "User not found."));
        if (!user.isActive()) {
            throw new WalletException(WalletErrorCode.INVALID_WALLET_REQUEST, "User is inactive.");
        }
        if (role == CredentialRole.USER && walletScope != null) {
            throw new WalletException(
                    WalletErrorCode.INVALID_WALLET_REQUEST,
                    "USER credentials cannot have a wallet scope.");
        }
        if (role == CredentialRole.AGENT
                && (walletScope == null || !wallets.existsByIdAndOwnerId(walletScope, userId))) {
            throw new WalletException(
                    WalletErrorCode.INVALID_WALLET_REQUEST,
                    "AGENT credentials require a wallet owned by this user.");
        }

        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String apiKey = "aw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String keyHash = ApiKeyRegistry.hash(apiKey);
        String keyId = keyHash.substring(0, 12);
        Instant now = clock.instant();
        ApiCredential credential = credentials.save(new ApiCredential(UUID.randomUUID(), user, role,
                walletScope, keyHash, keyId, now));
        return new CredentialResponse(credential.getId(), role.name(), walletScope, apiKey, keyId, now);
    }

    @Transactional
    @Audited(action = "API_CREDENTIAL_REVOKED", entityType = "api_credential")
    public void revokeCredential(UUID userId, UUID credentialId) {
        ApiCredential credential = credentials.findByIdAndUser_Id(credentialId, userId)
                .orElseThrow(() -> new WalletException(
                        WalletErrorCode.INVALID_WALLET_REQUEST,
                        "Credential not found."));
        credential.revoke();
    }
}
