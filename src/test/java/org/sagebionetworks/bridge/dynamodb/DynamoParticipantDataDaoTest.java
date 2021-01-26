package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.mail.Part;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertSame;

public class DynamoParticipantDataDaoTest extends Mockito {

    static final String USER_ID = "aUserId";
    static final String IDENTIFIER = "anIdentifier";
    static final String OFFSET_KEY = "anOffsetKey";
    static final int PAGE_SIZE = 10;

    DynamoParticipantData participantData0;
    DynamoParticipantData participantData1;
    DynamoParticipantData participantData2;
    DynamoParticipantData participantData3;
    ImmutableList<DynamoParticipantData> participantDataList;

    @Mock
    DynamoDBMapper mockMapper;

    @Mock
    PaginatedQueryList<DynamoParticipantData> mockQueryList;

    @Mock
    QueryResultPage<DynamoParticipantData> mockQueryPage;

    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantData>> queryCaptor;

    @Captor
    ArgumentCaptor<ParticipantData> participantDataCaptor;

    @Captor
    ArgumentCaptor<List<DynamoParticipantData>> dataListCaptor;

    @InjectMocks
    DynamoParticipantDataDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        participantData0 = createParticipantData("a", "b");
        participantData1 = createParticipantData("c", "d");
        participantData2 = createParticipantData("e", "f");
        participantData3 = createParticipantData("g", "h");
        participantDataList = ImmutableList.of(participantData0, participantData1, participantData2, participantData3);
    }

    @Test
    public void testGetParticipantData() {
        when(mockMapper.query(eq(DynamoParticipantData.class), any())).thenReturn(mockQueryList);

        ForwardCursorPagedResourceList<ParticipantData> result = dao.getParticipantData(USER_ID, OFFSET_KEY, PAGE_SIZE);

        assertEquals(result.getRequestParams().get("userId"), USER_ID);
        assertEquals(result.getRequestParams().get("offsetKey"), OFFSET_KEY);
        assertEquals(result.getItems(), mockQueryList);

        verify(mockMapper).query(eq(DynamoParticipantData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoParticipantData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getUserId(), USER_ID);
        Condition rangeCondition = query.getRangeKeyConditions().get("identifier");
        assertEquals(rangeCondition.getAttributeValueList().get(0).getS(), IDENTIFIER);
    }

    @Test
    public void testGetParticipantDataNoOffsetKey() {

    }

    @Test
    public void getParticipantDataMultiplePages() {

    }

    @Test
    public void testGetParticipantDataRecord() {
    }

    @Test
    public void testSaveParticipantData() {
        dao.saveParticipantData(participantData0);

        verify(mockMapper).save(participantDataCaptor.capture());
        ParticipantData participantData = participantDataCaptor.getValue();
        assertSame(participantData, participantData0);
        assertEquals(participantData.getUserId(), USER_ID);
    }

    @Test
    public void testDeleteAllParticipantData() {
        when(mockMapper.query(eq(DynamoParticipantData.class), any())).thenReturn(mockQueryList);

        dao.deleteAllParticipantData(participantData0.getUserId());

        verify(mockMapper).query(eq(DynamoParticipantData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoParticipantData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getUserId(), participantData0.getUserId());

        verify(mockMapper).batchDelete(dataListCaptor.capture());

        assertEquals(dataListCaptor.getValue(), mockQueryList);
    }

    @Test
    public void deleteAllParticipantDataNoData() {
        when(mockMapper.query(eq(DynamoParticipantData.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(true);

        dao.deleteAllParticipantData(participantData0.getUserId());

        verify(mockMapper, never()).batchDelete(dataListCaptor.capture());
    }

    @Test
    public void testDeleteParticipantDataRecord() {
        when(mockMapper.load(any())).thenReturn(participantData0);

        String identifier = participantData0.getIdentifier();
        dao.deleteParticipantData(participantData0.getUserId(), identifier);

        verify(mockMapper).load(participantDataCaptor.capture());
        assertEquals(participantDataCaptor.getValue().getUserId(), participantData0.getUserId());
        assertEquals(participantDataCaptor.getValue().getIdentifier(), identifier);
    }

    @Test
    public void testDeleteParticipantDataRecordNoRecord() {
        String identifier = participantData0.getIdentifier();
        dao.deleteParticipantData(participantData0.getUserId(), identifier);

        verify(mockMapper, never()).delete(any());
    }

    private static DynamoParticipantData createParticipantData(String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        DynamoParticipantData participantData = new DynamoParticipantData();
        participantData.setUserId(USER_ID);
        participantData.setIdentifier(IDENTIFIER);
        participantData.setData(node);
        return participantData;
    }
}