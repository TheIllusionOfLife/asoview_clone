package com.asoviewclone.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockProviderTest {

  @Test
  void fixedClockReturnsDeterministicTime() {
    Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
    ClockProvider provider = () -> Clock.fixed(fixed, ZoneOffset.UTC);

    assertThat(provider.now()).isEqualTo(fixed);
    assertThat(provider.now()).isEqualTo(fixed);
  }

  @Test
  void systemClockProviderReturnsCurrentTime() {
    SystemClockProvider provider = new SystemClockProvider();
    Instant before = Instant.now().minusSeconds(1);
    Instant result = provider.now();
    Instant after = Instant.now().plusSeconds(1);

    assertThat(result).isAfter(before).isBefore(after);
  }
}
