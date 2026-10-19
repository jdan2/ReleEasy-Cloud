package com.releasy.domain.tenant;

import com.releasy.domain.shared.Slugs;

import java.time.Instant;

/**
 * Cliente de la plataforma (PYME). Se registra solo en el portal y después conecta su propia cuenta de
 * AWS y su cuenta de GitHub (D-18).
 *
 * @param id     identificador interno ({@code t-xxxxxxxxxx})
 * @param name   nombre de la empresa
 * @param slug   nombre normalizado, usado en URLs y nombres de recursos
 * @param email  email de contacto (el del usuario que la registró)
 * @param aws    conexión con su cuenta AWS
 * @param github conexión con GitHub
 */
public record Tenant(
        String id,
        String name,
        String slug,
        String email,
        AwsConnection aws,
        GithubConnection github,
        Instant createdAt,
        Instant updatedAt) {

    private static final int SLUG_MAX_LENGTH = 20;

    /** Cliente recién registrado: todavía sin conexiones. */
    public static Tenant register(String id, String name, String email, String externalId, Instant now) {
        return new Tenant(id, name, Slugs.of(name, SLUG_MAX_LENGTH), email, AwsConnection.notConnected(externalId),
                GithubConnection.notConnected(), now, now);
    }

    public Tenant withAws(AwsConnection newAws, Instant now) {
        return new Tenant(id, name, slug, email, newAws, github, createdAt, now);
    }

    public Tenant withGithub(GithubConnection newGithub, Instant now) {
        return new Tenant(id, name, slug, email, aws, newGithub, createdAt, now);
    }
}
