package com.asoviewclone.commercecore.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression guard for the "validation errors masquerade as 403" bug found during the 2026-04-24
 * playtest on asoview-clone-dev.
 *
 * <p>Chain: Spring MVC's parameter binding fails (e.g. {@code not-a-uuid} can't convert to {@link
 * java.util.UUID}); {@code DefaultHandlerExceptionResolver} resolves to 400; the servlet container
 * then dispatches internally with {@code DispatcherType.ERROR} to {@code /error} for error-page
 * rendering; that second dispatch re-enters the {@link
 * org.springframework.security.web.SecurityFilterChain}; and because the chain had no permit for
 * the ERROR dispatcher type, {@code .anyRequest().authenticated()} caught the unauthenticated
 * re-dispatch and returned 403 with an empty body.
 *
 * <p>The Next.js consumer keys off HTTP 404 to render {@code notFound()} and has no branch for 403
 * on a public GET, so it rendered the Server Components error boundary (HTTP 500) instead of a 404.
 *
 * <p>This test MUST use a real servlet container ({@code WebEnvironment.RANDOM_PORT}) to reproduce
 * the bug — {@code MockMvc} dispatches exactly once and never triggers the servlet container's
 * error-page re-dispatch, so a MockMvc version would falsely pass against the broken code. We use
 * the JDK {@code HttpClient} rather than {@code TestRestTemplate} because Spring Boot 4 reorganized
 * the test client packages and the commerce-core test runtime doesn't pull the client transitively.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class ValidationErrorPassthroughIT {

  @LocalServerPort private int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void invalidUuidOnPublicGetReturns400NotForbidden() throws Exception {
    HttpResponse<String> res =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/v1/products/not-a-uuid"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    // Pre-fix: 403 empty body.
    // Post-fix: 400 JSON from GlobalExceptionHandler. Belt-and-suspenders: even if a
    // future refactor removes the explicit handler, dispatcherTypeMatchers(ERROR,FORWARD)
    // .permitAll() on the SecurityFilterChain ensures the container's error-page
    // re-dispatch is not re-gated by security.
    assertThat(res.statusCode()).isEqualTo(400);
    assertThat(res.body()).contains("\"error\"").contains("VALIDATION");
  }

  @Test
  void unknownPathUnderPublicPrefixReturns404NotForbidden() throws Exception {
    // Any path no controller can handle goes through NoResourceFoundException ->
    // default 404 -> ERROR re-dispatch. Pre-fix: 403 on the re-dispatch. Post-fix: 404.
    HttpResponse<String> res =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/v1/products/extra/segments/here"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(res.statusCode()).isEqualTo(404);
  }
}
