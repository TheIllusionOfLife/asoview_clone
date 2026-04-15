package com.asoviewclone.commercecore.ai.chat;

import com.asoviewclone.commercecore.ai.chat.dto.ChatResponse;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Gemini-powered chatbot that answers questions about activities, products, and areas. Uses the
 * product catalog as context in the system prompt.
 */
@Service
@ConditionalOnProperty(name = "asoview.ai.enabled", havingValue = "true")
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final Duration GEMINI_TIMEOUT = Duration.ofSeconds(15);

  private final String model;
  private final Client geminiClient;
  private final ProductRepository productRepository;
  private String catalogContext;

  public ChatService(
      Client geminiClient,
      ProductRepository productRepository,
      @Value("${asoview.ai.model:gemini-3-flash-preview}") String model) {
    this.geminiClient = geminiClient;
    this.productRepository = productRepository;
    this.model = model;
  }

  @PostConstruct
  void loadCatalog() {
    try {
      var products =
          productRepository.findByStatus(ProductStatus.ACTIVE, PageRequest.of(0, 50)).getContent();
      StringBuilder sb = new StringBuilder();
      for (Product p : products) {
        sb.append("- ")
            .append(p.getTitle())
            .append(" (ID: ")
            .append(p.getId())
            .append("): ")
            .append(p.getDescription() != null ? p.getDescription() : "")
            .append("\n");
      }
      catalogContext = sb.toString();
      log.info("Loaded {} products into chatbot context", products.size());
    } catch (Exception e) {
      log.warn("Failed to load product catalog for chatbot", e);
      catalogContext = "";
    }
  }

  public ChatResponse chat(String message) {
    try {
      String prompt = buildPrompt(message);
      // Wrap the Gemini call in a 15 s timeout so a hung upstream doesn't tie up a tomcat
      // thread indefinitely. Gemini's SDK doesn't expose a deadline on generateContent(),
      // so we run it on a CompletableFuture and time out at the caller.
      String text =
          CompletableFuture.supplyAsync(
                  () -> {
                    GenerateContentResponse response =
                        geminiClient.models.generateContent(model, prompt, null);
                    return response.text();
                  })
              .get(GEMINI_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      if (text == null || text.isBlank()) {
        log.warn("Gemini returned null/blank response for model={}", model);
        return new ChatResponse("回答を生成できませんでした。もう一度お試しください。");
      }
      return new ChatResponse(text);
    } catch (TimeoutException e) {
      log.warn("Gemini chat timed out after {} ms", GEMINI_TIMEOUT.toMillis());
      return new ChatResponse("応答に時間がかかっています。しばらくしてから再度お試しください。");
    } catch (Exception e) {
      log.error("Gemini chat failed", e);
      return new ChatResponse("申し訳ございません。現在チャットをご利用いただけません。");
    }
  }

  private String buildPrompt(String userMessage) {
    return """
        あなたはasoview（アソビュー）のアシスタントです。日本のレジャー・体験予約プラットフォームについての質問に答えてください。
        以下の商品カタログを参考にしてください:

        %s

        ユーザーの質問: %s

        日本語で簡潔に回答してください。予約については商品詳細ページへの案内をしてください。
        """
        .formatted(catalogContext, userMessage);
  }
}
