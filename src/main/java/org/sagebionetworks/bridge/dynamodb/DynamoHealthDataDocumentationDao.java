package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.HealthDataDocumentationDao;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.util.List;

@Component
public class DynamoHealthDataDocumentationDao implements HealthDataDocumentationDao {
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the HealthDataDocumentation table, configured by Spring. */
    @Resource(name = "healthDataDbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public HealthDataDocumentation createOrUpdateDocumentation(@Nonnull HealthDataDocumentation documentation, String s3Key) {
        HealthDataDocumentation dynamoDocumentation = (DynamoHealthDataDocumentation) documentation;

        if (dynamoDocumentation.getIdentifier() == null) {
            // Documentation doesn't have ID assigned yet (new documentation). Create ID and assign it.
            dynamoDocumentation.setIdentifier(BridgeUtils.generateGuid());

            // Update created on/by attributes.
            dynamoDocumentation.setCreatedOn(DateTime.now());
        } else {
            // Update modified on/by attributes.
            dynamoDocumentation.setModifiedOn(DateTime.now());
        }

        dynamoDocumentation.setS3Key(s3Key);

        // Save to DynamoDB.
        mapper.save(dynamoDocumentation);
        return dynamoDocumentation;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteDocumentationForParentId(@Nonnull String parentId) {
        // Query the records that we need to delete.
        DynamoHealthDataDocumentation key = new DynamoHealthDataDocumentation();
        key.setParentId(parentId);

        DynamoDBQueryExpression<DynamoHealthDataDocumentation> query =
                new DynamoDBQueryExpression<DynamoHealthDataDocumentation>().withHashKeyValues(key);
        List<DynamoHealthDataDocumentation> docsToDelete = queryHelper(query);

        // Batch delete.
        if (!docsToDelete.isEmpty()) {
            List<DynamoDBMapper.FailedBatch> failures = mapper.batchDelete(docsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteDocumentationForIdentifier(@Nonnull String identifier, @Nonnull String parentId) {
        DynamoHealthDataDocumentation hashKey = new DynamoHealthDataDocumentation();
        hashKey.setParentId(parentId);
        hashKey.setIdentifier(identifier);

        DynamoHealthDataDocumentation documentationToDelete = mapper.load(hashKey);
        if (documentationToDelete != null) {
            mapper.delete(documentationToDelete);
        }
    }

    /** {@inheritDoc} */
    @Override
    public HealthDataDocumentation getDocumentationById(@Nonnull String identifier, @Nonnull String parentId) {
        DynamoHealthDataDocumentation hashKey = new DynamoHealthDataDocumentation();
        hashKey.setIdentifier(identifier);
        hashKey.setParentId(parentId);

        return mapper.load(hashKey);
    }

    /** {@inheritDoc} */
    @Override
    public ForwardCursorPagedResourceList<HealthDataDocumentation> getDocumentationForParentId(@Nonnull String parentId,
                                                                                    int pageSize, String offsetKey) {
        // Create hash key.
        DynamoHealthDataDocumentation key = new DynamoHealthDataDocumentation();
        key.setParentId(parentId);
        int pageSizeWithIndicatorRecord = pageSize + 1;

        // Create query.
        DynamoDBQueryExpression<DynamoHealthDataDocumentation> query =
                new DynamoDBQueryExpression<DynamoHealthDataDocumentation>().withHashKeyValues(key)
                        .withLimit(pageSizeWithIndicatorRecord);

        // Add offset key condition to query.
        if (offsetKey != null) {
            Condition condition = new Condition().withComparisonOperator(ComparisonOperator.GE)
                    .withAttributeValueList(new AttributeValue().withS(offsetKey));
            query.withRangeKeyCondition("identifier", condition);
        }

        // Query.
        List<DynamoHealthDataDocumentation> dynamoList = queryHelper(query);
        List<HealthDataDocumentation> list = ImmutableList.copyOf(dynamoList);
        list = list.size() <= pageSizeWithIndicatorRecord ? list : list.subList(0, pageSizeWithIndicatorRecord);

        // Calculate nextOffsetKey, if present.
        String nextOffsetKey = null;
        if (list.size() == pageSizeWithIndicatorRecord) {
            nextOffsetKey = Iterables.getLast(list).getIdentifier();
        }

        int limit = Math.min(list.size(), pageSize);
        return new ForwardCursorPagedResourceList<>(list.subList(0, limit), nextOffsetKey);
    }

    List<DynamoHealthDataDocumentation> queryHelper(DynamoDBQueryExpression<DynamoHealthDataDocumentation> query) {
        return mapper.query(DynamoHealthDataDocumentation.class, query);
    }
}