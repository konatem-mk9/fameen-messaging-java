package com.fameen.messaging;

/**
 * Paramètres d'un envoi par canal dédié
 * ({@code POST /sms/send}, {@code /whatsapp/send}, {@code /email/send}).
 *
 * <pre>{@code
 * SendMessageParams.builder()
 *     .to("+224620000000")
 *     .message("Bonjour {prenom} !")
 *     .idempotencyKey("cmd-2026-001")
 *     .build();
 * }</pre>
 */
public final class SendMessageParams {

    private final String to;
    private final String message;
    private final String subject;
    private final String statusCallback;
    private final String idempotencyKey;

    private SendMessageParams(Builder builder) {
        this.to = builder.to;
        this.message = builder.message;
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

    /**
     * Contenu (max 5 000 caractères). Variables de personnalisation :
     * {@code {prenom}}, {@code {nom}}, {@code {email}}, {@code {phone}}.
     */
    public String message() {
        return message;
    }

    /** Objet de l'email (canal email uniquement, max 255). */
    public String subject() {
        return subject;
    }

    /** URL HTTPS publique notifiée à chaque changement de statut. */
    public String statusCallback() {
        return statusCallback;
    }

    /**
     * Clé d'idempotence (en-tête {@code Idempotency-Key}) : tout réessai dans les
     * 24 h renvoie la réponse d'origine au lieu de créer un doublon. Elle rend
     * aussi les réessais automatiques du SDK sûrs sur les POST.
     */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Constructeur fluide de {@link SendMessageParams}. */
    public static final class Builder {

        private String to;
        private String message;
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

        public SendMessageParams build() {
            return new SendMessageParams(this);
        }
    }
}
