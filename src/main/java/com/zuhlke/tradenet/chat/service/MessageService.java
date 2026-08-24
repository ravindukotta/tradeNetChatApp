package com.zuhlke.tradenet.chat.service;

import com.zuhlke.tradenet.chat.entity.Message;
import com.zuhlke.tradenet.chat.repository.MessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public record PersistResult(Long serverMessageId, Instant createdAt, boolean wasDuplicate) {
    }

    @Transactional
    public PersistResult persistAndGetId(Long senderId, Long recipientId, String body, String clientMsgId) {
        var existing = messageRepository.findBySenderIdAndClientMsgId(senderId, clientMsgId);
        if (existing.isPresent()) {
            return toDuplicateResult(existing.get());
        }
        try {
            Message saved = messageRepository.saveAndFlush(new Message(senderId, recipientId, body, clientMsgId));
            return new PersistResult(saved.getId(), saved.getCreatedAt(), false);
        } catch (DataIntegrityViolationException dataIntegrityViolationException) {
            // in a case of same msg comes in few milliseconds apart //highly unlikely also not an error
            return messageRepository.findBySenderIdAndClientMsgId(senderId, clientMsgId)
                    .map(this::toDuplicateResult)
                    .orElseThrow(() -> dataIntegrityViolationException);
        }
    }

    private PersistResult toDuplicateResult(Message existing) {
        return new PersistResult(existing.getId(), existing.getCreatedAt(), true);
    }

    public List<Message> getConversationPage(Long userA, Long userB, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<Message> newestFirst = messageRepository.findConversationPageDesc(userA, userB, pageable);
        Collections.reverse(newestFirst);
        return newestFirst;
    }
}
