package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.models.DateTimeHolder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

public class DynamoMasterSchedulerStatusDaoTest extends Mockito {
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @InjectMocks
    private DynamoMasterSchedulerStatusDao dao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        dao = new DynamoMasterSchedulerStatusDao();
        dao.setMapper(mockMapper);
    }
    
    @Test
    public void getLastProcessedTime() {
        DateTimeHolder timestamp = new DateTimeHolder.Builder().withDateTime(
                new DateTime(TIMESTAMP)).build();
        DynamoMasterSchedulerStatus status = new DynamoMasterSchedulerStatus();
        status.setHashKey(DynamoMasterSchedulerStatusDao.SCHEDULER_STATUS_HASH_KEY);
        status.setLastProcessedTime(TIMESTAMP.getMillis());
        
        when(mockMapper.load(any())).thenReturn(status);
        
        DateTimeHolder result = dao.getLastProcessedTime();
        assertEquals(result.getDateTime().getMillis(), timestamp.getDateTime().getMillis());
    }
    
    @Test
    public void getLastProcessedTimeIsNull() {
        when(mockMapper.load(any())).thenReturn(null);
        
        DateTimeHolder result = dao.getLastProcessedTime();
        assertNull(result.getDateTime());
    }
}
