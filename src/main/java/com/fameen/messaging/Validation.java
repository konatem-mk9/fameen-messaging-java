package com.fameen.messaging;

/** Validations locales avant tout appel réseau (erreurs immédiates, meilleure DX). */
final class Validation {

    private Validation() {
    }

    /**
     * Vérifie les champs minimum d'un envoi.
     *
     * @param channel canal explicite demandé, ou {@code null} si déduit côté serveur
     * @throws IllegalArgumentException si {@code to}/{@code message} manquent, ou si
     *                                  {@code to} est un email alors que le canal
     *                                  demandé n'est pas {@link Channel#EMAIL}.
     */
    static void assertSendable(String to, String message, Channel channel) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("`to` est requis (numéro E.164 ou adresse email).");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("`message` est requis et ne peut pas être vide.");
        }
        if (channel != null && channel != Channel.EMAIL && to.contains("@")) {
            throw new IllegalArgumentException(
                    "`to` ressemble à une adresse email mais le canal demandé est \"" + channel + "\".");
        }
    }
}
