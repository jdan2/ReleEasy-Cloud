package com.releasy.application.port;

/**
 * GitHub respondió con un error o no se pudo contactar. Se traduce a HTTP 502, salvo los casos que
 * los servicios convierten antes en un error del cliente (p. ej. una instalación que no existe).
 *
 * @see GithubGateway
 */
public class GithubApiException extends RuntimeException {

    /** Código HTTP de GitHub; 0 si no hubo respuesta. */
    private final int status;

    public GithubApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public GithubApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }
}
