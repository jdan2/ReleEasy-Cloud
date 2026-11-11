package com.releasy.infrastructure.adapters.dynamodb;

import com.releasy.domain.tenant.AwsConnection;
import com.releasy.domain.tenant.ConnectionStatus;
import com.releasy.domain.tenant.GithubConnection;
import com.releasy.domain.tenant.Tenant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Elemento de la tabla {@code tenants}.
 *
 * <p>Clave: partición {@code tenantId}, sin clave de ordenación (se accede por ID y el listado es un
 * scan, porque la tabla es pequeña). Las conexiones AWS y GitHub se guardan como atributos planos con
 * prefijo {@code aws}/{@code github}.
 */
@DynamoDbBean
public class TenantItem {

    private String tenantId;
    private String name;
    private String slug;
    private String email;
    private String awsStatus;
    private String awsExternalId;
    private String awsRoleArn;
    private String awsAccountId;
    private Instant awsConnectedAt;
    private String githubStatus;
    private String githubAccountLogin;
    private Long githubInstallationId;
    private String githubAuthorizedBy;
    private Instant githubConnectedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static TenantItem from(Tenant t) {
        TenantItem item = new TenantItem();
        item.tenantId = t.id();
        item.name = t.name();
        item.slug = t.slug();
        item.email = t.email();
        item.awsStatus = t.aws().status().name();
        item.awsExternalId = t.aws().externalId();
        item.awsRoleArn = t.aws().roleArn();
        item.awsAccountId = t.aws().accountId();
        item.awsConnectedAt = t.aws().connectedAt();
        item.githubStatus = t.github().status().name();
        item.githubAccountLogin = t.github().accountLogin();
        item.githubInstallationId = t.github().installationId();
        item.githubAuthorizedBy = t.github().authorizedBy();
        item.githubConnectedAt = t.github().connectedAt();
        item.createdAt = t.createdAt();
        item.updatedAt = t.updatedAt();
        return item;
    }

    public Tenant toDomain() {
        AwsConnection aws = new AwsConnection(ConnectionStatus.valueOf(awsStatus), awsExternalId, awsRoleArn,
                awsAccountId, awsConnectedAt);
        GithubConnection github = new GithubConnection(ConnectionStatus.valueOf(githubStatus), githubAccountLogin,
                githubInstallationId, githubAuthorizedBy, githubConnectedAt);
        return new Tenant(tenantId, name, slug, email, aws, github, createdAt, updatedAt);
    }

    @DynamoDbPartitionKey
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAwsStatus() { return awsStatus; }
    public void setAwsStatus(String awsStatus) { this.awsStatus = awsStatus; }

    public String getAwsExternalId() { return awsExternalId; }
    public void setAwsExternalId(String awsExternalId) { this.awsExternalId = awsExternalId; }

    public String getAwsRoleArn() { return awsRoleArn; }
    public void setAwsRoleArn(String awsRoleArn) { this.awsRoleArn = awsRoleArn; }

    public String getAwsAccountId() { return awsAccountId; }
    public void setAwsAccountId(String awsAccountId) { this.awsAccountId = awsAccountId; }

    public Instant getAwsConnectedAt() { return awsConnectedAt; }
    public void setAwsConnectedAt(Instant awsConnectedAt) { this.awsConnectedAt = awsConnectedAt; }

    public String getGithubStatus() { return githubStatus; }
    public void setGithubStatus(String githubStatus) { this.githubStatus = githubStatus; }

    public String getGithubAccountLogin() { return githubAccountLogin; }
    public void setGithubAccountLogin(String githubAccountLogin) { this.githubAccountLogin = githubAccountLogin; }

    public Long getGithubInstallationId() { return githubInstallationId; }
    public void setGithubInstallationId(Long githubInstallationId) { this.githubInstallationId = githubInstallationId; }

    public String getGithubAuthorizedBy() { return githubAuthorizedBy; }
    public void setGithubAuthorizedBy(String githubAuthorizedBy) { this.githubAuthorizedBy = githubAuthorizedBy; }

    public Instant getGithubConnectedAt() { return githubConnectedAt; }
    public void setGithubConnectedAt(Instant githubConnectedAt) { this.githubConnectedAt = githubConnectedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
