package com.releasy.application;

/**
 * El recurso solicitado no existe. Se traduce a HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, String id) {
        super(resource + " no encontrado: " + id);
    }
}
