package com.fameen.messaging;

/** Classe mère de toutes les exceptions du SDK Fameen Messaging. */
public class FameenException extends RuntimeException {

    public FameenException(String message) {
        super(message);
    }

    public FameenException(String message, Throwable cause) {
        super(message, cause);
    }
}
