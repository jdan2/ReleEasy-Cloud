package com.releasy.infrastructure.dynamodb;

import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;

/**
 * Elemento de la tabla {@code releases}.
 *
 * <p>Clave: partición {@code appId}, ordenación {@code releaseId}. Como {@code releaseId} empieza por
 * la fecha, una consulta por {@code appId} en orden descendente devuelve el historial del más reciente
 * al más antiguo.
 *
 * <p>Los nombres de los atributos son un contrato con la máquina de estados y {@code buildspec-deploy.yml},
 * que actualizan {@code status}, {@code url}, {@code errorMessage} y {@code updatedAt} directamente.
 */
@DynamoDbBean
public class ReleaseItem {

    private String appId;
    private String releaseId;
    private String tenantId;
    private String commitSha;
    private String commitMessage;
    private String status;
    private String url;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static ReleaseItem from(Release r) {
        ReleaseItem item = new ReleaseItem();
        item.appId = r.appId();
        item.releaseId = r.id();
        item.tenantId = r.tenantId();
        item.commitSha = r.commitSha();
        item.commitMessage = r.commitMessage();
        item.status = r.status().name();
        item.url = r.url();
        item.errorMessage = r.errorMessage();
        item.createdAt = r.createdAt();
        item.updatedAt = r.updatedAt();
        return item;
    }

    public Release toDomain() {
        return new Release(releaseId, appId, tenantId, commitSha, commitMessage, ReleaseStatus.valueOf(status),
                url, errorMessage, createdAt, updatedAt);
    }

    @DynamoDbPartitionKey
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    @DynamoDbSortKey
    public String getReleaseId() { return releaseId; }
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
