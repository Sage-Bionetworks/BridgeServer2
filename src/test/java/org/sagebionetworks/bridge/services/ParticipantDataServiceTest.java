package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.testng.Assert.assertEquals;

public class ParticipantDataServiceTest {

    static final String OFFSET_KEY = "anOffsetKey";
    static final int PAGE_SIZE = 10;

    @Mock
    ParticipantDataDao mockDao;

    @Captor
    ArgumentCaptor<ParticipantData> participantDataCaptor;

    @Spy
    ParticipantDataService service;

    ForwardCursorPagedResourceList<DynamoParticipantData> results;
    ParticipantData result;

    @BeforeMethod
    public void beforeMethod() throws Exception {
        MockitoAnnotations.initMocks(this);

        service.setParticipantDataDao(mockDao);

        List<DynamoParticipantData> list = new ArrayList<>();
        list.add(createParticipantData("a", "b"));
        list.add(createParticipantData("c", "d"));
        results = new ForwardCursorPagedResourceList<DynamoParticipantData>(list, OFFSET_KEY);

        result = createParticipantData("a", "b");
    }

    @Test
    public void testGetAllParticipantData() {
        doReturn(results).when(mockDao).getAllParticipantData(TestConstants.TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);

        ForwardCursorPagedResourceList<ParticipantData> retrieved = service.getAllParticipantData(
                TestConstants.TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);

        verify(mockDao).getAllParticipantData(TestConstants.TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);
        assertEquals(retrieved, results);
    }

    @Test
    public void testGetParticipantData() {
        doReturn(result).when(mockDao).getParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER);

        ParticipantData retrieved = service.getParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER);

        verify(mockDao).getParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER);
        assertEquals(retrieved, result);
    }

    @Test
    public void testSaveParticipantData() {
        ParticipantData participantData = createParticipantData("c", "d");
        service.saveParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER, participantData);

        verify(mockDao).saveParticipantData(participantDataCaptor.capture());
        ParticipantData retrieved = participantDataCaptor.getValue();
        assertEquals(retrieved, participantData);
        assertEquals(retrieved.getUserId(), TestConstants.TEST_USER_ID);
        assertEquals(retrieved.getIdentifier(), TestConstants.IDENTIFIER);
        assertEquals(retrieved.getData().get("field1").textValue(), "c");
        assertEquals(retrieved.getData().get("field2").textValue(), "d");
    }

    @Test
    public void testDeleteAllParticipantData() {
        service.deleteAllParticipantData(TestConstants.TEST_USER_ID);

        verify(mockDao).deleteAllParticipantData(TestConstants.TEST_USER_ID);
    }

    @Test
    public void testDeleteParticipantData() {
        doReturn(result).when(mockDao).getParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER);

        service.deleteParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER);

        verify(mockDao).deleteParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testDeleteParticipantDataValidatesUserId() {
        service.deleteParticipantData("", TestConstants.IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testDeleteParticipantDataValidatesIdentifier() {
        service.deleteParticipantData(TestConstants.TEST_USER_ID, "");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetAllDataPageSizeTooSmall() {
        service.getAllParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER, API_MINIMUM_PAGE_SIZE - 1);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetAllDataPageSizeTooLarge() {
        service.getAllParticipantData(TestConstants.TEST_USER_ID, TestConstants.IDENTIFIER, API_MAXIMUM_PAGE_SIZE + 1);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testGetAllDataNoData() {
        service.getAllParticipantData("", "", PAGE_SIZE);
    }

    private static DynamoParticipantData createParticipantData(String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        DynamoParticipantData participantData = new DynamoParticipantData();
        participantData.setUserId(TestConstants.TEST_USER_ID);
        participantData.setIdentifier(TestConstants.IDENTIFIER);
        participantData.setData(node);
        return participantData;
    }
}