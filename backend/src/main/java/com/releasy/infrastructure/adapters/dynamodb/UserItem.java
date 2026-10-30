package com.releasy.infrastructure.adapters.dynamodb;

import com.releasy.domain.user.Role;
import com.releasy.domain.user.User;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Elemento de la tabla {@code users}.
 *
 * <p>Clave: partición {@code email} (normalizado). Así el login es un {@code GetItem} y la unicidad del
 * email la garantiza DynamoDB con una escritura condicional, sin índices secundarios.
 */
@DynamoDbBean
public class UserItem {

    private String email;
    private String userId;
    private String name;
    private String role;
    private String tenantId;
    private String passwordHash;
    private Instant createdAt;

    public static UserItem from(User u) {
        UserItem item = new UserItem();
        item.email = User.normalizeEmail(u.email());
        item.userId = u.id();
        item.name = u.name();
        item.role = u.role().name();
        item.tenantId = u.tenantId();
        item.passwordHash = u.passwordHash();
        item.createdAt = u.createdAt();
        return item;
    }

    public User toDomain() {
        return new User(userId, email, name, Role.valueOf(role), tenantId, passwordHash, createdAt);
    }

    @DynamoDbPartitionKey
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
