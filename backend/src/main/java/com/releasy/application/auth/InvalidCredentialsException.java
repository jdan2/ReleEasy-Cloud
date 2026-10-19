package com.releasy.application.auth;

/**
 * Email o contraseña incorrectos. Se traduce a HTTP 401. El mensaje no dice cuál de los dos falló,
 * para no revelar qué emails están registrados.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email o contraseña incorrectos");
    }
}
