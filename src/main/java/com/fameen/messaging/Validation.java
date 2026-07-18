package com.fameen.messaging;

/** Validations locales avant tout appel réseau (erreurs immédiates, meilleure DX). */
final class Validation {

    private Validation() {
    }

    /**
     * Vérifie les champs minimum d'un envoi.
     *
     * @param channel  canal explicite demandé, ou {@code null} si déduit côté serveur
     * @param hasMedia {@code true} si au moins une pièce jointe est fournie
     * @throws IllegalArgumentException si {@code to} manque, si {@code message} est vide
     *                                  sans média, si un média est envoyé en SMS, ou si
     *                                  {@code to} est un email alors que le canal demandé
     *                                  n'est pas {@link Channel#EMAIL}.
     */
    static void assertSendable(String to, String message, Channel channel, boolean hasMedia) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("`to` est requis (numéro E.164 ou adresse email).");
        }
        // Un message peut n'être qu'un média (légende facultative).
        if (!hasMedia && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("`message` est requis (ou fournissez un média).");
        }
        if (hasMedia && channel == Channel.SMS) {
            throw new IllegalArgumentException("Le canal SMS ne supporte pas les pièces jointes.");
        }
        if (channel != null && channel != Channel.EMAIL && to.contains("@")) {
            throw new IllegalArgumentException(
                    "`to` ressemble à une adresse email mais le canal demandé est \"" + channel + "\".");
        }
    }
}
