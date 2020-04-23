package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.GE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_KEY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import java.util.List;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.OAuthAccessGrantDao;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

@Component
public class DynamoOAuthAccessGrantDao implements OAuthAccessGrantDao {
    
    private DynamoDBMapper mapper;

    @Resource(name = "oauthAccessGrantMapper")
    final void setOAuthAccessGrantMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Override
    public ForwardCursorPagedResourceList<OAuthAccessGrant> getAccessGrants(String appId, String vendorId,
            String offsetKey, int pageSize) {
        checkNotNull(appId);
        checkArgument(isNotBlank(vendorId));
        
        int pageSizeWithIndicatorRecord = pageSize+1;
        
        DynamoOAuthAccessGrant grantKey = new DynamoOAuthAccessGrant();
        grantKey.setKey(getGrantKey(appId, vendorId));

        DynamoDBQueryExpression<DynamoOAuthAccessGrant> query = new DynamoDBQueryExpression<DynamoOAuthAccessGrant>()
                .withHashKeyValues(grantKey)
                .withLimit(pageSizeWithIndicatorRecord);
        if (offsetKey != null) {
            Condition healthCodeCondition = new Condition().withComparisonOperator(GE)
                    .withAttributeValueList(new AttributeValue().withS(offsetKey));
            query.withRangeKeyCondition("healthCode", healthCodeCondition);
        }
        
        QueryResultPage<DynamoOAuthAccessGrant> page = mapper.queryPage(DynamoOAuthAccessGrant.class, query);
        int pageLimit = Math.min(page.getResults().size(), pageSizeWithIndicatorRecord);
        
        List<OAuthAccessGrant> list = Lists.newArrayListWithCapacity(pageLimit);
        for (int i = 0; i < pageLimit; i++) {
            list.add( page.getResults().get(i) );
        }
                
        String nextPageOffsetKey = null;
        if (list.size() == pageSizeWithIndicatorRecord) {
            nextPageOffsetKey = Iterables.getLast(list).getHealthCode();
        }

        int limit = Math.min(list.size(), pageSize);
        return new ForwardCursorPagedResourceList<OAuthAccessGrant>(list.subList(0, limit), nextPageOffsetKey)
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_KEY, offsetKey);
    }
    
    @Override
    public OAuthAccessGrant getAccessGrant(String appId, String vendorId, String healthCode) {
        checkNotNull(appId);
        checkArgument(isNotBlank(vendorId));
        checkArgument(isNotBlank(healthCode));
        
        DynamoOAuthAccessGrant grantKey = new DynamoOAuthAccessGrant();
        grantKey.setKey(getGrantKey(appId, vendorId));
        grantKey.setHealthCode(healthCode);
        
        return mapper.load(grantKey);
    }

    @Override
    public OAuthAccessGrant saveAccessGrant(String appId, OAuthAccessGrant grant) {
        checkNotNull(appId);
        checkArgument(isNotBlank(grant.getVendorId()));
        checkArgument(isNotBlank(grant.getHealthCode()));
        
        ((DynamoOAuthAccessGrant)grant).setKey(getGrantKey(appId, grant.getVendorId()));
        mapper.save(grant);
        
        return grant;
    }

    @Override
    public void deleteAccessGrant(String appId, String vendorId, String healthCode) {
        checkNotNull(appId);
        checkArgument(isNotBlank(vendorId));
        checkArgument(isNotBlank(healthCode));
        
        DynamoOAuthAccessGrant grantKey = new DynamoOAuthAccessGrant();
        grantKey.setKey(getGrantKey(appId, vendorId));
        grantKey.setHealthCode(healthCode);
        
        mapper.delete(grantKey);
    }

    private String getGrantKey(String appId, String vendorId) {
        return appId + ":" + vendorId;
    }
}
