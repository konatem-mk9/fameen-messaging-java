package com.fameen.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client officiel de l'API Fameen Messaging (SMS, WhatsApp, Email).
 *
 * <pre>{@code
 * FameenMessaging fameen = FameenMessaging.builder()
 *     .apiKey(System.getenv("FAMEEN_API_KEY"))
 *     .build();
 *
 * MessageResource msg = fameen.sms().send(SendMessageParams.builder()
 *     .to("+224620000000")
 *     .message("Bonjour {prenom} !")
 *     .build());
 * }</pre>
 *
 * <p>Le client est immuable et sûr pour un usage concurrent : créez-en un par
 * application et réutilisez-le. La clé API ({@code fam_…}) ne doit jamais être
 * embarquée côté client (mobile, navigateur).</p>
 */
public final class FameenMessaging {

    /** Version du SDK (reprise dans l'en-tête {@code User-Agent}). */
    public static final String VERSION = "1.0.2";

    /** URL de base par défaut de l'API. */
    public static final String DEFAULT_BASE_URL = "https://fameenbusiness.com/api/v1";

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final long retryBaseMillis;
    private final HttpTransport transport;
    private final Sleeper sleeper;
    private final ObjectMapper mapper;

    private final SmsResource sms;
    private final WhatsappResource whatsapp;
    private final EmailResource email;
    private final MessagesResource messages;
    private final WalletResource wallet;
    private final OtpResource otp;

    /** Compteurs {@code X-RateLimit-*} de la dernière réponse qui les fournissait. */
    private volatile RateLimitInfo lastRateLimit;

    private FameenMessaging(Builder builder) {
        if (builder.apiKey == null || builder.apiKey.isBlank()) {
            throw new IllegalArgumentException("FameenMessaging : `apiKey` est requis (clé \"fam_…\").");
        }
        this.apiKey = builder.apiKey.trim();

        String base = (builder.baseUrl == null || builder.baseUrl.isBlank())
                ? DEFAULT_BASE_URL
                : builder.baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;

        this.timeout = builder.timeout == null ? Duration.ofSeconds(30) : builder.timeout;
        this.maxRetries = Math.max(0, builder.maxRetries);
        long baseMillis = builder.retryBase == null ? 500L : builder.retryBase.toMillis();
        this.retryBaseMillis = Math.max(1L, baseMillis);
        this.transport = builder.transport == null ? new JdkHttpTransport() : builder.transport;
        this.sleeper = builder.sleeper == null ? Thread::sleep : builder.sleeper;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.sms = new SmsResource(this);
        this.whatsapp = new WhatsappResource(this);
        this.email = new EmailResource(this);
        this.messages = new MessagesResource(this);
        this.wallet = new WalletResource(this);
        this.otp = new OtpResource(this);
    }

    /** Point d'entrée : {@code FameenMessaging.builder().apiKey("fam_…").build()}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Envoi de SMS ({@code POST /sms/send}). */
    public SmsResource sms() {
        return sms;
    }

    /** Envoi WhatsApp ({@code POST /whatsapp/send}). */
    public WhatsappResource whatsapp() {
        return whatsapp;
    }

    /** Envoi d'emails ({@code POST /email/send}). */
    public EmailResource email() {
        return email;
    }

    /** Ressource « Messages » unifiée : création, consultation, liste. */
    public MessagesResource messages() {
        return messages;
    }

    /** Soldes de crédits et mode de facturation. */
    public WalletResource wallet() {
        return wallet;
    }

    /** Codes de verification a usage unique : {@code fameen.otp().send(...)}. */
    public OtpResource otp() {
        return otp;
    }

    /**
     * Compteurs de limitation de débit ({@code X-RateLimit-*}) lus sur la
     * dernière réponse qui les fournissait. Jamais écrasé par une réponse
     * qui ne les fournit pas.
     */
    public Optional<RateLimitInfo> lastRateLimit() {
        return Optional.ofNullable(lastRateLimit);
    }

    // ------------------------------------------------------------------
    // Cœur HTTP interne (package-private : utilisé par les ressources)
    // ------------------------------------------------------------------

    /**
     * Envoi sur un canal dédié : validation locale puis {@code POST path}.
     * Partagé par {@link SmsResource}, {@link WhatsappResource} et {@link EmailResource}.
     */
    MessageResource sendOnChannel(String path, Channel channel, SendMessageParams params) {
        Objects.requireNonNull(params, "params");
        Validation.assertSendable(params.to(), params.message(), channel, !params.attachments().isEmpty());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", params.to());
        body.put("message", params.message() != null ? params.message() : "");
        if (params.subject() != null && !params.subject().isBlank()) {
            body.put("subject", params.subject());
        }
        if (params.statusCallback() != null && !params.statusCallback().isBlank()) {
            body.put("statusCallback", params.statusCallback());
        }
        putAttachments(body, params.attachments());
        return request("POST", path, null, body, params.idempotencyKey(), MessageResource.class);
    }

    /** Ajoute les pièces jointes sérialisées au corps, si présentes. */
    static void putAttachments(Map<String, Object> body, java.util.List<Attachment> attachments) {
        if (attachments != null && !attachments.isEmpty()) {
            java.util.List<Map<String, Object>> serialized = new java.util.ArrayList<>();
            for (Attachment a : attachments) {
                serialized.add(a.toMap());
            }
            body.put("attachments", serialized);
        }
    }

    /**
     * Exécute une requête avec la sémantique commune du SDK : enveloppe
     * {@code {success,data}} déballée, erreurs typées, réessais automatiques
     * (réseau, 429 avec {@code Retry-After}, 5xx si GET ou clé d'idempotence).
     */
    <T> T request(String method, String path, Map<String, String> query,
                  Object body, String idempotencyKey, Class<T> type) {
        URI uri = buildUri(path, query);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "fameen-messaging-java/" + VERSION);

        byte[] bodyBytes = null;
        if (body != null) {
            headers.put("Content-Type", "application/json");
            try {
                bodyBytes = mapper.writeValueAsBytes(body);
            } catch (JsonProcessingException e) {
                throw new FameenException("Impossible de sérialiser le corps de la requête en JSON.", e);
            }
        }
        boolean hasIdempotencyKey = idempotencyKey != null && !idempotencyKey.isBlank();
        if (hasIdempotencyKey) {
            headers.put("Idempotency-Key", idempotencyKey.trim());
        }
        Map<String, String> headerMap = Map.copyOf(headers);

        IOException lastConnectionError = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpTransport.Response res;
            try {
                res = transport.execute(new HttpTransport.Request(method, uri, headerMap, bodyBytes, timeout));
            } catch (IOException e) {
                // Échec réseau : la requête n'a (très probablement) pas été traitée.
                lastConnectionError = e;
                if (attempt < maxRetries) {
                    sleepFor(backoffMillis(attempt));
                    continue;
                }
                throw new FameenConnectionException(
                        "Impossible de joindre l'API Fameen (" + uri.getHost() + ") : " + e.getMessage(), e);
            }

            RateLimitInfo rateLimit = readRateLimit(res);
            if (rateLimit != null) {
                this.lastRateLimit = rateLimit;
            }

            JsonNode parsed = parseJson(res.body());
            int status = res.statusCode();

            if (status >= 200 && status < 300) {
                JsonNode payload = parsed;
                // Enveloppe standard { success, data } → on renvoie `data` directement.
                if (parsed != null && parsed.isObject() && parsed.has("success") && parsed.has("data")) {
                    payload = parsed.get("data");
                }
                if (payload == null || payload.isNull()) {
                    return null;
                }
                try {
                    return mapper.treeToValue(payload, type);
                } catch (JsonProcessingException e) {
                    throw new FameenException(
                            "Réponse API illisible pour " + method + " " + uri.getPath() + ".", e);
                }
            }

            String code = null;
            String message = null;
            if (parsed != null && parsed.isObject()) {
                JsonNode err = parsed.get("error");
                if (err != null && err.isObject()) {
                    JsonNode c = err.get("code");
                    if (c != null && c.isTextual()) {
                        code = c.asText();
                    }
                    JsonNode m = err.get("message");
                    if (m != null && m.isTextual()) {
                        message = m.asText();
                    }
                }
                if (message == null) {
                    JsonNode m = parsed.get("message");
                    if (m != null && m.isTextual()) {
                        message = m.asText();
                    }
                }
            }
            if (code == null || code.isBlank()) {
                code = codeFromStatus(status);
            }
            if (message == null || message.isBlank()) {
                message = "Erreur HTTP " + status + " sur " + method + " " + uri.getPath();
            }

            Integer retryAfter = readRetryAfter(res);

            boolean retriable = status == 429 || status >= 500;
            // POST non idempotent : un 5xx a pu être traité côté serveur → pas de réessai.
            boolean safeToRetry = "GET".equals(method) || hasIdempotencyKey || status == 429;

            if (retriable && safeToRetry && attempt < maxRetries) {
                sleepFor(retryAfter != null ? retryAfter * 1000L : backoffMillis(attempt));
                continue;
            }

            throw new FameenApiException(status, code, message, this.lastRateLimit, retryAfter);
        }

        // Inatteignable (la boucle jette toujours), mais javac l'exige.
        throw new FameenConnectionException("Réessais épuisés.", lastConnectionError);
    }

    private URI buildUri(String path, Map<String, String> query) {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (query != null && !query.isEmpty()) {
            StringBuilder qs = new StringBuilder();
            for (Map.Entry<String, String> entry : query.entrySet()) {
                String value = entry.getValue();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                if (qs.length() > 0) {
                    qs.append('&');
                }
                qs.append(encode(entry.getKey())).append('=').append(encode(value));
            }
            if (qs.length() > 0) {
                url.append('?').append(qs);
            }
        }
        return URI.create(url.toString());
    }

    /** Encodage URL (UTF-8), espaces en {@code %20}. */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String codeFromStatus(int status) {
        return switch (status) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 402 -> "insufficient_credits";
            case 403 -> "channel_not_allowed";
            case 404 -> "not_found";
            case 429 -> "rate_limited";
            default -> status >= 500 ? "internal_error" : "unknown_error";
        };
    }

    private JsonNode parseJson(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (IOException e) {
            return null;
        }
    }

    private static Long headerLong(HttpTransport.Response res, String name) {
        Optional<String> value = res.header(name);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.get().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RateLimitInfo readRateLimit(HttpTransport.Response res) {
        Long limit = headerLong(res, "X-RateLimit-Limit");
        Long remaining = headerLong(res, "X-RateLimit-Remaining");
        Long reset = headerLong(res, "X-RateLimit-Reset");
        if (limit != null && remaining != null && reset != null && limit > 0) {
            return new RateLimitInfo(limit, remaining, reset);
        }
        return null;
    }

    private Integer readRetryAfter(HttpTransport.Response res) {
        Long value = headerLong(res, "Retry-After");
        if (value == null || value < 0) {
            return null;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    /** Backoff exponentiel : {@code base × 2^tentative + jitter(0..base)}. */
    private long backoffMillis(int attempt) {
        long base = retryBaseMillis * (1L << attempt);
        return base + ThreadLocalRandom.current().nextLong(retryBaseMillis);
    }

    private void sleepFor(long millis) {
        try {
            sleeper.sleep(Math.max(0L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FameenConnectionException("Attente de réessai interrompue.", e);
        }
    }

    /** Horloge d'attente injectable — package-private, réservée aux tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /**
     * Constructeur fluide de {@link FameenMessaging}.
     * Seule {@link #apiKey(String)} est obligatoire.
     */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout;
        private int maxRetries = 2;
        private Duration retryBase;
        private HttpTransport transport;
        private Sleeper sleeper;

        private Builder() {
        }

        /** Clé API du compte ({@code fam_…}) — obligatoire, jamais côté client. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** URL de base. Défaut : {@value FameenMessaging#DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Timeout par tentative. Défaut : 30 secondes. */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /**
         * Nombre de réessais automatiques (défaut : 2) sur erreur réseau, 429 et 5xx.
         * Un POST sans clé d'idempotence n'est réessayé que sur 429 (jamais traité) ;
         * fournissez {@code idempotencyKey} pour rendre tous les réessais sûrs.
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Base du backoff exponentiel. Défaut : 500 ms. Surtout utile en test. */
        public Builder retryBase(Duration retryBase) {
            this.retryBase = Objects.requireNonNull(retryBase, "retryBase");
            return this;
        }

        /** Transport HTTP custom (tests, proxys). Défaut : {@link JdkHttpTransport}. */
        public Builder transport(HttpTransport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
            return this;
        }

        /** Attente injectable entre réessais — package-private, tests uniquement. */
        Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        /**
         * Construit le client.
         *
         * @throws IllegalArgumentException si {@code apiKey} est absent ou vide.
         */
        public FameenMessaging build() {
            return new FameenMessaging(this);
        }
    }
}
