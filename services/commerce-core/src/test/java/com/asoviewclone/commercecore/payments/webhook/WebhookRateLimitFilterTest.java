package com.asoviewclone.commercecore.payments.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebhookRateLimitFilterTest {

  private static final String PATH = "/v1/payments/webhooks/stripe";

  @Test
  void sixtyRequestsPassAndSixtyFirstIsRateLimited() throws Exception {
    WebhookRateLimitFilter filter = new WebhookRateLimitFilter(65_536L, 60);

    for (int i = 0; i < 60; i++) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      FilterChain chain = new MockFilterChain();
      filter.doFilter(request("1.2.3.4", 10), response, chain);
      assertThat(response.getStatus()).as("request %d should pass", i + 1).isEqualTo(200);
    }

    MockHttpServletResponse blocked = new MockHttpServletResponse();
    filter.doFilter(request("1.2.3.4", 10), blocked, new MockFilterChain());
    assertThat(blocked.getStatus()).isEqualTo(429);
  }

  @Test
  void oversizedBodyReturns413() throws Exception {
    WebhookRateLimitFilter filter = new WebhookRateLimitFilter(65_536L, 60);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request("9.9.9.9", 65_537), response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(413);
  }

  private static MockHttpServletRequest request(String remote, int contentLength) {
    MockHttpServletRequest r = new MockHttpServletRequest("POST", PATH);
    r.setRequestURI(PATH);
    r.setRemoteAddr(remote);
    r.setContentType("application/json");
    r.setContent(new byte[contentLength]);
    return r;
  }
}
