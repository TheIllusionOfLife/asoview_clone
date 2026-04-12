package com.asoviewclone.commercecore.ai.config;

import com.google.genai.Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a Gemini API client bean. Only activated when asoview.ai.enabled=true and GOOGLE_API_KEY
 * environment variable is set. Disabled by default to avoid crashes in test/local without API key.
 */
@Configuration
@ConditionalOnProperty(name = "asoview.ai.enabled", havingValue = "true")
public class GeminiConfig {

  @Bean
  public Client geminiClient() {
    return new Client();
  }
}
