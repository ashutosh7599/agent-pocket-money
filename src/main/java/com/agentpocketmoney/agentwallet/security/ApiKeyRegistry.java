package com.agentpocketmoney.agentwallet.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiKeyRegistry {
    private final Map<String,ApiKeyPrincipal> keys;
    public ApiKeyRegistry(@Value("${WALLET_API_KEYS:}") String configuredKeys) {
        Map<String,ApiKeyPrincipal> parsed=new HashMap<>();
        if (configuredKeys!=null&&!configuredKeys.isBlank()) for (String item:configuredKeys.split(",")) {
            String[] parts=item.trim().split(":",3);
            if (parts.length<2||parts[0].isBlank()) throw new IllegalStateException("WALLET_API_KEYS entries must use key:ROLE[:wallet-id] format.");
            String role=parts[1].trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ADMIN","AGENT","WEBHOOK").contains(role)) throw new IllegalStateException("WALLET_API_KEYS contains an unsupported role.");
            UUID scope=null;
            if (parts.length==3&&!parts[2].isBlank()) try { scope=UUID.fromString(parts[2]); }
                catch (IllegalArgumentException e) { throw new IllegalStateException("WALLET_API_KEYS contains an invalid wallet scope."); }
            if (role.equals("AGENT")&&scope==null) throw new IllegalStateException("AGENT API keys must be scoped to one wallet.");
            if (!role.equals("AGENT")&&scope!=null) throw new IllegalStateException("Only AGENT API keys may have a wallet scope.");
            if (parsed.put(parts[0],new ApiKeyPrincipal(role,scope,keyId(parts[0])))!=null) throw new IllegalStateException("WALLET_API_KEYS contains a duplicate key.");
        }
        this.keys=Map.copyOf(parsed);
    }
    public Optional<ApiKeyPrincipal> authenticate(String supplied) {
        if (supplied==null||supplied.isBlank()) return Optional.empty();
        byte[] candidate=supplied.getBytes(StandardCharsets.UTF_8);
        for (var entry:keys.entrySet()) if (MessageDigest.isEqual(entry.getKey().getBytes(StandardCharsets.UTF_8),candidate)) return Optional.of(entry.getValue());
        return Optional.empty();
    }
    private static String keyId(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))).substring(0,12); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable.",e); }
    }
}
