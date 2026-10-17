package com.releasy.domain.tenant;

/**
 * Estado de la cuenta miembro de AWS asociada a un cliente.
 */
public enum TenantStatus {
    /** Se ha solicitado {@code CreateAccount} y se espera a que AWS Organizations la complete. */
    CREATING,
    /** La cuenta existe y la plataforma puede desplegar en ella. */
    ACTIVE,
    /** La creación de la cuenta falló o superó el tiempo máximo de espera. */
    FAILED
}
