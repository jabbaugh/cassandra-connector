package com.mulesoft.mule.cassandradb.utils.builders;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.schemabuilder.*;
import com.mulesoft.mule.cassandradb.utils.ReplicationStrategy;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.Map;

public class HelperStatements {

    public static SchemaStatement createKeyspaceStatement(String keyspaceName, Map<String, Object> replicationStrategy) {
        //build create keyspace statement if not exists
        CreateKeyspace createKeyspaceStatement = SchemaBuilder.createKeyspace(keyspaceName).ifNotExists();

        return createKeyspaceStatement.with().replication(ReplicationStrategy.buildReplicationStrategy(replicationStrategy));
    }

    public static SchemaStatement dropKeyspaceStatement(String keyspaceName) {
        return SchemaBuilder.dropKeyspace(keyspaceName).ifExists();
    }

    public static SchemaStatement createTable(String tableName, String keyspaceName, Map<String, Object> partitionKey) {
        //build drop keyspace statement
        ImmutablePair<String, DataType> partitionKeyInfo = resolvePartitionKey(partitionKey);

        return SchemaBuilder.createTable(keyspaceName, tableName).ifNotExists().
                addPartitionKey(partitionKeyInfo.getLeft(), partitionKeyInfo.getRight());
    }

    public static SchemaStatement dropTable(String tableName, String keyspaceName) {
        return SchemaBuilder.dropTable(keyspaceName, tableName).ifExists();
    }

    /**
     * extract partition key column information or return default values
     * @param partitionKey
     * @return
     */
    private static ImmutablePair<String, DataType> resolvePartitionKey(Map<String, Object> partitionKey) {
        MutablePair<String, DataType> partitionKeyInfo = new MutablePair<String, DataType>();

        if (partitionKey == null) {
            partitionKeyInfo.setLeft("dummy_partitionKey");
            partitionKeyInfo.setRight(DataType.text());
        } else {
            partitionKeyInfo.setLeft(String.valueOf(partitionKey.get("partitionKeyColumnName")));
            partitionKeyInfo.setRight((DataType) partitionKey.get("dataType"));
        }

        return new ImmutablePair<String, DataType>(partitionKeyInfo.getLeft(), partitionKeyInfo.getRight());
    }
}