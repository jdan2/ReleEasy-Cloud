package com.releasy.application.webhook;

/**
 * El cuerpo del webhook (ya con firma válida) no tiene el formato esperado. Se traduce a HTTP 400.
 */
public class InvalidWebhookPayloadException extends RuntimeException {

    public InvalidWebhookPayloadException(String message) {
        super(message);
    }
}
