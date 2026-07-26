package com.fameen.messaging;

/**
 * Vérification par code à usage unique ({@code /v1/otp/*}).
 *
 * <p>Ne contient <strong>jamais</strong> le code : celui-ci est généré côté
 * serveur et n'est transmis qu'au destinataire, par le canal choisi.
 *
 * @param verificationId    identifiant à conserver pour la vérification
 * @param status            {@code pending | approved | rejected | expired | failed | canceled}
 * @param channel           canal de livraison utilisé
 * @param to                destinataire
 * @param attempts          tentatives de vérification déjà consommées
 * @param maxAttempts       plafond de tentatives
 * @param attemptsRemaining tentatives restantes avant fermeture définitive
 * @param expiresAt         date d'expiration ISO 8601
 * @param createdAt         date de création ISO 8601
 * @param messageSid        SID du message porteur du code (suivi, facturation)
 * @param reason            renseigné uniquement si {@code status} vaut {@code rejected} :
 *                          {@code invalid_code}, {@code expired} ou {@code max_attempts}
 */
public record VerificationResource(
        String verificationId,
        String status,
        String channel,
        String to,
        int attempts,
        int maxAttempts,
        int attemptsRemaining,
        String expiresAt,
        String createdAt,
        String messageSid,
        String reason) {

    /** {@code true} si le code a été validé. */
    public boolean isApproved() {
        return "approved".equals(status);
    }
}
