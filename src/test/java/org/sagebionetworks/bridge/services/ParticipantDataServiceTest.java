package org.sagebionetworks.bridge.services;

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
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ParticipantDataServiceTest extends Mockito {

    private static final String OFFSET_KEY = "anOffsetKey";
    private static final int PAGE_SIZE = 10;

    @Mock
    ParticipantDataDao mockDao;
    
    @Mock
    CacheProvider mockCacheProvider;

    @Captor
    ArgumentCaptor<ParticipantData> participantDataCaptor;

    @Spy
    @InjectMocks
    ParticipantDataService service;

    ForwardCursorPagedResourceList<ParticipantData> results;
    ParticipantData result;

    @BeforeMethod
    public void beforeMethod() throws Exception {
        MockitoAnnotations.initMocks(this);

        List<ParticipantData> list = new ArrayList<>();
        list.add(createParticipantData("a", "b"));
        list.add(createParticipantData("c", "d"));
        results = new ForwardCursorPagedResourceList<ParticipantData>(list, OFFSET_KEY);

        result = createParticipantData("a", "b");
        
        doReturn(MODIFIED_ON).when(service).getModifiedOn();
    }

    @Test
    public void testGetAllParticipantData() {
        doReturn(results).when(mockDao).getAllParticipantData(TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);

        ForwardCursorPagedResourceList<ParticipantData> retrieved = service.getAllParticipantData(
                TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);

        verify(mockDao).getAllParticipantData(TEST_USER_ID, OFFSET_KEY, PAGE_SIZE);
        assertEquals(retrieved, results);
    }

    @Test
    public void testGetParticipantData() {
        doReturn(result).when(mockDao).getParticipantData(TEST_USER_ID, IDENTIFIER);

        ParticipantData retrieved = service.getParticipantData(TEST_USER_ID, IDENTIFIER);

        verify(mockDao).getParticipantData(TEST_USER_ID, IDENTIFIER);
        assertEquals(retrieved, result);
    }

    @Test
    public void testSaveParticipantData() {
        String userId = "aDifferentUserId";
        String identifier = "aDifferentIdentifier";

        ParticipantData participantData = createParticipantData("c", "d");
        service.saveParticipantData(userId, identifier, participantData);

        verify(mockDao).saveParticipantData(participantDataCaptor.capture());
        ParticipantData retrieved = participantDataCaptor.getValue();
        assertEquals(retrieved, participantData);
        assertEquals(retrieved.getUserId(), userId);
        assertEquals(retrieved.getIdentifier(), identifier);
        assertEquals(retrieved.getData().get("field1").textValue(), "c");
        assertEquals(retrieved.getData().get("field2").textValue(), "d");
        
        verify(mockCacheProvider).setObject(
                CacheKey.etag(ParticipantData.class, userId, identifier), MODIFIED_ON);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDeleteAllParticipantData() {
        ParticipantData data1 = ParticipantData.create();
        data1.setIdentifier("foo1");
        ParticipantData data2 = ParticipantData.create();
        data2.setIdentifier("foo2");
        
        ParticipantData data3 = ParticipantData.create();
        data3.setIdentifier("foo3");
        ParticipantData data4 = ParticipantData.create();
        data4.setIdentifier("foo4");
        
        ForwardCursorPagedResourceList<ParticipantData> list1 = 
                new ForwardCursorPagedResourceList<>(ImmutableList.of(data1, data2), "asdf");
        ForwardCursorPagedResourceList<ParticipantData> list2 = 
                new ForwardCursorPagedResourceList<>(ImmutableList.of(data3, data4), "efgh");
        ForwardCursorPagedResourceList<ParticipantData> list3 = 
                new ForwardCursorPagedResourceList<>(ImmutableList.of(), null);

        when(mockDao.getAllParticipantData(TEST_USER_ID, null, API_MAXIMUM_PAGE_SIZE))
            .thenReturn(list1, list2, list3);
        
        service.deleteAllParticipantData(TEST_USER_ID);

        verify(mockCacheProvider).removeObject(
                CacheKey.etag(ParticipantData.class, TEST_USER_ID, "foo1"));
        verify(mockCacheProvider).removeObject(
                CacheKey.etag(ParticipantData.class, TEST_USER_ID, "foo2"));
        verify(mockCacheProvider).removeObject(
                CacheKey.etag(ParticipantData.class, TEST_USER_ID, "foo3"));
        verify(mockCacheProvider).removeObject(
                CacheKey.etag(ParticipantData.class, TEST_USER_ID, "foo4"));
        verify(mockDao).deleteAllParticipantData(TEST_USER_ID);
    }

    @Test
    public void testDeleteParticipantData() {
        doReturn(result).when(mockDao).getParticipantData(TEST_USER_ID, IDENTIFIER);

        service.deleteParticipantData(TEST_USER_ID, IDENTIFIER);

        verify(mockDao).deleteParticipantData(TEST_USER_ID, IDENTIFIER);
        verify(mockCacheProvider).removeObject(
                CacheKey.etag(ParticipantData.class, TEST_USER_ID, IDENTIFIER));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "ParticipantData not found.")
    public void testDeleteParticipantDataNotFound() {
        service.deleteParticipantData(TEST_USER_ID, IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testDeleteParticipantDataValidatesUserId() {
        service.deleteParticipantData("", IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void testDeleteParticipantDataValidatesIdentifier() {
        service.deleteParticipantData(TEST_USER_ID, "");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetAllDataPageSizeTooSmall() {
        service.getAllParticipantData(TEST_USER_ID, IDENTIFIER, API_MINIMUM_PAGE_SIZE - 1);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetAllDataPageSizeTooLarge() {
        service.getAllParticipantData(TEST_USER_ID, IDENTIFIER, API_MAXIMUM_PAGE_SIZE + 1);
    }

    @Test
    public void testGetAllDataNoData() {
        doReturn(new ForwardCursorPagedResourceList<ParticipantData>(new ArrayList<ParticipantData>(), OFFSET_KEY))
                .when(mockDao).getAllParticipantData("", "", PAGE_SIZE);
        ForwardCursorPagedResourceList<ParticipantData> result = service.getAllParticipantData("", "", PAGE_SIZE);
        assertTrue(result.getItems().isEmpty());
    }

    private static ParticipantData createParticipantData(String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ParticipantData participantData = ParticipantData.create();
        participantData.setUserId(TEST_USER_ID);
        participantData.setIdentifier(IDENTIFIER);
        participantData.setData(node);
        return participantData;
    }
}