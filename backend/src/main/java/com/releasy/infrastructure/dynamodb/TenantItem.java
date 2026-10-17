package com.releasy.infrastructure.dynamodb;

import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Elemento de la tabla {@code tenants}.
 *
 * <p>Clave: partición {@code tenantId}, sin clave de ordenación (se accede por ID y el listado es un
 * scan, porque la tabla es pequeña).
 */
@DynamoDbBean
public class TenantItem {

    private String tenantId;
    private String name;
    private String slug;
    private String email;
    private String status;
    private String accountId;
    private String roleArn;
    private String creationRequestId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static TenantItem from(Tenant t) {
        TenantItem item = new TenantItem();
        item.tenantId = t.id();
        item.name = t.name();
        item.slug = t.slug();
        item.email = t.email();
        item.status = t.status().name();
        item.accountId = t.accountId();
        item.roleArn = t.roleArn();
        item.creationRequestId = t.creationRequestId();
        item.failureReason = t.failureReason();
        item.createdAt = t.createdAt();
        item.updatedAt = t.updatedAt();
        return item;
    }

    public Tenant toDomain() {
        return new Tenant(tenantId, name, slug, email, TenantStatus.valueOf(status), accountId, roleArn,
                creationRequestId, failureReason, createdAt, updatedAt);
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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getRoleArn() { return roleArn; }
    public void setRoleArn(String roleArn) { this.roleArn = roleArn; }

    public String getCreationRequestId() { return creationRequestId; }
    public void setCreationRequestId(String creationRequestId) { this.creationRequestId = creationRequestId; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
