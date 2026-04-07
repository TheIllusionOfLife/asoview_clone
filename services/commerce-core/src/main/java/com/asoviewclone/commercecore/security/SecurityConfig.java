package com.asoviewclone.commercecore.security;

import com.asoviewclone.commercecore.payments.webhook.WebhookRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
  private final WebhookRateLimitFilter webhookRateLimitFilter;

  public SecurityConfig(
      FirebaseTokenFilter firebaseTokenFilter, WebhookRateLimitFilter webhookRateLimitFilter) {
    this.firebaseTokenFilter = firebaseTokenFilter;
    this.webhookRateLimitFilter = webhookRateLimitFilter;
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
                    // Payment provider webhooks are authenticated by signature verification
                    // inside the handler, not by Firebase. FirebaseTokenFilter must ALSO
                    // skip this path so the raw bytes of the request body are preserved and
                    // no 401 is thrown for a missing bearer token.
                    .requestMatchers(HttpMethod.POST, "/v1/payments/webhooks/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/v1/categories/**", "/v1/products/**", "/v1/areas/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class)
        // Rate limit + body cap runs BEFORE Firebase so abusive callers are dropped before
        // any heavier work and unauthenticated webhook callers still get throttled.
        .addFilterBefore(webhookRateLimitFilter, FirebaseTokenFilter.class);

    return http.build();
  }

  /**
   * Suppress Spring Boot's automatic servlet-filter registration of the {@link
   * WebhookRateLimitFilter} bean. The filter is added to the security chain via {@code
   * addFilterBefore} above; without this disabling registration the filter would also be
   * auto-registered into the global servlet pipeline and execute twice — halving the effective rate
   * limit. (PR #21 review follow-up.)
   */
  @Bean
  public FilterRegistrationBean<WebhookRateLimitFilter> disableWebhookRateLimitAutoRegistration(
      WebhookRateLimitFilter filter) {
    FilterRegistrationBean<WebhookRateLimitFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
