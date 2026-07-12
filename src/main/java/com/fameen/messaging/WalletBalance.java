package com.fameen.messaging;

/**
 * Soldes et mode de facturation ({@code GET /wallet/balance}).
 *
 * @param smsCredits   crédits SMS restants
 * @param waCredits    crédits WhatsApp restants
 * @param emailCredits crédits email restants
 * @param billing      mode de facturation du compte
 */
public record WalletBalance(
        int smsCredits,
        int waCredits,
        int emailCredits,
        Billing billing) {

    /**
     * Mode de facturation.
     *
     * @param mode            {@code prepaid} ou {@code consumption}
     * @param postpaid        {@code true} = facturation à la consommation :
     *                        l'envoi n'est pas limité par le solde
     * @param prepaidRequired {@code true} = solde prépayé obligatoire pour envoyer
     * @param sendingBlocked  {@code true} = compte bloqué (période de consommation expirée)
     */
    public record Billing(
            String mode,
            boolean postpaid,
            boolean prepaidRequired,
            boolean sendingBlocked) {
    }
}
