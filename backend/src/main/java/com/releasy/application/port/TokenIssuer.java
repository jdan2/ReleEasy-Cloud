package com.releasy.application.port;

import com.releasy.domain.user.User;

/**
 * Emite el token de sesión de un usuario autenticado.
 *
 * <p>Implementación: {@code JwtTokenIssuer}, que firma un JWT con HMAC-SHA256. La API lo valida en cada
 * petición ({@code Authorization: Bearer <token>}).
 */
public interface TokenIssuer {

    String issue(User user);
}
