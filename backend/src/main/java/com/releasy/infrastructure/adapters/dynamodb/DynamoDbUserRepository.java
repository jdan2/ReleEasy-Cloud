package com.releasy.infrastructure.adapters.dynamodb;

import com.releasy.domain.user.User;
import com.releasy.domain.user.UserRepository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;

public class DynamoDbUserRepository implements UserRepository {

    /** Solo escribe si todavía no hay ningún elemento con ese email. */
    private static final Expression EMAIL_NOT_TAKEN =
            Expression.builder().expression("attribute_not_exists(email)").build();

    private final DynamoDbTable<UserItem> table;

    public DynamoDbUserRepository(DynamoDbEnhancedClient client, String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(UserItem.class));
    }

    @Override
    public boolean saveIfAbsent(User user) {
        try {
            table.putItem(b -> b.item(UserItem.from(user)).conditionExpression(EMAIL_NOT_TAKEN));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Key key = Key.builder().partitionValue(User.normalizeEmail(email)).build();
        return Optional.ofNullable(table.getItem(key)).map(UserItem::toDomain);
    }
}
