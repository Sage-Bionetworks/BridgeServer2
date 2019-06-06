package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.sms.SmsMessage;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DynamoSmsMessageDaoTest {
    private static final String PHONE_NUMBER = "+12065550123";

    private DynamoSmsMessageDao dao;
    private DynamoDBMapper mockMapper;

    @BeforeMethod
    public void before() {
        mockMapper = mock(DynamoDBMapper.class);

        dao = new DynamoSmsMessageDao();
        dao.setMapper(mockMapper);
    }

    @Test
    public void getMostRecentMessage() {
        // Mock mapper.
        DynamoSmsMessage mapperOutput = new DynamoSmsMessage();

        QueryResultPage<DynamoSmsMessage> resultPage = new QueryResultPage<>();
        resultPage.setResults(ImmutableList.of(mapperOutput));

        when(mockMapper.queryPage(eq(DynamoSmsMessage.class), any())).thenReturn(resultPage);

        // Execute and validate output matches.
        SmsMessage daoOutput = dao.getMostRecentMessage(PHONE_NUMBER);
        assertSame(daoOutput, mapperOutput);

        // Verify query.
        ArgumentCaptor<DynamoDBQueryExpression> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(mockMapper).queryPage(eq(DynamoSmsMessage.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoSmsMessage> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getPhoneNumber(), PHONE_NUMBER);
        assertFalse(query.isScanIndexForward());
        assertEquals(query.getLimit().intValue(), 1);
    }

    @Test
    public void getMostRecentMessage_EmptyList() {
        // Mock mapper.
        QueryResultPage<DynamoSmsMessage> resultPage = new QueryResultPage<>();
        resultPage.setResults(ImmutableList.of());

        when(mockMapper.queryPage(eq(DynamoSmsMessage.class), any())).thenReturn(resultPage);

        // Execute and validate.
        SmsMessage daoOutput = dao.getMostRecentMessage(PHONE_NUMBER);
        assertNull(daoOutput);

        // Query is already verified in previous test.
    }

    @Test
    public void logMessage() {
        // Execute.
        DynamoSmsMessage daoInput = new DynamoSmsMessage();
        dao.logMessage(daoInput);

        // Validate mapper called with matching input.
        verify(mockMapper).save(same(daoInput));
    }
}
