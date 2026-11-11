package com.releasy.infrastructure.entrypoints.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de una empresa cliente. Las reglas coinciden con las del formulario del portal. La contraseña
 * tiene como máximo 72 caracteres porque BCrypt ignora lo que pase de 72 bytes.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 50) String companyName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
