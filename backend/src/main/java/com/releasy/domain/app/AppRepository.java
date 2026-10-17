package com.releasy.domain.app;

import java.util.List;
import java.util.Optional;

/**
 * Persistencia de aplicaciones. Implementaciones: memoria (modo mock) y DynamoDB (tabla {@code apps}).
 */
public interface AppRepository {

    App save(App app);

    Optional<App> findById(String id);

    List<App> findByTenantId(String tenantId);

    /** Busca por repositorio {@code owner/repo} (en minúsculas). Lo usa el webhook de GitHub. */
    List<App> findByRepository(String repository);
}
