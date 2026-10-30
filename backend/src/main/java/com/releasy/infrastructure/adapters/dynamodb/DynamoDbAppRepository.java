package com.releasy.infrastructure.adapters.dynamodb;

import com.releasy.domain.app.App;
import com.releasy.domain.app.AppRepository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

public class DynamoDbAppRepository implements AppRepository {

    private final DynamoDbTable<AppItem> table;

    public DynamoDbAppRepository(DynamoDbEnhancedClient client, String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(AppItem.class));
    }

    @Override
    public App save(App app) {
        table.putItem(AppItem.from(app));
        return app;
    }

    @Override
    public Optional<App> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build())).map(AppItem::toDomain);
    }

    @Override
    public List<App> findByTenantId(String tenantId) {
        return queryIndex(AppItem.TENANT_INDEX, tenantId);
    }

    @Override
    public List<App> findByRepository(String repository) {
        return queryIndex(AppItem.REPOSITORY_INDEX, repository);
    }

    private List<App> queryIndex(String indexName, String value) {
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder().partitionValue(value).build());
        return table.index(indexName).query(condition).stream()
                .flatMap(page -> page.items().stream())
                .map(AppItem::toDomain)
                .toList();
    }
}
