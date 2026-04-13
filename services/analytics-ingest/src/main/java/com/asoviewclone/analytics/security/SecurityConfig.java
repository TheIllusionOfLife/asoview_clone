package com.asoviewclone.analytics.security;

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
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/healthz", "/actuator/**")
                    .permitAll()
                    .requestMatchers("/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * Suppress Spring Boot's automatic servlet-filter registration. The filter is added to the
   * security chain via addFilterBefore; without this it would execute twice.
   */
  @Bean
  public FilterRegistrationBean<FirebaseTokenFilter> disableFirebaseTokenFilterAutoRegistration(
      FirebaseTokenFilter filter) {
    FilterRegistrationBean<FirebaseTokenFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
