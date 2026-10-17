package com.releasy.domain.tenant;

import com.releasy.domain.shared.Slugs;

import java.time.Instant;

/**
 * Cliente de la plataforma (PYME). Cada cliente tiene su propia cuenta miembro de AWS.
 *
 * @param id                identificador interno ({@code t-xxxxxxxxxx})
 * @param name              nombre comercial
 * @param slug              nombre normalizado, usado en URLs y nombres de recursos
 * @param email             email raíz de la cuenta AWS (debe ser único en AWS)
 * @param status            estado de la cuenta AWS
 * @param accountId         ID de la cuenta AWS (cuando está {@link TenantStatus#ACTIVE})
 * @param roleArn           ARN del rol que asume la plataforma para desplegar en la cuenta
 * @param creationRequestId ID de la petición {@code CreateAccount} de AWS Organizations
 * @param failureReason     motivo del fallo (cuando está {@link TenantStatus#FAILED})
 */
public record Tenant(
        String id,
        String name,
        String slug,
        String email,
        TenantStatus status,
        String accountId,
        String roleArn,
        String creationRequestId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    private static final int SLUG_MAX_LENGTH = 20;

    public static Tenant creating(String id, String name, String email, Instant now) {
        return new Tenant(id, name, Slugs.of(name, SLUG_MAX_LENGTH), email, TenantStatus.CREATING,
                null, null, null, null, now, now);
    }

    public Tenant withCreationRequest(String requestId, Instant now) {
        return new Tenant(id, name, slug, email, status, accountId, roleArn, requestId, failureReason, createdAt, now);
    }

    public Tenant activate(String newAccountId, String newRoleArn, Instant now) {
        requireCreating();
        return new Tenant(id, name, slug, email, TenantStatus.ACTIVE, newAccountId, newRoleArn,
                creationRequestId, null, createdAt, now);
    }

    public Tenant fail(String reason, Instant now) {
        requireCreating();
        return new Tenant(id, name, slug, email, TenantStatus.FAILED, accountId, roleArn,
                creationRequestId, reason, createdAt, now);
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    private void requireCreating() {
        if (status != TenantStatus.CREATING) {
            throw new IllegalStateException("El cliente " + id + " ya no está en estado CREATING (" + status + ")");
        }
    }
}
