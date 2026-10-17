package com.releasy.domain.release;

import java.time.Instant;

/**
 * Despliegue de una versión concreta (commit) de una aplicación.
 *
 * <p>Es inmutable: cada cambio de estado devuelve una copia nueva y valida la transición con
 * {@link ReleaseStatus#canTransitionTo(ReleaseStatus)}.
 *
 * @param id            identificador ordenable por fecha ({@code r-yyyyMMddHHmmss-xxxxxx}); también
 *                      es el nombre de la ejecución de Step Functions
 * @param commitSha     SHA del commit desplegado
 * @param commitMessage primera línea del mensaje del commit
 * @param url           URL pública resultante (cuando termina bien)
 * @param errorMessage  motivo del fallo (cuando falla)
 */
public record Release(
        String id,
        String appId,
        String tenantId,
        String commitSha,
        String commitMessage,
        ReleaseStatus status,
        String url,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static Release pending(String id, String appId, String tenantId, String commitSha,
                                  String commitMessage, Instant now) {
        return new Release(id, appId, tenantId, commitSha, commitMessage, ReleaseStatus.PENDING,
                null, null, now, now);
    }

    public Release start(Instant now) {
        return transitionTo(ReleaseStatus.RUNNING, url, null, now);
    }

    public Release succeed(String publicUrl, Instant now) {
        return transitionTo(ReleaseStatus.SUCCEEDED, publicUrl, null, now);
    }

    public Release fail(String error, Instant now) {
        return transitionTo(ReleaseStatus.FAILED, url, error, now);
    }

    private Release transitionTo(ReleaseStatus next, String newUrl, String newError, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalReleaseTransitionException(id, status, next);
        }
        return new Release(id, appId, tenantId, commitSha, commitMessage, next, newUrl, newError, createdAt, now);
    }
}
