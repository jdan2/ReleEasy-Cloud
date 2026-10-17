package com.releasy.domain.app;

import java.time.Instant;

/**
 * Aplicación de un cliente, vinculada a un repositorio de GitHub.
 *
 * @param id         identificador interno ({@code a-xxxxxxxxxx})
 * @param tenantId   cliente propietario
 * @param name       nombre corto (slug), usado en nombres de recursos AWS
 * @param repository repositorio {@code owner/repo}, normalizado en minúsculas
 * @param branch     rama que dispara los despliegues (por defecto {@code master})
 * @param plan       plan contratado
 * @param url        URL pública: provisional al crearla y real tras el primer despliegue correcto
 */
public record App(
        String id,
        String tenantId,
        String name,
        String repository,
        String branch,
        AppPlan plan,
        String url,
        Instant createdAt,
        Instant updatedAt) {

    public App withUrl(String newUrl, Instant now) {
        return new App(id, tenantId, name, repository, branch, plan, newUrl, createdAt, now);
    }

    public boolean tracksBranch(String pushedBranch) {
        return branch.equals(pushedBranch);
    }
}
