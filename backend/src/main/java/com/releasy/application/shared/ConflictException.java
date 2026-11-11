package com.releasy.application.shared;

/**
 * La operación no es posible en el estado actual (p. ej. registrar una aplicación en un cliente
 * cuya cuenta AWS aún no está activa). Se traduce a HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
