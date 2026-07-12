package com.fameen.messaging;

import java.util.Optional;

/**
 * Erreur renvoyée par l'API (réponse HTTP non-2xx).
 *
 * <p>{@link #code()} reprend {@code error.code} du corps de la réponse
 * ({@code unauthorized}, {@code insufficient_credits}, {@code channel_not_allowed},
 * {@code rate_limited}, {@code not_found}, …). Si le corps est illisible, un code
 * de repli est déduit du statut HTTP.</p>
 */
public class FameenApiException extends FameenException {

    private final int status;
    private final String code;
    private final RateLimitInfo rateLimit;
    private final Integer retryAfter;

    public FameenApiException(int status, String code, String message,
                              RateLimitInfo rateLimit, Integer retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.rateLimit = rateLimit;
        this.retryAfter = retryAfter;
    }

    /** Statut HTTP (401, 402, 403, 404, 429, 500…). */
    public int status() {
        return status;
    }

    /** Code d'erreur stable de l'API ({@code error.code}). */
    public String code() {
        return code;
    }

    /** Secondes à attendre avant de réessayer (en-tête {@code Retry-After}), si fourni. */
    public Optional<Integer> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** Compteurs {@code X-RateLimit-*} connus au moment de l'erreur (surtout sur 429). */
    public Optional<RateLimitInfo> rateLimit() {
        return Optional.ofNullable(rateLimit);
    }
}
