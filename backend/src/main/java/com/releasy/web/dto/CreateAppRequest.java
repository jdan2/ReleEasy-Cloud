package com.releasy.web.dto;

import com.releasy.application.app.AppService.NewApp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Alta de una aplicación ({@code POST /api/me/apps}). Aquí solo se comprueba que vienen los campos; el
 * formato del nombre, el repositorio, la rama y las opciones los valida {@code AppService} con las
 * mismas reglas y mensajes que el portal.
 */
public record CreateAppRequest(
        @NotBlank String name,
        @NotBlank String templateId,
        @NotBlank String repository,
        @NotBlank String branch,
        @NotNull Map<String, Object> options,
        Map<String, Object> usage) {

    public NewApp toNewApp() {
        return new NewApp(name, templateId, repository, branch, options, usage == null ? Map.of() : usage);
    }
}
