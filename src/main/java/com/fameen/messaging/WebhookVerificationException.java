package com.fameen.messaging;

/** Signature ou corps de webhook invalide — ne traitez pas l'événement (répondez 401). */
public class WebhookVerificationException extends FameenException {

    public WebhookVerificationException(String message) {
        super(message);
    }

    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
