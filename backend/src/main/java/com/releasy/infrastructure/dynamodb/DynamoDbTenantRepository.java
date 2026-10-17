package com.releasy.infrastructure.dynamodb;

import com.releasy.domain.tenant.Tenant;
import com.releasy.domain.tenant.TenantRepository;
import com.releasy.domain.tenant.TenantStatus;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DynamoDbTenantRepository implements TenantRepository {

    private final DynamoDbTable<TenantItem> table;

    public DynamoDbTenantRepository(DynamoDbEnhancedClient client, String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(TenantItem.class));
    }

    @Override
    public Tenant save(Tenant tenant) {
        table.putItem(TenantItem.from(tenant));
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build())).map(TenantItem::toDomain);
    }

    @Override
    public List<Tenant> findAll() {
        return table.scan().items().stream().map(TenantItem::toDomain).toList();
    }

    @Override
    public List<Tenant> findByStatus(TenantStatus status) {
        Expression filter = Expression.builder()
                .expression("#status = :status")
                .expressionNames(Map.of("#status", "status"))
                .expressionValues(Map.of(":status", AttributeValue.fromS(status.name())))
                .build();
        return table.scan(b -> b.filterExpression(filter)).items().stream().map(TenantItem::toDomain).toList();
    }
}
