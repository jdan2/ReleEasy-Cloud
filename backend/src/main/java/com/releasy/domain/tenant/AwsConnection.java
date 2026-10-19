package com.releasy.domain.tenant;

import java.time.Instant;

/**
 * Conexión con la cuenta AWS del propio cliente (D-18): un rol IAM ({@code ReleEasyDeployRole}) que
 * confía en la cuenta de la plataforma y exige el {@code externalId}.
 *
 * @param externalId  identificador secreto que debe exigir la política de confianza del rol; se genera
 *                    al registrar el cliente y no cambia
 * @param roleArn     ARN del rol (cuando está conectada)
 * @param accountId   cuenta AWS del cliente (cuando está conectada)
 * @param connectedAt fecha en que se verificó el rol
 */
public record AwsConnection(
        ConnectionStatus status,
        String externalId,
        String roleArn,
        String accountId,
        Instant connectedAt) {

    public static AwsConnection notConnected(String externalId) {
        return new AwsConnection(ConnectionStatus.NOT_CONNECTED, externalId, null, null, null);
    }

    public AwsConnection connect(String newRoleArn, String newAccountId, Instant now) {
        return new AwsConnection(ConnectionStatus.CONNECTED, externalId, newRoleArn, newAccountId, now);
    }

    public boolean isConnected() {
        return status == ConnectionStatus.CONNECTED;
    }
}
