package com.asoviewclone.commercecore.ai.chat;

import com.asoviewclone.commercecore.ai.chat.dto.ChatRequest;
import com.asoviewclone.commercecore.ai.chat.dto.ChatResponse;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/chat")
public class ChatController {

  private final Optional<ChatService> chatService;

  public ChatController(Optional<ChatService> chatService) {
    this.chatService = chatService;
  }

  @PostMapping
  public ChatResponse chat(@RequestBody ChatRequest request) {
    if (chatService.isPresent()) {
      return chatService.get().chat(request.message());
    }
    return new ChatResponse("AIチャット機能は現在利用できません。");
  }
}
