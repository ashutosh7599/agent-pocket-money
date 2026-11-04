package com.agentpocketmoney.agentwallet.model.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {}

    public AppUser(UUID id, String username, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.active = true;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
