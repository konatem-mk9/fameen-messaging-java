package com.fameen.messaging;

/** Envoi d'emails ({@code POST /email/send}) — nécessite le scope {@code email}. */
public final class EmailResource {

    private final FameenMessaging client;

    EmailResource(FameenMessaging client) {
        this.client = client;
    }

    /**
     * Envoie un email ({@code to} = adresse email ; pensez à {@code subject}).
     *
     * @throws IllegalArgumentException si {@code to}/{@code message} manquent
     * @throws FameenApiException       si l'API refuse l'envoi (solde, scope, format…)
     * @throws FameenConnectionException si l'API est injoignable après réessais
     */
    public MessageResource send(SendMessageParams params) {
        return client.sendOnChannel("/email/send", Channel.EMAIL, params);
    }
}
