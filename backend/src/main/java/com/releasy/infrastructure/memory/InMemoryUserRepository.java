package com.releasy.infrastructure.memory;

import com.releasy.domain.user.User;
import com.releasy.domain.user.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio de usuarios en memoria (modo mock y pruebas). Los datos se pierden al reiniciar.
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(User user) {
        return usersByEmail.putIfAbsent(User.normalizeEmail(user.email()), user) == null;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(User.normalizeEmail(email)));
    }
}
