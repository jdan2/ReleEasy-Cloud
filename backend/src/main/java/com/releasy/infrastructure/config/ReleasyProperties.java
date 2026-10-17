package com.releasy.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Configuración de la plataforma (prefijo {@code releasy}). Los valores sensibles llegan por variables
 * de entorno; ver {@code application.yml} y {@code .env.example}.
 *
 * <p>Se valida al arrancar: si falta la contraseña del administrador o el secreto del webhook, la API
 * no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "releasy")
public record ReleasyProperties(
        @Valid @NotNull Admin admin,
        @Valid @NotNull Github github,
        @Valid @NotNull Apps apps,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Aws aws) {

    public record Admin(@NotBlank String username, @NotBlank String password) {
    }

    public record Github(@NotBlank String webhookSecret) {
    }

    /** @param baseDomain dominio base de las URLs provisionales de las aplicaciones */
    public record Apps(@NotBlank String baseDomain) {
    }

    /** @param allowedOrigins orígenes que pueden llamar a la API desde el navegador (portal) */
    public record Cors(List<String> allowedOrigins) {
    }

    public record Aws(
            @NotNull Mode mode,
            @NotBlank String region,
            @NotBlank String accountRoleName,
            @NotNull Duration accountCreationPollInterval,
            @NotNull Duration accountCreationTimeout,
            String stateMachineArn,
            @Valid @NotNull Dynamodb dynamodb,
            @Valid @NotNull Mock mock) {
    }

    public enum Mode { MOCK, REAL }

    public record Dynamodb(@NotBlank String tenantsTable, @NotBlank String appsTable, @NotBlank String releasesTable) {
    }

    /** Tiempos de la simulación en modo mock. */
    public record Mock(
            @NotNull Duration accountCreationDelay,
            @NotNull Duration releaseStartDelay,
            @NotNull Duration releaseDuration) {
    }
}
