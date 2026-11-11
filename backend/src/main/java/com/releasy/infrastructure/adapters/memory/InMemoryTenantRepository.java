package com.releasy.infrastructure.adapters.memory;

import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio de clientes en memoria (modo mock y pruebas). Los datos se pierden al reiniciar.
 */
public class InMemoryTenantRepository implements TenantRepository {

    private final Map<String, Tenant> tenants = new ConcurrentHashMap<>();

    @Override
    public Tenant save(Tenant tenant) {
        tenants.put(tenant.id(), tenant);
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(String id) {
        return Optional.ofNullable(tenants.get(id));
    }

    @Override
    public List<Tenant> findAll() {
        return List.copyOf(tenants.values());
    }
}
