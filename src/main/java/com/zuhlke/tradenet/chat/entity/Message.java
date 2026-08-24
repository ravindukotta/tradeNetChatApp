package com.zuhlke.tradenet.chat.entity;


import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_sender_client_msg", columnNames = {"sender_id", "client_msg_id"}),
        indexes = {
                @Index(name = "idx_messages_sender_recipient", columnList = "sender_id, recipient_id"),
                @Index(name = "idx_messages_recipient_sender", columnList = "recipient_id, sender_id")
        })
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "client_msg_id", nullable = false)
    private String clientMsgId;

    //defines msg order
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Message() {
        // JPA
    }

    public Message(Long senderId, Long recipientId, String body, String clientMsgId) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.body = body;
        this.clientMsgId = clientMsgId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
