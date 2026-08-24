package com.zuhlke.tradenet.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuhlke.tradenet.chat.model.WsEnvelope;
import com.zuhlke.tradenet.chat.repository.UserRepository;
import com.zuhlke.tradenet.chat.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private ConnectionRegistry connectionRegistry;
    @Mock
    private MessageService messageService;
    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatWebSocketHandler handler;
    private WebSocketSession senderSession;

    @BeforeEach
    void setUp() {
        handler = new ChatWebSocketHandler(connectionRegistry, messageService, userRepository, objectMapper);
        senderSession = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(AuthHandshakeInterceptor.USER_ID_ATTRIBUTE, 1L);
        when(senderSession.getAttributes()).thenReturn(attributes);
    }

    private TextMessage sendEnvelope(Long to, String body, String clientMsgId) throws Exception {
        WsEnvelope envelope = new WsEnvelope("send", to, null, body, clientMsgId, null, null, null);
        return new TextMessage(objectMapper.writeValueAsString(envelope));
    }

    @Test
    void messageToKnownRecipientIsPushedAndAcked() throws Exception {
        // ConnectionRegistry.sendToUser already no-ops for a recipient with no live
        // session (verified separately in ConnectionRegistryTest), so the handler
        // pushes unconditionally here regardless of whether the recipient is online.
        when(userRepository.existsById(2L)).thenReturn(true);
        Instant now = Instant.now();
        when(messageService.persistAndGetId(1L, 2L, "hi", "c1"))
                .thenReturn(new MessageService.PersistResult(10L, now, false));

        handler.handleTextMessage(senderSession, sendEnvelope(2L, "hi", "c1"));

        ArgumentCaptor<WsEnvelope> pushed = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(connectionRegistry).sendToUser(eq(2L), pushed.capture());
        assertThat(pushed.getValue().type()).isEqualTo("deliver");
        assertThat(pushed.getValue().from()).isEqualTo(1L);
        assertThat(pushed.getValue().serverMsgId()).isEqualTo(10L);

        ArgumentCaptor<WsEnvelope> ack = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(connectionRegistry).sendToSession(eq(senderSession), ack.capture());
        assertThat(ack.getValue().type()).isEqualTo("ack");
    }

    @Test
    void unknownRecipientIsRejectedWithoutPersisting() throws Exception {
        when(userRepository.existsById(99L)).thenReturn(false);

        handler.handleTextMessage(senderSession, sendEnvelope(99L, "hi", "c1"));

        verify(messageService, never()).persistAndGetId(any(), any(), any(), any());
        ArgumentCaptor<WsEnvelope> error = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(connectionRegistry).sendToSession(eq(senderSession), error.capture());
        assertThat(error.getValue().type()).isEqualTo("error");
    }

    @Test
    void duplicateSendDoesNotTriggerASecondLivePush() throws Exception {
        // This is a retried send with a clientMsgId already persisted.
        when(userRepository.existsById(2L)).thenReturn(true);
        when(messageService.persistAndGetId(1L, 2L, "hi", "c1"))
                .thenReturn(new MessageService.PersistResult(10L, Instant.now(), true));

        handler.handleTextMessage(senderSession, sendEnvelope(2L, "hi", "c1"));

        verify(connectionRegistry, never()).sendToUser(any(), any());
        ArgumentCaptor<WsEnvelope> ack = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(connectionRegistry).sendToSession(eq(senderSession), ack.capture());
        assertThat(ack.getValue().type()).isEqualTo("ack");
    }

    @Test
    void rapidConsecutiveSendsAreDeliveredInAcceptedOrder() throws Exception {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(messageService.persistAndGetId(1L, 2L, "first", "c1"))
                .thenReturn(new MessageService.PersistResult(1L, Instant.now(), false));
        when(messageService.persistAndGetId(1L, 2L, "second", "c2"))
                .thenReturn(new MessageService.PersistResult(2L, Instant.now(), false));

        handler.handleTextMessage(senderSession, sendEnvelope(2L, "first", "c1"));
        handler.handleTextMessage(senderSession, sendEnvelope(2L, "second", "c2"));

        ArgumentCaptor<WsEnvelope> pushed = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(connectionRegistry, org.mockito.Mockito.times(2)).sendToUser(eq(2L), pushed.capture());
        List<WsEnvelope> values = pushed.getAllValues();
        assertThat(values.get(0).serverMsgId()).isEqualTo(1L);
        assertThat(values.get(1).serverMsgId()).isEqualTo(2L);
    }
}
