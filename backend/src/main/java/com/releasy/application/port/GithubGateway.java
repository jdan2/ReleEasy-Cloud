package com.releasy.application.port;

import java.util.List;
import java.util.Optional;

/**
 * Puerto hacia la API de GitHub, actuando como la GitHub App de ReleEasy.
 *
 * <p>Implementaciones: {@code GithubAppClient} (modo real: JWT de la app y tokens de instalación) y
 * {@code MockGithubGateway} (modo mock: repositorios ficticios). Se elige con {@code releasy.github.mode},
 * independiente del modo de AWS.
 *
 * <p>Los errores de GitHub se lanzan como {@link GithubApiException}.
 */
public interface GithubGateway {

    /**
     * Canjea el {@code code} de un solo uso con el que GitHub vuelve al portal tras la autorización
     * del usuario (OAuth), y devuelve quién es y a qué instalaciones de la app tiene acceso. El token
     * del usuario solo se usa durante esta llamada; no se guarda.
     *
     * <p>Lanza {@link GithubApiException} con 401 si el código no es válido o ha caducado.
     */
    UserAuthorization authorizeUser(String code);

    /** Repositorios a los que el cliente dio acceso al instalar la app, con sus ramas. */
    List<Repository> repositories(long installationId);

    /** Último commit de una rama, o vacío si la rama no existe. */
    Optional<Commit> branchHead(long installationId, String repository, String branch);

    /** @param accountLogin usuario u organización de GitHub donde está instalada la app */
    record Installation(long id, String accountLogin) {
    }

    /**
     * @param userLogin     usuario de GitHub que autorizó
     * @param installations instalaciones de la app a las que ese usuario tiene acceso
     */
    record UserAuthorization(String userLogin, List<Installation> installations) {
    }

    /** @param fullName repositorio en formato {@code owner/repo} */
    record Repository(String fullName, String description, boolean isPrivate, String defaultBranch,
                      List<String> branches) {
    }

    record Commit(String sha, String message) {
    }
}
