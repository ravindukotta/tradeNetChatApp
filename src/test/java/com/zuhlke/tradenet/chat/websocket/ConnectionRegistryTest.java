package com.zuhlke.tradenet.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuhlke.tradenet.chat.model.WsEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionRegistryTest {

    private final ConnectionRegistry registry = new ConnectionRegistry(new ObjectMapper());

    @Test
    void registerMakesSessionCountAsLive() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        registry.register(1L, session);

        assertThat(registry.hasLiveSession(1L)).isTrue();
        assertThat(registry.hasLiveSession(2L)).isFalse();
    }

    @Test
    void registeringASecondSessionForTheSameUserReplacesAndClosesTheFirst() throws Exception {
        WebSocketSession tab1 = mock(WebSocketSession.class);
        WebSocketSession tab2 = mock(WebSocketSession.class);
        when(tab1.isOpen()).thenReturn(true);
        when(tab2.isOpen()).thenReturn(true);

        registry.register(1L, tab1);
        registry.register(1L, tab2);
        registry.sendToUser(1L, WsEnvelope.error("hi"));

        verify(tab1).close(any(CloseStatus.class));
        verify(tab1, never()).sendMessage(any());
        verify(tab2).sendMessage(any());
    }

    @Test
    void unregisterOfAStaleReplacedSessionDoesNotEvictTheCurrentOne() throws Exception {
        WebSocketSession tab1 = mock(WebSocketSession.class);
        WebSocketSession tab2 = mock(WebSocketSession.class);
        when(tab1.isOpen()).thenReturn(true);
        when(tab2.isOpen()).thenReturn(true);

        registry.register(1L, tab1);
        registry.register(1L, tab2);
        // Simulate afterConnectionClosed firing late for the already-replaced session.
        registry.unregister(1L, tab1);

        assertThat(registry.hasLiveSession(1L)).isTrue();
        registry.sendToUser(1L, WsEnvelope.error("hi"));
        verify(tab2).sendMessage(any());
    }

    @Test
    void sendToUserDoesNotReachOtherUsersSessions() throws Exception {
        WebSocketSession userA = mock(WebSocketSession.class);
        WebSocketSession userB = mock(WebSocketSession.class);
        when(userA.isOpen()).thenReturn(true);

        registry.register(1L, userA);
        registry.register(2L, userB);
        registry.sendToUser(1L, WsEnvelope.error("only for user 1"));

        verify(userA).sendMessage(any());
        verify(userB, never()).sendMessage(any());
    }

    @Test
    void sendToUserForAnUnregisteredUserIsANoOp() throws Exception {
        // Callers (ChatWebSocketHandler) push unconditionally rather than checking
        // hasLiveSession first - this is what makes that safe for an offline user.
        registry.sendToUser(99L, WsEnvelope.error("nobody's listening"));
        // No exception, and nothing to verify against - there's no session to have
        // received anything.
    }

    @Test
    void sendToSessionSkipsAClosedSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);

        registry.sendToSession(session, WsEnvelope.error("x"));

        verify(session, never()).sendMessage(any(WebSocketMessage.class));
    }
}
