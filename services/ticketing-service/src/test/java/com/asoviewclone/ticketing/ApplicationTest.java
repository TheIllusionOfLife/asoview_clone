package com.asoviewclone.ticketing;

import com.asoviewclone.ticketing.testutil.SpannerEmulatorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(SpannerEmulatorConfig.class)
@ActiveProfiles("test")
class ApplicationTest {

  @Test
  void contextLoads() {}
}
