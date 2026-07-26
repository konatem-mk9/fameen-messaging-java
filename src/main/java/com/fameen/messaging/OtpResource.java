package com.fameen.messaging;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Codes à usage unique (« Verify ») par SMS, WhatsApp ou email
 * ({@code /v1/otp/*}).
 *
 * <p>Le code est généré, stocké haché et vérifié <strong>côté serveur</strong> :
 * il ne transite jamais par votre application et n'apparaît dans aucune réponse.
 * Vous n'avez ni génération, ni stockage, ni expiration à gérer.
 *
 * <pre>{@code
 * VerificationResource v = fameen.otp().send(
 *         SendOtpParams.builder().to("+224620000000").channel(Channel.SMS).build());
 *
 * VerificationResource r = fameen.otp().verify(
 *         VerifyOtpParams.builder().verificationId(v.verificationId()).code("483920").build());
 *
 * if (r.isApproved()) {
 *     // utilisateur authentifié
 * }
 * }</pre>
 */
public final class OtpResource {

    private final FameenMessaging client;

    OtpResource(FameenMessaging client) {
        this.client = client;
    }

    /**
     * Génère un code et l'envoie sur le canal choisi.
     *
     * <p>Nécessite le scope du canal utilisé ({@code sms}, {@code whatsapp} ou
     * {@code email}) et consomme un crédit de ce canal, exactement comme un
     * message ordinaire.
     *
     * @throws IllegalArgumentException  si {@code to} manque, si le canal est
     *                                   incohérent avec le destinataire, ou si le
     *                                   gabarit ne contient pas <code>{{code}}</code>
     * @throws FameenApiException        si l'API refuse l'envoi (solde, scope…)
     * @throws FameenConnectionException si l'API est injoignable après réessais
     */
    public VerificationResource send(SendOtpParams params) {
        Objects.requireNonNull(params, "params");

        String to = params.to() == null ? "" : params.to().trim();
        if (to.isEmpty()) {
            throw new IllegalArgumentException("`to` est requis.");
        }
        Channel channel = params.channel();
        if (channel != null && channel != Channel.EMAIL && to.contains("@")) {
            throw new IllegalArgumentException(
                    "`to` ressemble à un email mais le canal demandé est \"" + channel.value() + "\".");
        }
        if (params.template() != null && !params.template().contains("{{code}}")) {
            throw new IllegalArgumentException("`template` doit contenir le marqueur {{code}}.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to);
        if (channel != null) {
            body.put("channel", channel.value());
        }
        if (params.codeLength() != null) {
            body.put("codeLength", params.codeLength());
        }
        if (params.ttlSeconds() != null) {
            body.put("ttlSeconds", params.ttlSeconds());
        }
        if (params.maxAttempts() != null) {
            body.put("maxAttempts", params.maxAttempts());
        }
        if (params.template() != null) {
            body.put("template", params.template());
        }
        if (params.subject() != null && !params.subject().isBlank()) {
            body.put("subject", params.subject());
        }
        if (params.statusCallback() != null && !params.statusCallback().isBlank()) {
            body.put("statusCallback", params.statusCallback());
        }

        return client.request("POST", "/otp/send", null, body,
                params.idempotencyKey(), VerificationResource.class);
    }

    /**
     * Contrôle le code saisi par l'utilisateur.
     *
     * <p>Ne lève <strong>pas</strong> d'exception sur un code erroné : la réponse
     * porte {@code status = "rejected"} et {@code reason}. Testez
     * {@link VerificationResource#isApproved()}.
     *
     * @throws IllegalArgumentException si le code manque, ou si ni
     *                                  {@code verificationId} ni {@code to} n'est fourni
     */
    public VerificationResource verify(VerifyOtpParams params) {
        Objects.requireNonNull(params, "params");

        String code = params.code() == null ? "" : params.code().trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("`code` est requis.");
        }
        String verificationId = params.verificationId() == null ? "" : params.verificationId().trim();
        String to = params.to() == null ? "" : params.to().trim();
        if (verificationId.isEmpty() && to.isEmpty()) {
            throw new IllegalArgumentException("Fournissez `verificationId` ou `to`.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        if (!verificationId.isEmpty()) {
            body.put("verificationId", verificationId);
        }
        if (!to.isEmpty()) {
            body.put("to", to);
        }
        if (params.channel() != null) {
            body.put("channel", params.channel().value());
        }

        return client.request("POST", "/otp/verify", null, body, null, VerificationResource.class);
    }

    /**
     * État courant d'une vérification (jamais le code).
     *
     * @throws IllegalArgumentException si l'identifiant est vide
     */
    public VerificationResource get(String verificationId) {
        String value = verificationId == null ? "" : verificationId.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("`verificationId` est requis.");
        }
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");

        return client.request("GET", "/otp/" + encoded, null, null, null, VerificationResource.class);
    }
}
