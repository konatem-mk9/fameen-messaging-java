package com.fameen.messaging;

import com.fasterxml.jackson.annotation.JsonValue;

/** Canaux d'envoi supportés par l'API Fameen Messaging. */
public enum Channel {

    SMS("sms"),
    WHATSAPP("whatsapp"),
    EMAIL("email");

    private final String value;

    Channel(String value) {
        this.value = value;
    }

    /** Valeur attendue par l'API ({@code sms}, {@code whatsapp}, {@code email}). */
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
