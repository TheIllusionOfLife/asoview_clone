package com.asoviewclone.commercecore.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.ai.chat.dto.ChatResponse;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.google.genai.Client;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ChatServiceTest {

  @Test
  void chat_returnsErrorMessageWhenGeminiFails() {
    Client client = mock(Client.class);
    ProductRepository repo = mock(ProductRepository.class);
    when(repo.findByStatus(any(ProductStatus.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    ChatService service = new ChatService(client, repo, "gemini-3-flash-preview");
    service.loadCatalog();

    // client.models is null in mock -> NPE -> caught -> returns error message
    ChatResponse response = service.chat("hello");

    assertThat(response.reply()).contains("申し訳ございません");
  }
}
