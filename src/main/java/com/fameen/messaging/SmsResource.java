package com.fameen.messaging;

/** Envoi de SMS ({@code POST /sms/send}) — nécessite le scope {@code sms} de la clé API. */
public final class SmsResource {

    private final FameenMessaging client;

    SmsResource(FameenMessaging client) {
        this.client = client;
    }

    /**
     * Envoie un SMS.
     *
     * @throws IllegalArgumentException si {@code to}/{@code message} manquent ou si
     *                                  {@code to} est une adresse email
     * @throws FameenApiException       si l'API refuse l'envoi (solde, scope, format…)
     * @throws FameenConnectionException si l'API est injoignable après réessais
     */
    public MessageResource send(SendMessageParams params) {
        return client.sendOnChannel("/sms/send", Channel.SMS, params);
    }
}
