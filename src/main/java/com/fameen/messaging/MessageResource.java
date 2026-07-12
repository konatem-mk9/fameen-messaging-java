package com.fameen.messaging;

/**
 * Message tel que renvoyé par l'API (contenu {@code data} de l'enveloppe).
 * Les dates sont des chaînes ISO 8601. Champs absents → {@code null} / 0.
 *
 * @param sid            identifiant unique du message — à conserver pour le suivi
 * @param status         {@code queued|sending|sent|delivered|failed}
 * @param channel        {@code sms|whatsapp|email}
 * @param to             destinataire (E.164 ou adresse email)
 * @param from           expéditeur effectif (sender name SMS, numéro WhatsApp,
 *                       adresse email), ou {@code null}
 * @param body           contenu envoyé
 * @param segments       tranches de 160 caractères (SMS) ; 1 pour WhatsApp/email
 * @param credits        crédits consommés
 * @param error          message d'erreur si l'envoi a échoué, sinon {@code null}
 * @param externalId     identifiant du message chez l'opérateur, une fois envoyé
 * @param statusCallback URL de callback de statut fournie à l'envoi, ou {@code null}
 * @param createdAt      date de création (ISO 8601)
 * @param sentAt         date d'envoi (ISO 8601), ou {@code null}
 * @param deliveredAt    date de remise (ISO 8601), ou {@code null}
 */
public record MessageResource(
        String sid,
        String status,
        String channel,
        String to,
        String from,
        String body,
        int segments,
        int credits,
        String error,
        String externalId,
        String statusCallback,
        String createdAt,
        String sentAt,
        String deliveredAt) {
}
