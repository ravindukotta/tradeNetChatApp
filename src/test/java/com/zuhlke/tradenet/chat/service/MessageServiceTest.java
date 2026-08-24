package com.zuhlke.tradenet.chat.service;

import com.zuhlke.tradenet.chat.entity.Message;
import com.zuhlke.tradenet.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MessageServiceTest {

    @Autowired
    private MessageRepository messageRepository;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository);
    }

    @Test
    void persistAndGetIdIsIdempotentForARetriedSend() {
        var first = messageService.persistAndGetId(1L, 2L, "hello", "c1");
        var second = messageService.persistAndGetId(1L, 2L, "hello (retried body ignored)", "c1");

        assertThat(first.wasDuplicate()).isFalse();
        assertThat(second.wasDuplicate()).isTrue();
        assertThat(second.serverMessageId()).isEqualTo(first.serverMessageId());
        assertThat(messageRepository.count()).isEqualTo(1L);
    }

    @Test
    void sameClientMsgIdFromDifferentSendersIsNotTreatedAsDuplicate() {
        // The idempotency key is scoped to (senderId, clientMsgId) - two different
        // senders can legitimately generate the same client-side id independently.
        var fromAlice = messageService.persistAndGetId(1L, 3L, "hi from alice", "shared-id");
        var fromBob = messageService.persistAndGetId(2L, 3L, "hi from bob", "shared-id");

        assertThat(fromAlice.serverMessageId()).isNotEqualTo(fromBob.serverMessageId());
        assertThat(messageRepository.count()).isEqualTo(2L);
    }

    @Test
    void conversationPageReturnsOnlyThatPairOldestFirst() {
        messageService.persistAndGetId(1L, 2L, "a to b - 1", "m1");
        messageService.persistAndGetId(2L, 1L, "b to a - 1", "m2");
        messageService.persistAndGetId(1L, 3L, "a to c - unrelated conversation", "m3");

        List<Message> page = messageService.getConversationPage(1L, 2L, 50);

        assertThat(page).extracting(Message::getBody)
                .containsExactly("a to b - 1", "b to a - 1");
    }
}
