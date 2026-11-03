package com.agentpocketmoney.agentwallet.security;

import com.agentpocketmoney.agentwallet.repository.ApiCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyRegistry {
    private static final Set<String> STATIC_ROLES = Set.of("ADMIN", "WEBHOOK");
    private static final UUID LEGACY_SYSTEM_OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<String, ApiKeyPrincipal> staticKeys;
    private final ApiCredentialRepository credentials;

    @Autowired
    public ApiKeyRegistry(
            @Value("${WALLET_API_KEYS:}") String configuredKeys,
            ApiCredentialRepository credentials) {
        this.staticKeys = parseStaticKeys(configuredKeys);
        this.credentials = credentials;
    }

    ApiKeyRegistry(String configuredKeys) {
        this(configuredKeys, null);
    }

    public Optional<ApiKeyPrincipal> authenticate(String suppliedKey) {
        if (suppliedKey == null || suppliedKey.isBlank()) {
            return Optional.empty();
        }

        Optional<ApiKeyPrincipal> staticPrincipal = authenticateStaticKey(suppliedKey);
        if (staticPrincipal.isPresent() || credentials == null) {
            return staticPrincipal;
        }

        String keyHash = hash(suppliedKey);
        return credentials.findByKeyHashAndRevokedFalseAndUser_ActiveTrue(keyHash)
                .map(credential -> new ApiKeyPrincipal(
                        credential.getRole().name(),
                        credential.getUser().getId(),
                        credential.getWalletScope(),
                        credential.getKeyId()));
    }

    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private Optional<ApiKeyPrincipal> authenticateStaticKey(String suppliedKey) {
        byte[] candidate = suppliedKey.getBytes(StandardCharsets.UTF_8);
        return staticKeys.entrySet().stream()
                .filter(entry -> MessageDigest.isEqual(
                        entry.getKey().getBytes(StandardCharsets.UTF_8), candidate))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static Map<String, ApiKeyPrincipal> parseStaticKeys(String configuredKeys) {
        Map<String, ApiKeyPrincipal> parsed = new HashMap<>();
        if (configuredKeys == null || configuredKeys.isBlank()) {
            return Map.of();
        }

        for (String item : configuredKeys.split(",")) {
            parseStaticKey(item, parsed);
        }
        return Map.copyOf(parsed);
    }

    private static void parseStaticKey(String item, Map<String, ApiKeyPrincipal> parsed) {
        String[] parts = item.trim().split(":", 3);
        if (parts.length < 2 || parts[0].isBlank()) {
            throw new IllegalStateException(
                    "WALLET_API_KEYS entries must use key:ADMIN, key:WEBHOOK, "
                            + "or legacy key:AGENT:wallet-id format.");
        }

        String role = parts[1].trim().toUpperCase(Locale.ROOT);
        UUID walletScope = null;
        UUID userId = null;
        if ("AGENT".equals(role)) {
            walletScope = parseLegacyWalletScope(parts);
            userId = LEGACY_SYSTEM_OWNER_ID;
        } else if (!STATIC_ROLES.contains(role) || parts.length != 2) {
            throw new IllegalStateException(
                    "Only ADMIN and WEBHOOK keys may be configured without a wallet scope.");
        }

        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                role, userId, walletScope, keyId(parts[0]));
        if (parsed.putIfAbsent(parts[0], principal) != null) {
            throw new IllegalStateException("WALLET_API_KEYS contains a duplicate key.");
        }
    }

    private static UUID parseLegacyWalletScope(String[] parts) {
        if (parts.length != 3) {
            throw new IllegalStateException(
                    "Legacy AGENT API keys must be scoped to one wallet; "
                            + "create user-bound credentials through the admin API.");
        }
        try {
            return UUID.fromString(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("WALLET_API_KEYS contains an invalid wallet scope.");
        }
    }

    private static String keyId(String key) {
        return hash(key).substring(0, 12);
    }
}
