package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;

public class DynamoOAuthAccessGrantDaoTest extends Mockito {

    static final String VENDOR_ID = "aVendorId";
    static final String OFFSET_KEY = "offsetKey";
    static final String KEY = TEST_APP_ID + ":" + VENDOR_ID;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    QueryResultPage<DynamoOAuthAccessGrant> mockQueryPage;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoOAuthAccessGrant>> queryCaptor;
    
    @Captor
    ArgumentCaptor<DynamoOAuthAccessGrant> keyCaptor;
    
    @InjectMocks
    DynamoOAuthAccessGrantDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getAccessGrants() {
        DynamoOAuthAccessGrant grant1 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant2 = new DynamoOAuthAccessGrant();
        List<DynamoOAuthAccessGrant> grantList = ImmutableList.of(grant1, grant2);
        when(mockQueryPage.getResults()).thenReturn(grantList);
        when(mockMapper.queryPage(eq(DynamoOAuthAccessGrant.class), any())).thenReturn(mockQueryPage);
        
        ForwardCursorPagedResourceList<OAuthAccessGrant> grants = dao.getAccessGrants(TEST_APP_ID, VENDOR_ID,
                OFFSET_KEY, 5);
        assertEquals(grants.getItems().size(), 2);
        assertEquals(grants.getItems().get(0), grant1);
        assertEquals(grants.getItems().get(1), grant2);
        assertEquals(grants.getRequestParams().get("offsetKey"), OFFSET_KEY);
        assertEquals(grants.getRequestParams().get("pageSize"), new Integer(5));

        verify(mockMapper).queryPage(eq(DynamoOAuthAccessGrant.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoOAuthAccessGrant> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getKey(), KEY);
        assertEquals(query.getLimit(), new Integer(6));
        Condition healthKeyCondition = query.getRangeKeyConditions().get("healthCode");
        assertEquals(healthKeyCondition.getAttributeValueList().get(0).getS(), OFFSET_KEY);
    }
    
    @Test
    public void getAccessGrantsNoOffsetKey() {
        DynamoOAuthAccessGrant grant1 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant2 = new DynamoOAuthAccessGrant();
        List<DynamoOAuthAccessGrant> grantList = ImmutableList.of(grant1, grant2);
        when(mockQueryPage.getResults()).thenReturn(grantList);
        when(mockMapper.queryPage(eq(DynamoOAuthAccessGrant.class), any())).thenReturn(mockQueryPage);
        
        ForwardCursorPagedResourceList<OAuthAccessGrant> grants = dao.getAccessGrants(TEST_APP_ID, VENDOR_ID, null, 5);
        assertEquals(grants.getItems().size(), 2);
        assertEquals(grants.getItems().get(0), grant1);
        assertEquals(grants.getItems().get(1), grant2);
        assertNull(grants.getRequestParams().get("offsetKey"));
        assertEquals(grants.getRequestParams().get("pageSize"), new Integer(5));

        verify(mockMapper).queryPage(eq(DynamoOAuthAccessGrant.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoOAuthAccessGrant> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getKey(), KEY);
        assertEquals(query.getLimit(), new Integer(6));
        assertNull(query.getRangeKeyConditions());
    }
    
    @Test
    public void getAccessGrantsMultiplePages() {
        DynamoOAuthAccessGrant grant1 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant2 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant3 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant4 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant5 = new DynamoOAuthAccessGrant();
        DynamoOAuthAccessGrant grant6 = new DynamoOAuthAccessGrant();
        grant6.setHealthCode("lastRecordHealthCode");
        List<DynamoOAuthAccessGrant> grantList = ImmutableList.of(grant1, grant2, grant3, grant4, grant5, grant6);
        when(mockQueryPage.getResults()).thenReturn(grantList);
        when(mockMapper.queryPage(eq(DynamoOAuthAccessGrant.class), any())).thenReturn(mockQueryPage);
        
        ForwardCursorPagedResourceList<OAuthAccessGrant> grants = dao.getAccessGrants(TEST_APP_ID, VENDOR_ID,
                OFFSET_KEY, 5);
        assertEquals(grants.getNextPageOffsetKey(), "lastRecordHealthCode");
    }    
    
    @Test
    public void getAccessGrant() {
        DynamoOAuthAccessGrant grant = new DynamoOAuthAccessGrant();
        when(mockMapper.load(any())).thenReturn(grant);
        
        OAuthAccessGrant result = dao.getAccessGrant(TEST_APP_ID, VENDOR_ID, HEALTH_CODE);
        assertSame(result, grant);
        
        verify(mockMapper).load(keyCaptor.capture());
        DynamoOAuthAccessGrant key = keyCaptor.getValue();
        assertEquals(key.getKey(), KEY);
        assertEquals(key.getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void saveAccessGrant() {
        DynamoOAuthAccessGrant grant = new DynamoOAuthAccessGrant();
        grant.setVendorId(VENDOR_ID);
        grant.setHealthCode(HEALTH_CODE);
        
        OAuthAccessGrant result = dao.saveAccessGrant(TEST_APP_ID, grant);
        assertSame(result, grant);
        
        verify(mockMapper).save(keyCaptor.capture());
        DynamoOAuthAccessGrant key = keyCaptor.getValue();
        assertEquals(key.getKey(), KEY); // this was set by the call
        assertSame(key, grant);
    }
    
    @Test
    public void deleteAccessGrant() {
        dao.deleteAccessGrant(TEST_APP_ID, VENDOR_ID, HEALTH_CODE);
        
        verify(mockMapper).delete(keyCaptor.capture());
        DynamoOAuthAccessGrant key = keyCaptor.getValue();
        assertEquals(key.getKey(), KEY); // this was set by the call
        assertEquals(key.getHealthCode(), HEALTH_CODE);
    }
}
