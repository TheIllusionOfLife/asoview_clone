package com.asoviewclone.commercecore.ai.chat;

import com.asoviewclone.commercecore.ai.chat.dto.ChatRequest;
import com.asoviewclone.commercecore.ai.chat.dto.ChatResponse;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/chat")
public class ChatController {

  private static final int MAX_MESSAGE_LENGTH = 500;

  private final Optional<ChatService> chatService;

  public ChatController(Optional<ChatService> chatService) {
    this.chatService = chatService;
  }

  @PostMapping
  public ChatResponse chat(@RequestBody ChatRequest request) {
    if (request.message() == null || request.message().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
    }
    if (request.message().length() > MAX_MESSAGE_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "message exceeds maximum length of " + MAX_MESSAGE_LENGTH);
    }
    if (chatService.isPresent()) {
      return chatService.get().chat(request.message());
    }
    return new ChatResponse("AIチャット機能は現在利用できません。");
  }
}
