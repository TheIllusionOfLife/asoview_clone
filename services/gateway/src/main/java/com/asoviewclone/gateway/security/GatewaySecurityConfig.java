package com.asoviewclone.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
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
        // Delegate CORS handling to the framework's CorsWebFilter (fed by
        // spring.cloud.gateway.server.webflux.globalcors). Spring Security
        // won't add the CORS headers itself because Spring Cloud Gateway
        // registers a CorsWebFilter bean rather than a
        // CorsConfigurationSource, but we still call .cors() to make the
        // intent explicit and future-proof.
        .cors(Customizer.withDefaults())
        .authorizeExchange(
            exchanges ->
                exchanges
                    // Preflights carry no Authorization header; let the
                    // gateway's CORS filter answer them. Without this,
                    // Spring Security 401s every OPTIONS preflight and
                    // browsers drop the response with no
                    // Access-Control-Allow-Origin header. Scoped to the
                    // API path shapes (both /v1/** and /api/v1/**, since
                    // the deployed ingress forwards /api/v1/** and the
                    // gateway's CORS filter runs before StripPrefix) so
                    // unauthenticated OPTIONS probes against actuator or
                    // other internal paths still fall through to the
                    // authenticated chain.
                    .pathMatchers(HttpMethod.OPTIONS, "/v1/**", "/api/v1/**")
                    .permitAll()
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
                    .pathMatchers(
                        HttpMethod.POST, "/v1/payments/webhooks/**", "/api/v1/payments/webhooks/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .addFilterBefore(firebaseTokenFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build();
  }
}
