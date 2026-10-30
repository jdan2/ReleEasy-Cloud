package com.releasy.infrastructure.entrypoints.dto;

import com.releasy.domain.tenant.AwsConnection;
import com.releasy.domain.tenant.ConnectionStatus;
import com.releasy.domain.tenant.GithubConnection;
import com.releasy.domain.tenant.Tenant;

import java.time.Instant;

/**
 * Cliente tal como lo espera el portal (tipo {@code Tenant} de {@code core/api/models.ts}).
 */
public record TenantResponse(
        String id,
        String name,
        String email,
        Aws aws,
        Github github,
        int appCount,
        Instant createdAt) {

    /**
     * @param platformAccountId cuenta AWS de la plataforma, en la que debe confiar el rol del cliente
     * @param appCount          número de aplicaciones del cliente
     */
    public static TenantResponse from(Tenant t, String platformAccountId, int appCount) {
        AwsConnection aws = t.aws();
        GithubConnection github = t.github();
        return new TenantResponse(t.id(), t.name(), t.email(),
                new Aws(aws.status(), aws.externalId(), platformAccountId, aws.roleArn(), aws.accountId(),
                        aws.connectedAt()),
                new Github(github.status(), github.accountLogin(), github.installationId(), github.authorizedBy(),
                        github.connectedAt()),
                appCount, t.createdAt());
    }

    public record Aws(
            ConnectionStatus status,
            String externalId,
            String platformAccountId,
            String roleArn,
            String accountId,
            Instant connectedAt) {
    }

    public record Github(ConnectionStatus status, String accountLogin, Long installationId, String authorizedBy,
                         Instant connectedAt) {
    }
}
