package com.releasy.domain.tenant;

import java.time.Instant;

/**
 * Instalación de la GitHub App de ReleEasy en la cuenta u organización de GitHub del cliente.
 *
 * @param accountLogin   usuario u organización de GitHub donde se instaló la app
 * @param installationId identificador de la instalación; con él se piden tokens para leer los repos
 * @param authorizedBy   usuario de GitHub que autorizó la conexión (D-26)
 * @param connectedAt    fecha de la conexión
 */
public record GithubConnection(
        ConnectionStatus status,
        String accountLogin,
        Long installationId,
        String authorizedBy,
        Instant connectedAt) {

    public static GithubConnection notConnected() {
        return new GithubConnection(ConnectionStatus.NOT_CONNECTED, null, null, null, null);
    }

    public static GithubConnection connected(String accountLogin, long installationId, String authorizedBy,
                                             Instant now) {
        return new GithubConnection(ConnectionStatus.CONNECTED, accountLogin, installationId, authorizedBy, now);
    }

    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }
}
