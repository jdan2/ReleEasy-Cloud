package com.releasy.infrastructure.dynamodb;

import com.releasy.domain.app.App;
import com.releasy.domain.app.AppPlan;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;

/**
 * Elemento de la tabla {@code apps}.
 *
 * <p>Clave: partición {@code appId}. Índices globales:
 * <ul>
 *   <li>{@value #TENANT_INDEX}: partición {@code tenantId}, para listar las apps de un cliente;</li>
 *   <li>{@value #REPOSITORY_INDEX}: partición {@code repository}, para que el webhook encuentre la app.</li>
 * </ul>
 */
@DynamoDbBean
public class AppItem {

    public static final String TENANT_INDEX = "tenantId-index";
    public static final String REPOSITORY_INDEX = "repository-index";

    private String appId;
    private String tenantId;
    private String name;
    private String repository;
    private String branch;
    private String plan;
    private String url;
    private Instant createdAt;
    private Instant updatedAt;

    public static AppItem from(App a) {
        AppItem item = new AppItem();
        item.appId = a.id();
        item.tenantId = a.tenantId();
        item.name = a.name();
        item.repository = a.repository();
        item.branch = a.branch();
        item.plan = a.plan().name();
        item.url = a.url();
        item.createdAt = a.createdAt();
        item.updatedAt = a.updatedAt();
        return item;
    }

    public App toDomain() {
        return new App(appId, tenantId, name, repository, branch, AppPlan.valueOf(plan), url, createdAt, updatedAt);
    }

    @DynamoDbPartitionKey
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    @DynamoDbSecondaryPartitionKey(indexNames = TENANT_INDEX)
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @DynamoDbSecondaryPartitionKey(indexNames = REPOSITORY_INDEX)
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
