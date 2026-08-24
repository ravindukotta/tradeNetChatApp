package com.zuhlke.tradenet.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuhlke.tradenet.chat.model.WsEnvelope;
import com.zuhlke.tradenet.chat.repository.UserRepository;
import com.zuhlke.tradenet.chat.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ConnectionRegistry connectionRegistry;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ConnectionRegistry connectionRegistry, MessageService messageService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    private Long userIdOf(WebSocketSession session) {
        return (Long) session.getAttributes().get(AuthHandshakeInterceptor.USER_ID_ATTRIBUTE);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            // Shouldn't happen - AuthHandshakeInterceptor rejects the upgrade before
            // this point - but fail closed rather than register a connection with no
            // known owner.
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        connectionRegistry.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            connectionRegistry.unregister(userId, session);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage msg){
        Long senderId = userIdOf(session);
        if(senderId == null){
            closeQuietly(session,CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        WsEnvelope envelope;
        try{
            envelope = objectMapper.readValue(msg.getPayload(), WsEnvelope.class);
        }catch(IOException e){
            connectionRegistry.sendToSession(session, WsEnvelope.error("Malformed message"));
            return;
        }

        if (envelope.type() == null) {
            connectionRegistry.sendToSession(session, WsEnvelope.error("Missing message type"));
            return;
        }
        switch (envelope.type()){
            case "send" ->{
                handleSend(session, senderId, envelope);
            } default -> connectionRegistry.sendToSession(session, WsEnvelope.error("Unknown type: " + envelope.type()));
        }
    }

    private void handleSend(WebSocketSession session, Long senderId, WsEnvelope envelope){
        Long recipientId = envelope.to();
        String clientMsgId = envelope.clientMsgId();
        if (recipientId == null || envelope.body() == null || clientMsgId == null) {
            connectionRegistry.sendToSession(session, WsEnvelope.error("send requires to, body, clientMsgId"));
            return;
        }
        if (!userRepository.existsById(recipientId)) {
            connectionRegistry.sendToSession(session, WsEnvelope.error("Unknown recipient: " + recipientId));
            return;
        }
        // We will save the message first because we dont want to loose any data
        // if the save failed after sending the msg to session msgs will be lost in a later conversation load
        MessageService.PersistResult result =
                messageService.persistAndGetId(senderId, recipientId, envelope.body(), clientMsgId);

        if (!result.wasDuplicate()) {
            WsEnvelope deliverEnvelope = new WsEnvelope(
                    "deliver", recipientId, senderId,
                    envelope.body(), clientMsgId, result.serverMessageId(),
                    result.createdAt().toEpochMilli(), null);
            connectionRegistry.sendToUser(recipientId, deliverEnvelope);
        }

        // Ack is sent unconditionally, even when the send was a duplicate - the sender
        // has no other way to know their message was accepted (we will render the msg in senders ui after the ack only
        // ), so skipping the ack on a retry would mean the
        // sender's own message never shows up in their UI.
        WsEnvelope ack = new WsEnvelope(
                "ack", null, senderId,
                null, clientMsgId, result.serverMessageId(),
                result.createdAt().toEpochMilli(), null);
        connectionRegistry.sendToSession(session, ack);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Failed to close session {} with status {}", session.getId(), status, e);
        }
    }
}
