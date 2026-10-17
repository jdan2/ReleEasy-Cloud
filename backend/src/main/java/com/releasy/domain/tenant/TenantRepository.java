package com.releasy.domain.tenant;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de clientes. Implementaciones: memoria (modo mock) y DynamoDB (tabla {@code tenants}).
 */
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(String id);

    List<Tenant> findAll();

    List<Tenant> findByStatus(TenantStatus status);
}
