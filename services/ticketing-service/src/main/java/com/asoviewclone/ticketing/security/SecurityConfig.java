package com.asoviewclone.ticketing.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/healthz", "/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/v1/op/tickets/*/revoke")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers("/v1/op/tickets/**")
                    .hasAnyAuthority("ROLE_SCANNER", "ROLE_ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public FilterRegistrationBean<FirebaseTokenFilter> disableFirebaseTokenFilterAutoRegistration(
      FirebaseTokenFilter filter) {
    FilterRegistrationBean<FirebaseTokenFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
