package com.asoviewclone.commercecore.testutil;

import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest @WebMvcTest} slices do not
 * register Spring Security's {@link AuthenticationPrincipalArgumentResolver}. Without it, any
 * {@link
 * org.springframework.security.core.annotation.AuthenticationPrincipal @AuthenticationPrincipal}
 * parameter falls through to {@code ServletModelAttributeMethodProcessor}, which reflectively
 * instantiates the target (e.g. our {@code AuthenticatedUser} record) via its canonical constructor
 * with null fields. That makes it impossible to put null-guards in the principal's compact
 * constructor without breaking every slice test.
 *
 * <p>Import this via {@code @Import(WebMvcSliceSecurityConfig.class)} on any {@code @WebMvcTest}
 * class whose tested controller has a {@code @AuthenticationPrincipal} parameter.
 */
@TestConfiguration
public class WebMvcSliceSecurityConfig {

  @Bean
  WebMvcConfigurer authenticationPrincipalArgumentResolverConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthenticationPrincipalArgumentResolver());
      }
    };
  }
}
