package com.agentpocketmoney.agentwallet.model.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_credentials")
public class ApiCredential {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private CredentialRole role;

    @Column(name = "wallet_scope")
    private UUID walletScope;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String keyHash;

    @Column(name = "key_id", nullable = false, length = 12, updatable = false)
    private String keyId;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApiCredential() {}

    public ApiCredential(UUID id, AppUser user, CredentialRole role, UUID walletScope,
            String keyHash, String keyId, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.role = role;
        this.walletScope = walletScope;
        this.keyHash = keyHash;
        this.keyId = keyId;
        this.createdAt = createdAt;
        this.revoked = false;
    }

    public void revoke() { this.revoked = true; }
    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public CredentialRole getRole() { return role; }
    public UUID getWalletScope() { return walletScope; }
    public String getKeyHash() { return keyHash; }
    public String getKeyId() { return keyId; }
    public boolean isRevoked() { return revoked; }
    public Instant getCreatedAt() { return createdAt; }
}
