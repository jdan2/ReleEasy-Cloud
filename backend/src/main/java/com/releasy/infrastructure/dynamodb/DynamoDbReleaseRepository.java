package com.releasy.infrastructure.dynamodb;

import com.releasy.domain.release.Release;
import com.releasy.domain.release.ReleaseRepository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;

public class DynamoDbReleaseRepository implements ReleaseRepository {

    private final DynamoDbTable<ReleaseItem> table;

    public DynamoDbReleaseRepository(DynamoDbEnhancedClient client, String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(ReleaseItem.class));
    }

    @Override
    public Release save(Release release) {
        table.putItem(ReleaseItem.from(release));
        return release;
    }

    @Override
    public Optional<Release> findById(String appId, String releaseId) {
        Key key = Key.builder().partitionValue(appId).sortValue(releaseId).build();
        return Optional.ofNullable(table.getItem(key)).map(ReleaseItem::toDomain);
    }

    @Override
    public List<Release> findByAppId(String appId) {
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder().partitionValue(appId).build());
        return table.query(b -> b.queryConditional(condition).scanIndexForward(false)).items().stream()
                .map(ReleaseItem::toDomain)
                .toList();
    }
}
