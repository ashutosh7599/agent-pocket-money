package com.agentpocketmoney.agentwallet.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.agentpocketmoney.agentwallet.repository.WalletRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern WALLET_PATH=Pattern.compile("^/api/v1/wallets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$");
    private final ApiKeyRegistry keys;
    private final WalletRepository wallets;
    public ApiKeyAuthenticationFilter(ApiKeyRegistry keys, WalletRepository wallets){this.keys=keys;this.wallets=wallets;}
    ApiKeyAuthenticationFilter(ApiKeyRegistry keys){this(keys,null);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String apiKey=request.getHeader("X-API-Key");
        if (apiKey!=null) {
            var principal=keys.authenticate(apiKey);
            if (principal.isEmpty()) { unauthorized(response,"Invalid API key."); return; }
            ApiKeyPrincipal identity=principal.get();
            var authentication=new UsernamePasswordAuthenticationToken(identity,null,
                    List.of(new SimpleGrantedAuthority("ROLE_"+identity.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            if (identity.walletScope()!=null) {
                Matcher match=WALLET_PATH.matcher(request.getRequestURI());
                if (match.matches()&&!identity.walletScope().equals(UUID.fromString(match.group(1)))) {
                    forbidden(response,"API key is not scoped to this wallet."); return;
                }
            }
            Matcher walletPath=WALLET_PATH.matcher(request.getRequestURI());
            if (identity.userId()!=null && walletPath.matches() && wallets!=null) {
                UUID walletId=UUID.fromString(walletPath.group(1));
                if (!wallets.existsByIdAndOwnerId(walletId,identity.userId())) {
                    forbidden(response,"This identity does not have access to this wallet."); return;
                }
            } else if (Set.of("USER","AGENT").contains(identity.role()) && identity.userId()==null) {
                forbidden(response,"Credential has no user identity."); return;
            }
        }
        chain.doFilter(request,response);
    }
    private static void unauthorized(HttpServletResponse response,String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":401,\"errorCode\":\"UNAUTHORIZED\",\"detail\":\""+detail+"\"}");
    }
    private static void forbidden(HttpServletResponse response,String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":403,\"errorCode\":\"FORBIDDEN\",\"detail\":\""+detail+"\"}");
    }
}
