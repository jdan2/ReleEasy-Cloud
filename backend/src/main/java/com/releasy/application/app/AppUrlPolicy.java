package com.releasy.application.app;

/**
 * Regla para asignar la URL provisional de una aplicación al registrarla:
 * {@code https://<app>-<cliente>.<dominio-base>}.
 *
 * <p>La URL real (dominio de CloudFront) solo se conoce tras el primer despliegue y sustituye a esta.
 * Ver la decisión D-03 en {@code docs/decisiones.md}.
 */
public record AppUrlPolicy(String baseDomain) {

    public String provisionalUrl(String appName, String tenantSlug) {
        return "https://" + appName + "-" + tenantSlug + "." + baseDomain;
    }
}
