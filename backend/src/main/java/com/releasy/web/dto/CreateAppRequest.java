package com.releasy.web.dto;

import com.releasy.domain.app.AppPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @param name       nombre corto: minúsculas, dígitos y guiones (se usa en nombres de recursos AWS)
 * @param repository repositorio de GitHub en formato {@code owner/repo}
 * @param branch     rama a desplegar; si se omite, {@code master}
 * @param plan       plan contratado
 */
public record CreateAppRequest(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,19}$",
                message = "debe empezar por letra y tener 3-20 caracteres: minúsculas, dígitos o guiones") String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$",
                message = "debe tener el formato owner/repo") String repository,
        @Pattern(regexp = "^[A-Za-z0-9._/-]{1,100}$", message = "nombre de rama no válido") String branch,
        @NotNull AppPlan plan) {

    public static final String DEFAULT_BRANCH = "master";

    public String branchOrDefault() {
        return branch == null || branch.isBlank() ? DEFAULT_BRANCH : branch;
    }
}
