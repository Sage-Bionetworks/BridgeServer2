package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

public class DynamoHealthCodeDaoTest {

    @Mock
    private DynamoDBMapper mapper;
    
    @Captor
    private ArgumentCaptor<DynamoHealthCode> codeCaptor;
    
    private DynamoHealthCodeDao healthCodeDao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        healthCodeDao = new DynamoHealthCodeDao();
        healthCodeDao.setMapper(mapper);
    }
    

    @Test
    public void successfullyRetrieveStudyId() {
        DynamoHealthCode code = new DynamoHealthCode();
        code.setCode("healthCode");
        code.setAppId(TEST_APP_ID);
        code.setVersion(1L);
        when(mapper.load(any())).thenReturn(code);
        
        String result = healthCodeDao.getStudyIdentifier("healthCode");
        
        verify(mapper).load(codeCaptor.capture());
        
        assertEquals(result, TEST_APP_ID);
        assertEquals(codeCaptor.getValue().getCode(), "healthCode");
    }
    
    @Test
    public void noRecord() {
        when(mapper.load(any())).thenReturn(null);
        
        assertNull(healthCodeDao.getStudyIdentifier("healthCode"));
    }
}
