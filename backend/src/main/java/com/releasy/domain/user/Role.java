package com.releasy.domain.user;

/**
 * Rol de un usuario del portal.
 */
public enum Role {
    /** Operador de la plataforma: ve a todos los clientes y sus apps, en solo lectura. */
    ADMIN,
    /** Usuario de una empresa cliente: gestiona sus conexiones y sus aplicaciones. */
    CLIENT
}
