package com.releasy.domain.app;

import java.time.Instant;
import java.util.Map;

/**
 * Aplicación de un cliente: una plantilla del catálogo configurada y vinculada a un repositorio de
 * GitHub (D-22).
 *
 * @param id         identificador interno ({@code a-xxxxxxxxxx})
 * @param tenantId   cliente propietario
 * @param name       nombre corto (slug), usado en nombres de recursos AWS
 * @param templateId plantilla del catálogo ({@code angular-static}, {@code springboot-api}...)
 * @param repository repositorio {@code owner/repo}, normalizado en minúsculas
 * @param branch     rama que dispara los despliegues
 * @param options    componentes opcionales elegidos (claves de la plantilla); serán variables de Terraform
 * @param usage      uso estimado indicado por el cliente (para la estimación de costes)
 * @param url        URL pública: provisional al crearla y real tras el primer despliegue correcto
 */
public record App(
        String id,
        String tenantId,
        String name,
        String templateId,
        String repository,
        String branch,
        Map<String, Object> options,
        Map<String, Double> usage,
        String url,
        Instant createdAt,
        Instant updatedAt) {

    public App {
        options = Map.copyOf(options);
        usage = Map.copyOf(usage);
    }

    public App withUrl(String newUrl, Instant now) {
        return new App(id, tenantId, name, templateId, repository, branch, options, usage, newUrl, createdAt, now);
    }

    public boolean tracksBranch(String pushedBranch) {
        return branch.equals(pushedBranch);
    }
}
