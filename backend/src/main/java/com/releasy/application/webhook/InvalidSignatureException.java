package com.releasy.application.webhook;

/**
 * La firma {@code X-Hub-Signature-256} falta o no coincide. Se traduce a HTTP 401.
 */
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException() {
        super("Firma del webhook ausente o inválida");
    }
}
