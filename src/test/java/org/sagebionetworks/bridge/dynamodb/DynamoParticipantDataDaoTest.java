package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
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
import org.sagebionetworks.bridge.models.ResourceList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
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

        // Create list with a size greater than PageSize
        participantDataList = ImmutableList.of(
            createParticipantData("a", "b"),
            createParticipantData("c", "d"),
            createParticipantData("e", "f"),
            createParticipantData("g", "h"),
            createParticipantData("i", "j"),
            createParticipantData("k", "l"),
            createParticipantData("m", "o"),
            createParticipantData("p", "q"),
            createParticipantData("r", "s"),
            createParticipantData("t", "u"),
            createParticipantData("v", "w")
        );
    }

    @Test
    public void testGetParticipantData() {
        when(mockMapper.query(eq(DynamoParticipantData.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.iterator()).thenReturn(participantDataList.iterator());
        ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantData>> queryCaptor = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        Condition expectedRangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.GE)
                .withAttributeValueList(new AttributeValue().withS(OFFSET_KEY));

        ForwardCursorPagedResourceList<ParticipantData> result = dao.getParticipantData(USER_ID, OFFSET_KEY, PAGE_SIZE);

        assertEquals(result.getItems(), participantDataList.subList(0, PAGE_SIZE));
        assertEquals(result.getItems().get(0).getUserId(), USER_ID);
        assertEquals(result.getNextPageOffsetKey(), result.getItems().get(PAGE_SIZE - 1).getIdentifier());
        verify(mockMapper).query(any(), queryCaptor.capture());
        assertEquals(queryCaptor.getValue().getRangeKeyConditions().get("offsetKey"), expectedRangeKeyCondition);
    }

    //TODO another test with list less than 10 to assert that nextPageOffsetKey is null

    @Test
    public void testGetParticipantDataNoOffsetKey() {
        when(mockMapper.query(eq(DynamoParticipantData.class), any())).thenReturn(mockQueryList);

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