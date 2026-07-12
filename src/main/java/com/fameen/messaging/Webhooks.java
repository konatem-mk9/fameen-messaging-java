package com.fameen.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Vérification des webhooks Fameen (en-tête {@code X-Fameen-Signature}).
 *
 * <p>La signature est le HMAC-SHA256 <b>hexadécimal</b> du <b>corps brut</b> de la
 * requête (octets reçus, avant tout parsing JSON) calculé avec le secret
 * {@code whsec_…} du compte. Un re-sérialisage JSON ne produit pas forcément
 * les mêmes octets : lisez toujours le corps brut
 * (Spring : {@code @RequestBody byte[]}).</p>
 */
public final class Webhooks {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Webhooks() {
    }

    /**
     * Vérifie la signature HMAC-SHA256 d'un webhook, en temps constant
     * ({@link MessageDigest#isEqual}).
     *
     * @param payload   corps brut de la requête (octets reçus)
     * @param signature valeur de l'en-tête {@code X-Fameen-Signature}
     *                  ({@code null} accepté → {@code false})
     * @param secret    secret {@code whsec_…} du compte
     * @return {@code true} si la signature correspond
     * @throws IllegalArgumentException si {@code secret} est absent ou vide
     */
    public static boolean verifySignature(byte[] payload, String signature, String secret) {
        Objects.requireNonNull(payload, "payload");
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("`secret` est requis (secret \"whsec_…\" du compte).");
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = HexFormat.of().formatHex(hmacSha256(secret, payload))
                .getBytes(StandardCharsets.UTF_8);
        byte[] provided = signature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    /** Variante {@link String} de {@link #verifySignature(byte[], String, String)} (UTF-8). */
    public static boolean verifySignature(String payload, String signature, String secret) {
        Objects.requireNonNull(payload, "payload");
        return verifySignature(payload.getBytes(StandardCharsets.UTF_8), signature, secret);
    }

    /**
     * Vérifie la signature PUIS parse l'événement — à appeler dans votre handler
     * de webhook. En cas d'échec, répondez 401 et ne traitez rien.
     *
     * @param payload   corps brut de la requête (octets reçus)
     * @param signature valeur de l'en-tête {@code X-Fameen-Signature}
     * @param secret    secret {@code whsec_…} du compte
     * @throws WebhookVerificationException si la signature est invalide ou le corps illisible
     * @throws IllegalArgumentException     si {@code secret} est absent ou vide
     */
    public static WebhookEvent constructEvent(byte[] payload, String signature, String secret) {
        if (!verifySignature(payload, signature, secret)) {
            throw new WebhookVerificationException("Signature X-Fameen-Signature invalide — événement rejeté.");
        }
        try {
            return MAPPER.readValue(payload, WebhookEvent.class);
        } catch (IOException e) {
            throw new WebhookVerificationException("Corps de webhook illisible (JSON invalide).", e);
        }
    }

    /** Variante {@link String} de {@link #constructEvent(byte[], String, String)} (UTF-8). */
    public static WebhookEvent constructEvent(String payload, String signature, String secret) {
        Objects.requireNonNull(payload, "payload");
        return constructEvent(payload.getBytes(StandardCharsets.UTF_8), signature, secret);
    }

    private static byte[] hmacSha256(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new FameenException("HMAC-SHA256 indisponible dans ce runtime.", e);
        }
    }
}
