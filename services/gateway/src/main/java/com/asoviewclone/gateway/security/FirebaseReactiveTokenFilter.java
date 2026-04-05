package com.asoviewclone.gateway.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class FirebaseReactiveTokenFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger(FirebaseReactiveTokenFilter.class);

  private final FirebaseAuth firebaseAuth;

  public FirebaseReactiveTokenFilter(FirebaseAuth firebaseAuth) {
    this.firebaseAuth = firebaseAuth;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return chain.filter(exchange);
    }

    String token = authHeader.substring(7);
    return Mono.fromCallable(() -> firebaseAuth.verifyIdToken(token))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            decodedToken -> {
              UsernamePasswordAuthenticationToken auth = createAuthentication(decodedToken);
              return chain
                  .filter(exchange)
                  .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
            })
        .onErrorResume(
            e -> {
              log.warn("Firebase token verification failed: {}", e.getMessage());
              return chain.filter(exchange);
            });
  }

  private UsernamePasswordAuthenticationToken createAuthentication(FirebaseToken token) {
    return new UsernamePasswordAuthenticationToken(
        token.getUid(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
}
