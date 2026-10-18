package com.agentpocketmoney.agentwallet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyRegistry keys){return new ApiKeyAuthenticationFilter(keys);}
    @Bean
    UserDetailsService disablePasswordAuthentication(){return username->{throw new UsernameNotFoundException("Password authentication is disabled.");};}
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,ApiKeyAuthenticationFilter apiKeys) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e->e.authenticationEntryPoint((request,response,exception)->{
                    response.setStatus(401);response.setContentType("application/problem+json");
                    response.getWriter().write("{\"status\":401,\"errorCode\":\"UNAUTHORIZED\",\"detail\":\"API key required.\"}");
                }).accessDeniedHandler((request,response,exception)->{
                    response.setStatus(403);response.setContentType("application/problem+json");
                    response.getWriter().write("{\"status\":403,\"errorCode\":\"FORBIDDEN\",\"detail\":\"Access denied.\"}");
                }))
                .authorizeHttpRequests(auth->auth
                        .requestMatchers("/actuator/health/**","/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/webhooks/**").hasAnyRole("WEBHOOK","ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/wallets").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/wallets/*/policy").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/wallets/*/top-ups").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/wallets/*/mandates").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/wallets/*/payments/*/approve").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/wallets/*/payments/*/reject").hasRole("ADMIN")
                        .requestMatchers("/api/v1/reconciliation/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasAnyRole("ADMIN","AGENT")
                        .anyRequest().permitAll())
                .addFilterBefore(apiKeys,UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
