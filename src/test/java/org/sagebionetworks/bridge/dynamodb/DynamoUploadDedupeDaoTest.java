package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BETWEEN;
import static org.sagebionetworks.bridge.BridgeConstants.LOCAL_TIME_ZONE;
import static org.sagebionetworks.bridge.dynamodb.DynamoUploadDedupeDao.NUM_DAYS_BEFORE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.Condition;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DynamoUploadDedupeDaoTest extends Mockito {
    private static final String HEALTHCODE = "test-healthcode";
    private static final String UPLOAD_ID = "original-upload";
    private static final String UPLOAD_MD5 = "test-md5";
    private static final DateTime UPLOAD_REQUESTED_ON = DateTime.parse("2016-02-15T10:26:45-0800");

    @InjectMocks
    DynamoUploadDedupeDao dao;

    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedQueryList<DynamoUploadDedupe> mockQueryList;

    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoUploadDedupe>> queryCaptor;
    
    @Captor
    ArgumentCaptor<DynamoUploadDedupe> dedupeCaptor;
    
    @BeforeMethod
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void registerUpload() {
        dao.registerUpload(HEALTHCODE, UPLOAD_MD5, UPLOAD_REQUESTED_ON, UPLOAD_ID);
        
        verify(mockMapper).save(dedupeCaptor.capture());
        DynamoUploadDedupe dedupe = dedupeCaptor.getValue();
        assertEquals(dedupe.getHealthCode(), HEALTHCODE);
        assertEquals(dedupe.getOriginalUploadId(), UPLOAD_ID);
        assertEquals(dedupe.getUploadMd5(), UPLOAD_MD5);
        assertEquals(dedupe.getUploadRequestedDate(), UPLOAD_REQUESTED_ON.withZone(LOCAL_TIME_ZONE).toLocalDate());
        assertEquals(dedupe.getUploadRequestedOn(), UPLOAD_REQUESTED_ON.getMillis());
    }
    
    @Test
    public void getDuplicate() {
        DynamoUploadDedupe dedupe = new DynamoUploadDedupe();
        dedupe.setOriginalUploadId(UPLOAD_ID);
        dedupe.setUploadRequestedOn(UPLOAD_REQUESTED_ON.getMillis());
        
        when(mockMapper.query(eq(DynamoUploadDedupe.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(false);
        when(mockQueryList.get(0)).thenReturn(dedupe);
        
        String originalUploadId = dao.getDuplicate(HEALTHCODE, UPLOAD_MD5,
                UPLOAD_REQUESTED_ON);
        assertEquals(originalUploadId, UPLOAD_ID);
        
        verify(mockMapper).query(eq(DynamoUploadDedupe.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoUploadDedupe> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getHealthCode(), HEALTHCODE);
        assertEquals(query.getHashKeyValues().getUploadMd5(), UPLOAD_MD5);
        
        long startOfRange = UPLOAD_REQUESTED_ON.minusDays(NUM_DAYS_BEFORE).getMillis();
        long endOfRange = UPLOAD_REQUESTED_ON.getMillis();
        
        Condition requestedOnCondition = query.getRangeKeyConditions().get("uploadRequestedOn");
        assertEquals(requestedOnCondition.getComparisonOperator(), BETWEEN.name());
        assertEquals(requestedOnCondition.getAttributeValueList().get(0).getN(), Long.toString(startOfRange));
        assertEquals(requestedOnCondition.getAttributeValueList().get(1).getN(), Long.toString(endOfRange));
    }

    @Test
    public void getDuplicateNotFound() {
        when(mockMapper.query(eq(DynamoUploadDedupe.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(true);
        
        String originalUploadId = dao.getDuplicate(HEALTHCODE, UPLOAD_MD5, UPLOAD_REQUESTED_ON);
        assertNull(originalUploadId);
    }
}
