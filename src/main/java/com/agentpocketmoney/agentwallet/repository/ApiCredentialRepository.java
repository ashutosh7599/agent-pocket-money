package com.agentpocketmoney.agentwallet.repository;

import com.agentpocketmoney.agentwallet.model.identity.ApiCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {
    Optional<ApiCredential> findByKeyHashAndRevokedFalseAndUser_ActiveTrue(String keyHash);
    Optional<ApiCredential> findByIdAndUser_Id(UUID id, UUID userId);
}
