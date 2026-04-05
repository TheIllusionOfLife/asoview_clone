package com.asoviewclone.common.time;

import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class SystemClockProvider implements ClockProvider {

  private final Clock clock = Clock.systemUTC();

  @Override
  public Clock clock() {
    return clock;
  }
}
