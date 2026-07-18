package com.fameen.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Ressource « Messages » unifiée (façon Twilio) : création, consultation, liste. */
public final class MessagesResource {

    private final FameenMessaging client;

    MessagesResource(FameenMessaging client) {
        this.client = client;
    }

    /**
     * Envoie un message — canal explicite ou déduit du destinataire
     * (« @ » dans {@code to} → email, sinon SMS).
     *
     * @throws IllegalArgumentException si {@code to}/{@code message} manquent, ou si
     *                                  {@code to} est un email alors que le canal
     *                                  demandé n'est pas {@link Channel#EMAIL}
     */
    public MessageResource create(CreateMessageParams params) {
        Objects.requireNonNull(params, "params");
        Validation.assertSendable(params.to(), params.message(), params.channel(), !params.attachments().isEmpty());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", params.to());
        body.put("message", params.message() != null ? params.message() : "");
        if (params.channel() != null) {
            body.put("channel", params.channel().value());
        }
        if (params.subject() != null && !params.subject().isBlank()) {
            body.put("subject", params.subject());
        }
        if (params.statusCallback() != null && !params.statusCallback().isBlank()) {
            body.put("statusCallback", params.statusCallback());
        }
        FameenMessaging.putAttachments(body, params.attachments());
        return client.request("POST", "/messages", null, body, params.idempotencyKey(), MessageResource.class);
    }

    /**
     * Statut courant d'un message.
     *
     * @throws IllegalArgumentException si {@code sid} est absent ou vide
     */
    public MessageResource get(String sid) {
        if (sid == null || sid.isBlank()) {
            throw new IllegalArgumentException("`sid` est requis.");
        }
        return client.request("GET", "/messages/" + FameenMessaging.encode(sid.trim()),
                null, null, null, MessageResource.class);
    }

    /** Liste paginée sans filtre (30 messages par page). */
    public MessageList list() {
        return list(ListMessagesParams.builder().build());
    }

    /** Liste paginée (filtres canal / statut / destinataire). */
    public MessageList list(ListMessagesParams params) {
        Objects.requireNonNull(params, "params");
        Map<String, String> query = new LinkedHashMap<>();
        if (params.channel() != null) {
            query.put("channel", params.channel().value());
        }
        putIfPresent(query, "status", params.status());
        putIfPresent(query, "to", params.to());
        if (params.page() != null) {
            query.put("page", String.valueOf(params.page()));
        }
        if (params.limit() != null) {
            query.put("limit", String.valueOf(params.limit()));
        }
        return client.request("GET", "/messages", query, null, null, MessageList.class);
    }

    /**
     * Historique brut sans filtre.
     *
     * @deprecated endpoint historique aux lignes brutes — préférez {@link #list()}.
     */
    @Deprecated
    public HistoryPage history() {
        return history(HistoryParams.builder().build());
    }

    /**
     * Historique brut ({@code GET /messages/history}).
     *
     * @deprecated endpoint historique aux lignes brutes — préférez {@link #list(ListMessagesParams)}.
     */
    @Deprecated
    public HistoryPage history(HistoryParams params) {
        Objects.requireNonNull(params, "params");
        Map<String, String> query = new LinkedHashMap<>();
        if (params.channel() != null) {
            query.put("channel", params.channel().value());
        }
        putIfPresent(query, "status", params.status());
        if (params.page() != null) {
            query.put("page", String.valueOf(params.page()));
        }
        return client.request("GET", "/messages/history", query, null, null, HistoryPage.class);
    }

    private static void putIfPresent(Map<String, String> query, String key, String value) {
        if (value != null && !value.isBlank()) {
            query.put(key, value);
        }
    }
}
