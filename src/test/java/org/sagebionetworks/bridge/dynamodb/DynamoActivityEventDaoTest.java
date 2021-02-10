package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.QUESTION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SURVEY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.ANSWERED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DynamoActivityEventDaoTest extends Mockito {
    
    // timestamp is in milliseconds since the epoch, so use UTC here
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    
    private static final DynamoActivityEvent ENROLLMENT_EVENT = new DynamoActivityEvent.Builder()
            .withHealthCode(HEALTH_CODE).withObjectType(ENROLLMENT).withTimestamp(TIMESTAMP).build();

    private static final DynamoActivityEvent SURVEY_FINISHED_EVENT = new DynamoActivityEvent.Builder()
            .withHealthCode(HEALTH_CODE).withObjectType(SURVEY).withEventType(FINISHED).withTimestamp(TIMESTAMP)
            .withObjectId("AAA-BBB-CCC").build();

    private static final DynamoActivityEvent QUESTION_ANSWERED_EVENT = new DynamoActivityEvent.Builder()
            .withHealthCode(HEALTH_CODE).withObjectType(QUESTION).withObjectId("DDD-EEE-FFF").withEventType(ANSWERED)
            .withAnswerValue("anAnswer").withTimestamp(TIMESTAMP).build();

    private static final DynamoActivityEvent ACTIVITY_FINISHED_EVENT = new DynamoActivityEvent.Builder()
            .withHealthCode(HEALTH_CODE).withObjectType(ACTIVITY).withObjectId("AAA-BBB-CCC").withEventType(FINISHED)
            .withTimestamp(TIMESTAMP).build();
    
    private static final DynamoActivityEvent ENROLLMENT_EVENT_WITH_STUDY_ID = new DynamoActivityEvent.Builder()
            .withStudyId(TEST_STUDY_ID).withHealthCode(HEALTH_CODE).withObjectType(ENROLLMENT).withTimestamp(TIMESTAMP).build();

    @InjectMocks
    DynamoActivityEventDao dao;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    PaginatedQueryList<DynamoActivityEvent> queryResults;
    
    @Captor
    ArgumentCaptor<DynamoActivityEvent> eventCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoActivityEvent>> queryCaptor;
    
    @Captor
    ArgumentCaptor<List<DynamoActivityEvent>> listCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void publishEventIsNew() {
        boolean result = dao.publishEvent(SURVEY_FINISHED_EVENT);
        assertTrue(result);
        
        verify(mockMapper).load(eventCaptor.capture());
        DynamoActivityEvent loadKey = eventCaptor.getValue();
        assertEquals(loadKey.getHealthCode(), HEALTH_CODE);
        assertEquals(loadKey.getEventId(), "survey:AAA-BBB-CCC:finished");
        
        verify(mockMapper).save(eventCaptor.capture());
        assertSame(eventCaptor.getValue(), SURVEY_FINISHED_EVENT);
    }
    
    @Test
    public void publishEventIsMutableAndLater() {
        DynamoActivityEvent earlierEvent = new DynamoActivityEvent.Builder().withHealthCode(HEALTH_CODE)
                .withObjectType(SURVEY).withEventType(FINISHED).withTimestamp(TIMESTAMP.minusHours(1))
                .withObjectId("AAA-BBB-CCC").build();
        when(mockMapper.load(any())).thenReturn(earlierEvent);
        
        boolean result = dao.publishEvent(SURVEY_FINISHED_EVENT);
        assertTrue(result);
        
        verify(mockMapper).save(eventCaptor.capture());
        assertSame(eventCaptor.getValue(), SURVEY_FINISHED_EVENT);
    }
    
    @Test
    public void publishEventWithStudyId() {
        boolean result = dao.publishEvent(ENROLLMENT_EVENT_WITH_STUDY_ID);
        assertTrue(result);
        
        verify(mockMapper).save(eventCaptor.capture());
        
        DynamoActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getEventId(), "enrollment");
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
    }
    
    @Test
    public void publishEventIsEarlierFails() {
        DynamoActivityEvent laterEvent = new DynamoActivityEvent.Builder().withHealthCode(HEALTH_CODE)
                .withObjectType(SURVEY).withEventType(FINISHED).withTimestamp(TIMESTAMP.plusHours(1))
                .withObjectId("AAA-BBB-CCC").build();
        when(mockMapper.load(any())).thenReturn(laterEvent);
        
        boolean result = dao.publishEvent(SURVEY_FINISHED_EVENT);
        assertFalse(result);
        
        verify(mockMapper, never()).save(any());
    }
    
    @Test
    public void deletesCustomEvent() {
        DynamoActivityEvent event = new DynamoActivityEvent.Builder().withHealthCode(HEALTH_CODE)
                .withObjectType(CUSTOM).withObjectId("AAA").build();
        
        when(mockMapper.load(any())).thenReturn(event);
        
        boolean result = dao.deleteCustomEvent(event);
        assertTrue(result);
        
        verify(mockMapper).delete(event);
    }

    @Test
    public void deletesCustomEventEventNotFound() {
        DynamoActivityEvent event = new DynamoActivityEvent.Builder().withHealthCode(HEALTH_CODE)
                .withObjectType(CUSTOM).withObjectId("AAA").build();
        
        when(mockMapper.load(any())).thenReturn(null);
        
        boolean result = dao.deleteCustomEvent(event);
        assertFalse(result);
        
        verify(mockMapper, never()).delete(any());
    }
    
    @Test
    public void getActivityEventMap() {
        List<DynamoActivityEvent> savedEvents = ImmutableList.of(ENROLLMENT_EVENT, SURVEY_FINISHED_EVENT,
                QUESTION_ANSWERED_EVENT, ACTIVITY_FINISHED_EVENT);
        when(queryResults.iterator()).thenReturn(savedEvents.iterator());
        when(mockMapper.query(eq(DynamoActivityEvent.class), any())).thenReturn(queryResults);
        
        Map<String, DateTime> results = dao.getActivityEventMap(HEALTH_CODE, null);
        
        assertEquals(results.size(), 4);
        assertEquals(results.get("enrollment"), TIMESTAMP);
        assertEquals(results.get("survey:AAA-BBB-CCC:finished"), TIMESTAMP);
        assertEquals(results.get("question:DDD-EEE-FFF:answered=anAnswer"), TIMESTAMP);
        assertEquals(results.get("activity:AAA-BBB-CCC:finished"), TIMESTAMP);
        
        verify(mockMapper).query(any(), queryCaptor.capture());
        
        DynamoDBQueryExpression<DynamoActivityEvent> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void getActivityEventMapNoEvents() {
        List<DynamoActivityEvent> savedEvents = ImmutableList.of();
        when(queryResults.iterator()).thenReturn(savedEvents.iterator());
        when(mockMapper.query(eq(DynamoActivityEvent.class), any())).thenReturn(queryResults);
        
        Map<String, DateTime> results = dao.getActivityEventMap(HEALTH_CODE, null);
        assertTrue(results.isEmpty());
        
        verify(mockMapper).query(any(), queryCaptor.capture());
        
        DynamoDBQueryExpression<DynamoActivityEvent> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void getActivityEventMapStudyScoped() {
        List<DynamoActivityEvent> savedEvents = ImmutableList.of();
        when(queryResults.iterator()).thenReturn(savedEvents.iterator());
        when(mockMapper.query(eq(DynamoActivityEvent.class), any())).thenReturn(queryResults);
        
        Map<String, DateTime> results = dao.getActivityEventMap(HEALTH_CODE, TEST_STUDY_ID);
        assertTrue(results.isEmpty());
        
        verify(mockMapper).query(any(), queryCaptor.capture());
        
        DynamoDBQueryExpression<DynamoActivityEvent> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
    }
    
    @Test
    public void deleteActivityEventsGlobal() {
        List<DynamoActivityEvent> savedEvents = ImmutableList.of(ENROLLMENT_EVENT, SURVEY_FINISHED_EVENT,
                QUESTION_ANSWERED_EVENT, ACTIVITY_FINISHED_EVENT);
        when(queryResults.toArray()).thenReturn(savedEvents.toArray());
        when(mockMapper.query(eq(DynamoActivityEvent.class), any())).thenReturn(queryResults);
        
        dao.deleteActivityEvents(HEALTH_CODE, null);
        
        verify(mockMapper).batchDelete(listCaptor.capture());
        List<DynamoActivityEvent> eventsToDelete = listCaptor.getValue();
        assertEquals(eventsToDelete, savedEvents);
    }    
    
    @Test
    public void deleteActivityEventsStudyScoped() {
        List<DynamoActivityEvent> savedEvents = ImmutableList.of(ENROLLMENT_EVENT, SURVEY_FINISHED_EVENT,
                QUESTION_ANSWERED_EVENT, ACTIVITY_FINISHED_EVENT);
        when(queryResults.toArray()).thenReturn(savedEvents.toArray());
        when(mockMapper.query(eq(DynamoActivityEvent.class), any())).thenReturn(queryResults);
        
        dao.deleteActivityEvents(HEALTH_CODE, TEST_STUDY_ID);
        
        verify(mockMapper).query(eq(DynamoActivityEvent.class), queryCaptor.capture());
        assertEquals(queryCaptor.getValue().getHashKeyValues().getHealthCode(), 
                HEALTH_CODE + ":" + TEST_STUDY_ID);
        verify(mockMapper).batchDelete(listCaptor.capture());
        List<DynamoActivityEvent> eventsToDelete = listCaptor.getValue();
        assertEquals(eventsToDelete, savedEvents);
    }
    
    @Test
    public void publishEventIsImmutableFails() {
        when(mockMapper.load(any())).thenReturn(ENROLLMENT_EVENT);

        DynamoActivityEvent laterEvent = new DynamoActivityEvent.Builder().withHealthCode(HEALTH_CODE)
                .withObjectType(ENROLLMENT).withTimestamp(TIMESTAMP.plusHours(1)).build();

        boolean result = dao.publishEvent(laterEvent);
        assertFalse(result);

        verify(mockMapper, never()).save(any());
    }
}    
