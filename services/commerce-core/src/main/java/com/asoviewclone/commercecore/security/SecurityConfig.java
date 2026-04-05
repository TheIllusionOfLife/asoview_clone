package com.asoviewclone.commercecore.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final FirebaseTokenFilter firebaseTokenFilter;

  public SecurityConfig(FirebaseTokenFilter firebaseTokenFilter) {
    this.firebaseTokenFilter = firebaseTokenFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/healthz", "/actuator/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/categories/**", "/v1/products/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
