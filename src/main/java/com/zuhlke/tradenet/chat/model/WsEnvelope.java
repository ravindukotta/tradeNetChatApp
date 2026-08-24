package com.zuhlke.tradenet.chat.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsEnvelope(
    String type,
    Long to,
    Long from,
    String body,
    String clientMsgId,
    Long serverMsgId,
    Long createdAt,
    String error
)
{
    public static WsEnvelope error(String message) {
        return new WsEnvelope("error", null, null, null, null, null, null, message);
    }
}
