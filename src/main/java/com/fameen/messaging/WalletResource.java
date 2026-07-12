package com.fameen.messaging;

/** Portefeuille de crédits ({@code GET /wallet/balance}). */
public final class WalletResource {

    private final FameenMessaging client;

    WalletResource(FameenMessaging client) {
        this.client = client;
    }

    /**
     * Soldes SMS / WhatsApp / Email et mode de facturation.
     *
     * @throws FameenApiException        si l'API renvoie une erreur
     * @throws FameenConnectionException si l'API est injoignable après réessais
     */
    public WalletBalance balance() {
        return client.request("GET", "/wallet/balance", null, null, null, WalletBalance.class);
    }
}
