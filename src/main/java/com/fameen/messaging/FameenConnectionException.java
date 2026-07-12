package com.fameen.messaging;

/** Échec réseau : l'API n'a pas pu être jointe (DNS, timeout, coupure…), réessais épuisés. */
public class FameenConnectionException extends FameenException {

    public FameenConnectionException(String message) {
        super(message);
    }

    public FameenConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
