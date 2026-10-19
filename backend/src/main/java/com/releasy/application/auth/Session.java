package com.releasy.application.auth;

import com.releasy.domain.user.User;

/**
 * Resultado de un login o registro correcto: el token que el portal envía en cada petición y el
 * usuario autenticado.
 */
public record Session(String token, User user) {
}
