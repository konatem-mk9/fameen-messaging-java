package com.fameen.messaging;

/**
 * Paramètres d'un envoi de code de vérification ({@code POST /otp/send}).
 *
 * <pre>{@code
 * SendOtpParams.builder()
 *     .to("+224620000000")
 *     .channel(Channel.SMS)
 *     .ttlSeconds(600)
 *     .build();
 * }</pre>
 *
 * <p>Seul {@code to} est obligatoire : sans canal explicite, il est déduit du
 * destinataire (email si « @ », sinon SMS), et les autres réglages retombent sur
 * ceux du compte.
 */
public final class SendOtpParams {

    private final String to;
    private final Channel channel;
    private final Integer codeLength;
    private final Integer ttlSeconds;
    private final Integer maxAttempts;
    private final String template;
    private final String subject;
    private final String statusCallback;
    private final String idempotencyKey;

    private SendOtpParams(Builder builder) {
        this.to = builder.to;
        this.channel = builder.channel;
        this.codeLength = builder.codeLength;
        this.ttlSeconds = builder.ttlSeconds;
        this.maxAttempts = builder.maxAttempts;
        this.template = builder.template;
        this.subject = builder.subject;
        this.statusCallback = builder.statusCallback;
        this.idempotencyKey = builder.idempotencyKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Destinataire : numéro E.164 ({@code +224…}) ou adresse email (canal email). */
    public String to() {
        return to;
    }

    /** Canal de livraison, ou {@code null} pour le déduire du destinataire. */
    public Channel channel() {
        return channel;
    }

    /** Longueur du code, 4 à 8 chiffres, ou {@code null} pour le réglage du compte. */
    public Integer codeLength() {
        return codeLength;
    }

    /** Validité en secondes, 60 à 3600, ou {@code null} pour le réglage du compte. */
    public Integer ttlSeconds() {
        return ttlSeconds;
    }

    /** Tentatives autorisées, 1 à 10, ou {@code null} pour le réglage du compte. */
    public Integer maxAttempts() {
        return maxAttempts;
    }

    /** Gabarit ponctuel ; doit contenir le marqueur <code>{{code}}</code>. */
    public String template() {
        return template;
    }

    /** Objet du message (canal email). */
    public String subject() {
        return subject;
    }

    /** URL HTTPS notifiée du statut du message porteur. */
    public String statusCallback() {
        return statusCallback;
    }

    /** Clé d'idempotence : un réessai réseau n'enverra pas un second code. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Constructeur fluide de {@link SendOtpParams}. */
    public static final class Builder {

        private String to;
        private Channel channel;
        private Integer codeLength;
        private Integer ttlSeconds;
        private Integer maxAttempts;
        private String template;
        private String subject;
        private String statusCallback;
        private String idempotencyKey;

        private Builder() {
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder channel(Channel channel) {
            this.channel = channel;
            return this;
        }

        public Builder codeLength(int codeLength) {
            this.codeLength = codeLength;
            return this;
        }

        public Builder ttlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder template(String template) {
            this.template = template;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder statusCallback(String statusCallback) {
            this.statusCallback = statusCallback;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public SendOtpParams build() {
            return new SendOtpParams(this);
        }
    }
}
