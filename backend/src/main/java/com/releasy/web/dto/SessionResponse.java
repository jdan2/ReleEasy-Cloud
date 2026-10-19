package com.releasy.web.dto;

import com.releasy.application.auth.Session;
import com.releasy.domain.user.Role;
import com.releasy.domain.user.User;

/**
 * Respuesta de login y registro: {@code Session{token, user}} del contrato del portal (D-21). Nunca
 * incluye la contraseña cifrada.
 */
public record SessionResponse(String token, UserResponse user) {

    public static SessionResponse from(Session session) {
        return new SessionResponse(session.token(), UserResponse.from(session.user()));
    }

    public record UserResponse(String id, String email, String name, Role role, String tenantId) {

        static UserResponse from(User u) {
            return new UserResponse(u.id(), u.email(), u.name(), u.role(), u.tenantId());
        }
    }
}
