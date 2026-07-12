package com.fameen.messaging;

/**
 * Paramètres de l'envoi unifié ({@code POST /messages}).
 *
 * <p>Canal explicite via {@link Builder#channel(Channel)}, sinon déduit par le
 * serveur : email si {@code to} contient « @ », sinon SMS. WhatsApp doit donc
 * toujours être explicite.</p>
 */
public final class CreateMessageParams {

    private final String to;
    private final String message;
    private final Channel channel;
    private final String subject;
    private final String statusCallback;
    private final String idempotencyKey;

    private CreateMessageParams(Builder builder) {
        this.to = builder.to;
        this.message = builder.message;
        this.channel = builder.channel;
        this.subject = builder.subject;
        this.statusCallback = builder.statusCallback;
        this.idempotencyKey = builder.idempotencyKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Destinataire : numéro E.164 ({@code +224…}) ou adresse email. */
    public String to() {
        return to;
    }

    /** Contenu (max 5 000 caractères, variables {@code {prenom} {nom} {email} {phone}}). */
    public String message() {
        return message;
    }

    /** Canal explicite, ou {@code null} si déduit du destinataire. */
    public Channel channel() {
        return channel;
    }

    /** Objet de l'email (canal email uniquement, max 255). */
    public String subject() {
        return subject;
    }

    /** URL HTTPS publique notifiée à chaque changement de statut. */
    public String statusCallback() {
        return statusCallback;
    }

    /** Clé d'idempotence (en-tête {@code Idempotency-Key}, fenêtre 24 h). */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Constructeur fluide de {@link CreateMessageParams}. */
    public static final class Builder {

        private String to;
        private String message;
        private Channel channel;
        private String subject;
        private String statusCallback;
        private String idempotencyKey;

        private Builder() {
        }

        /** Destinataire (requis) : numéro E.164 ou adresse email. */
        public Builder to(String to) {
            this.to = to;
            return this;
        }

        /** Contenu du message (requis, max 5 000 caractères). */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /** Canal explicite ({@code null} = déduit : « @ » → email, sinon SMS). */
        public Builder channel(Channel channel) {
            this.channel = channel;
            return this;
        }

        /** Objet de l'email (canal email uniquement, max 255). */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /** URL HTTPS publique de callback de statut. */
        public Builder statusCallback(String statusCallback) {
            this.statusCallback = statusCallback;
            return this;
        }

        /** Clé d'idempotence (fenêtre de 24 h côté serveur). */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public CreateMessageParams build() {
            return new CreateMessageParams(this);
        }
    }
}
