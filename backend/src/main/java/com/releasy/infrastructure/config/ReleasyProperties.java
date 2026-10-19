package com.releasy.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Configuración de la plataforma (prefijo {@code releasy}). Los valores sensibles llegan por variables
 * de entorno; ver {@code application.yml} y {@code .env.example}.
 *
 * <p>Se valida al arrancar: si falta la contraseña del administrador, el secreto de los JWT o el del
 * webhook, la API no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "releasy")
public record ReleasyProperties(
        @Valid @NotNull Admin admin,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Github github,
        @Valid @NotNull Apps apps,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Aws aws) {

    public record Admin(@NotBlank String username, @NotBlank String password) {
    }

    /**
     * @param jwtSecret secreto con el que se firman los JWT de sesión (HMAC-SHA256, mínimo 32 caracteres)
     * @param tokenTtl  validez del token; al caducar, el usuario vuelve a iniciar sesión
     */
    public record Auth(@NotBlank @Size(min = 32) String jwtSecret, @NotNull Duration tokenTtl) {
    }

    /**
     * @param mode           {@code mock} (repositorios ficticios) o {@code real} (API de GitHub como GitHub App)
     * @param appId          App ID de la GitHub App (obligatorio en modo real)
     * @param privateKeyPath ruta del fichero {@code .pem} con la clave privada de la app (obligatorio en modo
     *                       real; nunca dentro del repositorio)
     * @param clientId       client ID de la app, para la autorización OAuth del usuario (obligatorio en modo real)
     * @param clientSecret   client secret de la app (obligatorio en modo real; secreto)
     * @param apiUrl         URL base de la API de GitHub
     * @param oauthUrl       web de GitHub, donde se canjean los códigos OAuth
     * @param webhookSecret  secreto con el que GitHub firma los webhooks
     */
    public record Github(
            @NotNull Mode mode,
            String appId,
            String privateKeyPath,
            String clientId,
            String clientSecret,
            @NotBlank String apiUrl,
            @NotBlank String oauthUrl,
            @NotBlank String webhookSecret) {
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
            @NotBlank @Pattern(regexp = "\\d{12}") String platformAccountId,
            String stateMachineArn,
            @Valid @NotNull Dynamodb dynamodb,
            @Valid @NotNull Mock mock) {
    }

    public enum Mode { MOCK, REAL }

    public record Dynamodb(@NotBlank String usersTable, @NotBlank String tenantsTable, @NotBlank String appsTable, @NotBlank String releasesTable) {
    }

    /** Tiempos de la simulación en modo mock. */
    public record Mock(
            @NotNull Duration releaseStartDelay,
            @NotNull Duration releaseDuration) {
    }
}
