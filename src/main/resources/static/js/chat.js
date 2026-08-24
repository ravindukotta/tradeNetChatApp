(function () {
    "use strict";

    var main = document.getElementById("main");
    if (!main || !main.dataset.activeWith) {
        // No conversation selected on this page load - nothing to wire up. Scope note:
        // the MVP only maintains a live connection for the currently open conversation,
        // not a background socket for unread-message notifications across all users.
        return;
    }

    var myId = Number(main.dataset.myId);
    var otherUserId = Number(main.dataset.activeWith);

    var messagesEl = document.getElementById("messages");
    var connStatusEl = document.getElementById("connStatus");
    var composerEl = document.getElementById("composer");
    var inputEl = document.getElementById("messageInput");

    var ws = null;
    var intentionalClose = false;
    // Body text for a send that's been fired but not yet acked, keyed by clientMsgId.
    // No optimistic rendering (documented scope cut): the sender's own message is
    // only rendered once the server acks it, not immediately on submit.
    var pendingBodies = {};

    function setStatus(text, cssClass) {
        connStatusEl.textContent = text;
        connStatusEl.className = cssClass || "";
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function renderMessage(msg) {
        // msg: {senderId, body, createdAt, clientMsgId}
        var li = document.createElement("li");
        var isMine = msg.senderId === myId;
        li.className = isMine ? "me" : "them";
        if (msg.clientMsgId) {
            li.dataset.clientMsgId = msg.clientMsgId;
        }

        var bubble = document.createElement("div");
        bubble.className = "bubble";
        // textContent, never innerHTML - message bodies are untrusted user input and
        // must not be interpreted as markup (XSS). See openspec design.md risk list.
        bubble.textContent = msg.body;
        li.appendChild(bubble);

        var meta = document.createElement("div");
        meta.className = "meta";
        meta.textContent = msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString() : "";
        li.appendChild(meta);

        messagesEl.appendChild(li);
        scrollToBottom();
        return li;
    }

    function loadHistory() {
        return fetch("/api/conversations/" + otherUserId + "/messages", {credentials: "same-origin"})
            .then(function (res) {
                if (!res.ok) {
                    throw new Error("Failed to load history: " + res.status);
                }
                return res.json();
            })
            .then(function (history) {
                history.forEach(function (m) {
                    renderMessage({
                        senderId: m.senderId,
                        body: m.body,
                        createdAt: m.createdAt
                    });
                });
            });
    }

    function connect() {
        var protocol = location.protocol === "https:" ? "wss:" : "ws:";
        ws = new WebSocket(protocol + "//" + location.host + "/ws/chat");

        ws.onopen = function () {
            setStatus("Connected", "connected");
            // No reconnect-backfill: a message sent while this socket was down is only
            // picked up on the next page load (loadHistory), not retroactively pushed
            // here. Documented scope cut.
        };

        ws.onmessage = function (event) {
            var envelope = JSON.parse(event.data);
            handleEnvelope(envelope);
        };

        ws.onclose = function () {
            if (intentionalClose) {
                return;
            }
            setStatus("Disconnected - reconnecting...", "disconnected");
            // Fixed delay, not exponential backoff (documented scope cut).
            setTimeout(connect, 2000);
        };

        ws.onerror = function () {
            // onclose fires after onerror for WebSocket; reconnect is handled there.
        };
    }

    function handleEnvelope(envelope) {
        switch (envelope.type) {
            case "deliver":
                if (envelope.from === otherUserId || envelope.to === otherUserId) {
                    renderMessage({
                        senderId: envelope.from,
                        body: envelope.body,
                        createdAt: envelope.createdAt,
                        clientMsgId: envelope.clientMsgId
                    });
                }
                // A "deliver" for a different conversation (someone else messaging us
                // while this one is open) is intentionally dropped - no unread-badge
                // system in the MVP, documented scope cut.
                break;
            case "ack":
                var body = pendingBodies[envelope.clientMsgId];
                delete pendingBodies[envelope.clientMsgId];
                renderMessage({
                    senderId: myId,
                    body: body,
                    createdAt: envelope.createdAt,
                    clientMsgId: envelope.clientMsgId
                });
                break;
            case "error":
                console.error("Server error:", envelope.error);
                break;
            default:
                console.warn("Unknown envelope type:", envelope.type);
        }
    }

    function generateClientMsgId() {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID();
        }
        return "cid-" + Date.now() + "-" + Math.random().toString(16).slice(2);
    }

    composerEl.addEventListener("submit", function (event) {
        event.preventDefault();
        var body = inputEl.value.trim();
        if (!body || !ws || ws.readyState !== WebSocket.OPEN) {
            return;
        }
        var clientMsgId = generateClientMsgId();
        pendingBodies[clientMsgId] = body;
        ws.send(JSON.stringify({
            type: "send",
            to: otherUserId,
            body: body,
            clientMsgId: clientMsgId
        }));
        inputEl.value = "";
    });

    loadHistory().finally(connect);
})();
