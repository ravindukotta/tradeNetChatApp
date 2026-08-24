package com.zuhlke.tradenet.chat.controller;

import com.zuhlke.tradenet.chat.auth.AppUserPrincipal;
import com.zuhlke.tradenet.chat.entity.Message;
import com.zuhlke.tradenet.chat.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final int HISTORY_LIMIT = 50;

    private final MessageService messageService;

    public ConversationController(MessageService messageService) {
        this.messageService = messageService;
    }

    public record MessageDto(Long id, Long senderId, Long recipientId, String body, Instant createdAt) {
        static MessageDto from(Message message) {
            return new MessageDto(message.getId(), message.getSenderId(), message.getRecipientId(), message.getBody(), message.getCreatedAt());
        }
    }

    @GetMapping("/{otherUserId}/messages")
    public List<MessageDto> getMessages(@PathVariable Long otherUserId, @AuthenticationPrincipal AppUserPrincipal principal) {
        return messageService.getConversationPage(principal.getUserId(), otherUserId , HISTORY_LIMIT)
                .stream().map(MessageDto::from).toList();
    }
}
