package com.releasy.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param name  nombre del cliente (también será el nombre de la cuenta AWS; máx. 50 caracteres)
 * @param email email raíz de la cuenta AWS; debe ser único en todo AWS
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 64) String email) {
}
