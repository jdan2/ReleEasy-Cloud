package com.releasy.application;

/**
 * La petición no es válida según las reglas de negocio (p. ej. opciones de plantilla incorrectas o una
 * rama que no existe en el repositorio). Se traduce a HTTP 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
