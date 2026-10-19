package com.releasy.web;

import com.releasy.domain.user.Role;
import com.releasy.infrastructure.security.JwtTokenIssuer;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Datos del usuario autenticado, leídos de los claims del JWT (ver {@link JwtTokenIssuer}).
 */
final class CurrentUser {

    private CurrentUser() {
    }

    /** Cliente del usuario; {@code null} si es el administrador. */
    static String tenantId(Jwt jwt) {
        return jwt.getClaimAsString(JwtTokenIssuer.CLAIM_TENANT_ID);
    }

    static boolean isAdmin(Jwt jwt) {
        return Role.ADMIN.name().equals(jwt.getClaimAsString(JwtTokenIssuer.CLAIM_ROLE));
    }
}
