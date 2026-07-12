package com.fameen.messaging;

/** Envoi WhatsApp ({@code POST /whatsapp/send}) — nécessite le scope {@code whatsapp}. */
public final class WhatsappResource {

    private final FameenMessaging client;

    WhatsappResource(FameenMessaging client) {
        this.client = client;
    }

    /**
     * Envoie un message WhatsApp.
     *
     * @throws IllegalArgumentException si {@code to}/{@code message} manquent ou si
     *                                  {@code to} est une adresse email
     * @throws FameenApiException       si l'API refuse l'envoi (solde, scope, format…)
     * @throws FameenConnectionException si l'API est injoignable après réessais
     */
    public MessageResource send(SendMessageParams params) {
        return client.sendOnChannel("/whatsapp/send", Channel.WHATSAPP, params);
    }
}
