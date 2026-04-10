package com.asoviewclone.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  private final FirebaseReactiveTokenFilter firebaseTokenFilter;

  public GatewaySecurityConfig(FirebaseReactiveTokenFilter firebaseTokenFilter) {
    this.firebaseTokenFilter = firebaseTokenFilter;
  }

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers("/healthz", "/actuator/**")
                    .permitAll()
                    // Admin endpoints under /v1/search/admin/** must NOT be public.
                    // Order matters: this denyAll matcher runs BEFORE the public
                    // /v1/search/** permitAll below. (PR #21 Codex finding: the
                    // reindex endpoint was reachable by any caller through the
                    // gateway with no role check.)
                    .pathMatchers("/v1/search/admin/**")
                    .denyAll()
                    .pathMatchers(
                        HttpMethod.GET,
                        "/v1/categories/**",
                        "/v1/products/**",
                        "/v1/areas/**",
                        "/v1/search/**",
                        // Dev ingress forwards /api/v1/** (StripPrefix
                        // runs after security), so permit both shapes.
                        "/api/v1/categories/**",
                        "/api/v1/products/**",
                        "/api/v1/areas/**",
                        "/api/v1/search/**")
                    .permitAll()
                    // Webhook endpoints use signature verification, not
                    // Firebase auth. Permit both /v1 and /api/v1 shapes.
                    .pathMatchers(HttpMethod.POST, "/v1/payments/webhooks/**", "/api/v1/payments/webhooks/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .addFilterBefore(firebaseTokenFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build();
  }
}
