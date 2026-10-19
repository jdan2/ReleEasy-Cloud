package com.releasy.domain.user;

import java.util.Optional;

/**
 * Persistencia de usuarios. Implementaciones: memoria (modo mock) y DynamoDB (tabla {@code users}, con
 * el email como clave).
 */
public interface UserRepository {

    /**
     * Guarda el usuario solo si no existe otro con el mismo email. La comprobación es atómica, así que
     * dos registros simultáneos con el mismo email no pueden tener éxito los dos.
     *
     * @return {@code false} si el email ya estaba registrado
     */
    boolean saveIfAbsent(User user);

    /** Busca por email (se normaliza antes de buscar). */
    Optional<User> findByEmail(String email);
}
