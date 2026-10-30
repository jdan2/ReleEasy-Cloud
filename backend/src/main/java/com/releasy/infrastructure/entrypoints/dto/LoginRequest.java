package com.releasy.infrastructure.entrypoints.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inicio de sesión. El campo {@code email} no se valida como email porque el administrador entra con
 * su nombre de usuario ({@code RELEASY_ADMIN_USER}).
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
