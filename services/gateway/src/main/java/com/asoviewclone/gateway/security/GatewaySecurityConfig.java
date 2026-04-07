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
                        "/v1/search/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .addFilterBefore(firebaseTokenFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build();
  }
}
