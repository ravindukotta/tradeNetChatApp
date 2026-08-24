package com.zuhlke.tradenet.chat.websocket;

import com.zuhlke.tradenet.chat.model.WsEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    private final Map<Long, WebSocketSession> userSessionMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;


    public ConnectionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long userId, WebSocketSession session){
        WebSocketSession previous = userSessionMap.put(userId,session);

        if(previous != null && previous != session){
            try{
                previous.close(CloseStatus.NORMAL.withReason("replaced"));
            }catch (Exception e){
                // session is broken no op
            }
        }
    }

    public void unregister(Long userId , WebSocketSession webSocketSession){
        userSessionMap.computeIfPresent(userId, (id, current) -> current == webSocketSession ? null : current);
    }

    public boolean hasLiveSession(Long userId){
        return userSessionMap.containsKey(userId);
    }

    public void sendToUser(Long userId, WsEnvelope envelope) {
        WebSocketSession session = userSessionMap.get(userId);
        if (session != null) {
            sendToSession(session, envelope);
        }
    }

    public void sendToSession(WebSocketSession session, WsEnvelope envelope){
        String payload;
        try{
            payload = objectMapper.writeValueAsString(envelope);
        }catch (IOException e){
            log.error("Failed to serialize WS envelope type={}", envelope.type(), e);
            return;
        }

        // sendMessage isn't thread-safe for concurrent calls on the same session; locking
        // on the session itself (not one shared lock) keeps unrelated sessions independent.
        synchronized (session){
            if(!session.isOpen()){
                return;
            }
            try{
                session.sendMessage(new TextMessage(payload));
            }catch (IOException e) {
                // Best-effort: a session that just died will be cleaned up by
                // afterConnectionClosed; nothing more to do here.
                log.debug("Send failed for session {}", session.getId());
            }
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // Closing an already-broken session is a no-op we don't need to react to.
        }
    }
}
