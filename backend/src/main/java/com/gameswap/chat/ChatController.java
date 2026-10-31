package com.gameswap.chat;

import com.gameswap.chat.ChatDtos.*;
import com.gameswap.common.api.CursorPage;
import com.gameswap.common.security.AuthenticatedUser;
import com.gameswap.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/threads")
    public ResponseEntity<ChatThreadDto> getOrCreateThread(@Valid @RequestBody OpenThreadRequestDto request) {
        return ResponseEntity.ok(chatService.getOrCreateThread(SecurityUtils.currentUserId(), request.recipientId()));
    }

    @GetMapping("/threads")
    public ResponseEntity<CursorPage<ChatThreadDto>> listThreads(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(chatService.listThreads(SecurityUtils.currentUserId(), cursor, limit));
    }

    @GetMapping("/threads/{threadId}")
    public ResponseEntity<ChatThreadDto> getThread(@PathVariable UUID threadId) {
        return ResponseEntity.ok(chatService.getThreadById(SecurityUtils.currentUserId(), threadId));
    }

    @GetMapping("/threads/{threadId}/messages")
    public ResponseEntity<CursorPage<ChatMessageDto>> listMessages(
        @PathVariable UUID threadId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(chatService.listMessages(SecurityUtils.currentUserId(), threadId, cursor, limit));
    }

    @PostMapping("/threads/{threadId}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(
        @PathVariable UUID threadId,
        @Valid @RequestBody SendMessageRequestDto request
    ) {
        return ResponseEntity.ok(chatService.sendMessage(SecurityUtils.currentUserId(), threadId, request));
    }

    @MessageMapping("/chats/{threadId}/send")
    public void sendMessageViaStomp(
        @DestinationVariable UUID threadId,
        @Payload SendMessageRequestDto request,
        Principal principal
    ) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUser user) {
            chatService.sendMessage(user.userId(), threadId, request);
        }
    }
}

