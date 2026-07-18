package com.fameen.messaging;

/**
 * Classe de média d'une pièce jointe : détermine le rendu WhatsApp
 * (image / vidéo / audio / document). Déduite du type MIME côté serveur si
 * elle n'est pas précisée.
 */
public enum MediaType {

    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    DOCUMENT("document");

    private final String value;

    MediaType(String value) {
        this.value = value;
    }

    /** Valeur transmise à l'API ({@code image} | {@code video} | {@code audio} | {@code document}). */
    public String value() {
        return value;
    }
}
