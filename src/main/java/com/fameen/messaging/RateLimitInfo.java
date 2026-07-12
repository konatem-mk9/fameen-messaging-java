package com.fameen.messaging;

/**
 * Compteurs de limitation de débit lus sur la dernière réponse (60 req/min/clé).
 *
 * @param limit     plafond de requêtes par fenêtre ({@code X-RateLimit-Limit})
 * @param remaining requêtes restantes dans la fenêtre ({@code X-RateLimit-Remaining})
 * @param reset     fin de fenêtre, epoch en secondes ({@code X-RateLimit-Reset})
 */
public record RateLimitInfo(long limit, long remaining, long reset) {
}
