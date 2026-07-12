package com.fameen.messaging;

/**
 * Corps JSON reçu sur votre webhook de statut (en-tête info : {@code X-Fameen-Event}).
 * Construisez-le exclusivement via {@link Webhooks#constructEvent} pour garantir
 * la vérification de signature.
 *
 * @param event      {@code queued|sent|delivered|failed}
 * @param sid        identifiant du message concerné
 * @param status     statut courant du message
 * @param channel    {@code sms|whatsapp|email}
 * @param to         destinataire
 * @param from       expéditeur effectif, ou {@code null}
 * @param error      message d'erreur si échec, sinon {@code null}
 * @param externalId identifiant opérateur, ou {@code null}
 * @param timestamp  date d'émission du callback (ISO 8601)
 */
public record WebhookEvent(
        String event,
        String sid,
        String status,
        String channel,
        String to,
        String from,
        String error,
        String externalId,
        String timestamp) {
}
