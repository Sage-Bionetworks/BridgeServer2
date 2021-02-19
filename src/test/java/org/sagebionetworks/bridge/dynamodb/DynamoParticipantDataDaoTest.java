package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
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
import org.mockito.Spy;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.sagebionetworks.bridge.models.ResourceList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

public class DynamoParticipantDataDaoTest extends Mockito {

    private static final String OFFSET_KEY = "anOffsetKey";
    private static final int PAGE_SIZE = 10;

    DynamoParticipantData participantData0;
    ImmutableList<DynamoParticipantData> participantDataList;

    @Mock
    DynamoDBMapper mockMapper;

    @Mock
    PaginatedQueryList<DynamoParticipantData> mockQueryList;

    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantData>> queryCaptor;

    @Captor
    ArgumentCaptor<ParticipantData> participantDataCaptor;

    @Captor
    ArgumentCaptor<List<DynamoParticipantData>> dataListCaptor;

    @InjectMocks
    @Spy
    DynamoParticipantDataDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        participantData0 = createParticipantData("a", "b");

        // Create list with a size greater than PageSize
        participantDataList = ImmutableList.of(
            participantData0,
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
    public void testGetAllParticipantData() {
        // Mock dependencies
        DynamoParticipantData participantData = new DynamoParticipantData();
        List<DynamoParticipantData> participantDataList = ImmutableList.of(participantData);
        doReturn(participantDataList).when(dao).queryHelper(any());

        // Execute
        ForwardCursorPagedResourceList<ParticipantData> result =
                dao.getAllParticipantData(TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);
        assertEquals(result.getItems().size(), 1);
        assertSame(result.getItems().get(0), participantData);
        assertNull(result.getNextPageOffsetKey());

        //Validate
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoParticipantData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getUserId(), TEST_USER_ID);
        assertEquals(query.getLimit().intValue(), PAGE_SIZE + 1);
        assertEquals(query.getRangeKeyConditions().size() , 1);

        Condition expectedRangeKeyCondition = new Condition().withComparisonOperator(ComparisonOperator.GE)
                .withAttributeValueList(new AttributeValue().withS(OFFSET_KEY));
        Condition actualRangeKeyCondition = query.getRangeKeyConditions().get("offsetKey");
        assertEquals(actualRangeKeyCondition.getAttributeValueList().size(), 1);
        assertEquals(actualRangeKeyCondition, expectedRangeKeyCondition);
    }

    @Test
    public void testGetParticipantDataNoOffsetKey() {
        doReturn(participantDataList).when(dao).queryHelper(any());

        ForwardCursorPagedResourceList<ParticipantData> result =
                dao.getAllParticipantData(TEST_USER_ID, null, PAGE_SIZE);
        assertEquals(result.getItems().size(), 10);
        assertNull(result.getRequestParams().get(ResourceList.OFFSET_KEY));

        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoParticipantData> query = queryCaptor.getValue();
        assertNull(query.getRangeKeyConditions());
    }

    @Test
    public void getParticipantDataMultiplePages() {
        List<DynamoParticipantData> list = new ArrayList<>(participantDataList);

        doReturn(list).when(dao).queryHelper(any());

        ForwardCursorPagedResourceList<ParticipantData> result=
                dao.getAllParticipantData(TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);
        assertEquals(result.getItems().size(), PAGE_SIZE);
        assertEquals(result.getNextPageOffsetKey(), list.get(PAGE_SIZE).getIdentifier());
    }

    @Test
    public void testGetParticipantData() {
        doReturn(participantData0).when(mockMapper).load(any());

        dao.getParticipantData(TEST_USER_ID, IDENTIFIER);

        verify(mockMapper).load(participantDataCaptor.capture());
        ParticipantData participantData = participantDataCaptor.getValue();
        assertEquals(participantData.getUserId(), TEST_USER_ID);
        assertEquals(participantData.getIdentifier(), IDENTIFIER);
    }

    @Test
    public void testSaveParticipantData() {
        dao.saveParticipantData(participantData0);

        verify(mockMapper).save(participantData0);
    }

    @Test
    public void testDeleteAllParticipantData() {
        when(mockMapper.query(eq(DynamoParticipantData.class), any())).thenReturn(mockQueryList);

        dao.deleteAllParticipantData(participantData0.getUserId());

        verify(mockMapper).query(eq(DynamoParticipantData.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoParticipantData> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getUserId(), participantData0.getUserId());

        verify(mockMapper).batchDelete(mockQueryList);
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
        verify(mockMapper).delete(participantData0);
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
        participantData.setUserId(TEST_USER_ID);
        participantData.setIdentifier(IDENTIFIER);
        participantData.setData(node);
        return participantData;
    }
}