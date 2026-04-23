package com.asoviewclone.gateway.cors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Regression guard for the Spring Cloud Gateway WebFlux CORS config.
 *
 * <p>We were bitten twice: first the globalcors block lived at the pre-2025.x property path ({@code
 * spring.cloud.gateway.globalcors}), which Spring Cloud 2025 silently ignores; then the path
 * pattern matched {@code /v1/**} while browsers request {@code /api/v1/**} (gateway strips the
 * {@code /api} prefix after the CORS filter runs). Either miss makes every cross-origin preflight
 * fall through to Spring Security and return 401 without CORS headers.
 *
 * <p>This test exercises the OPTIONS preflight end-to-end through the real gateway filter chain on
 * a random port, so it fails if either regression returns.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "GATEWAY_CORS_ORIGINS=https://asoview-operator.duckdns.org")
class CorsPreflightTest {

  private static final String OPERATOR_ORIGIN = "https://asoview-operator.duckdns.org";

  @LocalServerPort private int port;

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  // Exercise both URL shapes: /v1/** (local + base profile) and /api/v1/**
  // (deployed dev + ingress). The original bug missed the /api/v1/** case,
  // so pinning both shapes prevents a future pattern drift from shipping
  // while the local shape stays green.
  @ParameterizedTest
  @ValueSource(strings = {"/v1/op/me/venues", "/api/v1/op/me/venues"})
  void preflightAllowsOperatorOriginOnOpEndpoint(String path) {
    webTestClient
        .options()
        .uri(path)
        .header(HttpHeaders.ORIGIN, OPERATOR_ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, OPERATOR_ORIGIN)
        .expectHeader()
        .value(
            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
            methods -> assertThat(methods).contains("GET"))
        .expectHeader()
        .value(
            HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
            headers -> assertThat(headers).contains(HttpHeaders.AUTHORIZATION));
  }

  @Test
  void preflightRejectsUnknownOrigin() {
    // A random origin not in the allow-list must not receive an echo of the
    // Origin header. Spring Cloud Gateway's CORS filter either returns 403
    // or omits Access-Control-Allow-Origin entirely; the important property
    // is that it never echoes the attacker's origin.
    webTestClient
        .options()
        .uri("/v1/op/me/venues")
        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
        .exchange()
        .expectHeader()
        .value(
            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
            allowed -> assertThat(allowed).isNotEqualTo("https://evil.example.com"));
  }
}
