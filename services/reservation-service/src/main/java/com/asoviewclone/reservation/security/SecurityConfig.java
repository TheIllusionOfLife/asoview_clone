package com.asoviewclone.reservation.security;

import com.asoviewclone.common.error.JsonAccessDeniedHandler;
import com.asoviewclone.common.error.JsonAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
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
  private final JsonAccessDeniedHandler accessDeniedHandler;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;

  public SecurityConfig(
      FirebaseTokenFilter firebaseTokenFilter,
      JsonAccessDeniedHandler accessDeniedHandler,
      JsonAuthenticationEntryPoint authenticationEntryPoint) {
    this.firebaseTokenFilter = firebaseTokenFilter;
    this.accessDeniedHandler = accessDeniedHandler;
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(accessDeniedHandler)
                    .authenticationEntryPoint(authenticationEntryPoint))
        .authorizeHttpRequests(
            auth ->
                // See commerce-core SecurityConfig for rationale.
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/healthz",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/v1/reservation-slots")
                    .permitAll()
                    .requestMatchers("/v1/op/**")
                    .hasAuthority("ROLE_ADMIN")
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
