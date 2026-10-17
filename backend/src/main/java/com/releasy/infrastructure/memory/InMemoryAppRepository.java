package com.releasy.infrastructure.memory;

import com.releasy.domain.app.App;
import com.releasy.domain.app.AppRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio de aplicaciones en memoria (modo mock y pruebas).
 */
public class InMemoryAppRepository implements AppRepository {

    private final Map<String, App> apps = new ConcurrentHashMap<>();

    @Override
    public App save(App app) {
        apps.put(app.id(), app);
        return app;
    }

    @Override
    public Optional<App> findById(String id) {
        return Optional.ofNullable(apps.get(id));
    }

    @Override
    public List<App> findByTenantId(String tenantId) {
        return apps.values().stream().filter(a -> a.tenantId().equals(tenantId)).toList();
    }

    @Override
    public List<App> findByRepository(String repository) {
        return apps.values().stream().filter(a -> a.repository().equals(repository)).toList();
    }
}
