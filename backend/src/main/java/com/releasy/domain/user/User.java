package com.releasy.domain.user;

import java.time.Instant;
import java.util.Locale;

/**
 * Usuario del portal.
 *
 * @param id           identificador interno ({@code u-xxxxxxxxxx})
 * @param email        email normalizado (minúsculas y sin espacios); es único y sirve para iniciar sesión
 * @param name         nombre que se muestra en el portal (para un cliente, el de su empresa)
 * @param role         rol del usuario
 * @param tenantId     cliente al que pertenece; {@code null} para el administrador
 * @param passwordHash contraseña cifrada con BCrypt; nunca se guarda ni se devuelve en claro
 */
public record User(
        String id,
        String email,
        String name,
        Role role,
        String tenantId,
        String passwordHash,
        Instant createdAt) {

    public static User client(String id, String email, String companyName, String tenantId, String passwordHash,
                              Instant now) {
        return new User(id, normalizeEmail(email), companyName, Role.CLIENT, tenantId, passwordHash, now);
    }

    /** Normaliza un email para guardarlo y buscarlo siempre igual. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
