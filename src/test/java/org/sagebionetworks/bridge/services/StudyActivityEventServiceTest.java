package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.createEvent;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.INSTALL_LINK_SENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_BURST;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.services.StudyActivityEventService.CREATED_ON_FIELD;
import static org.sagebionetworks.bridge.services.StudyActivityEventService.ENROLLMENT_FIELD;
import static org.sagebionetworks.bridge.services.StudyActivityEventService.INSTALL_LINK_SENT_FIELD;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

public class StudyActivityEventServiceTest extends Mockito {
    private static final CacheKey ETAG_KEY = CacheKey.etag(StudyActivityEvent.class, TEST_USER_ID);
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
    private static final DateTime TIMELINE_RETRIEVED_TS = DateTime.parse("2019-03-05T01:34:53.395Z");
    private static final DateTime ENROLLMENT_TS = DateTime.parse("2019-10-14T01:34:53.395Z");
    private static final DateTime INSTALL_LINK_SENT_TS = DateTime.parse("2018-10-11T03:34:53.395Z");
    private static final StudyActivityEvent PERSISTED_EVENT = new StudyActivityEvent.Builder()
            .withUpdateType(IMMUTABLE).build();

    @Mock
    StudyActivityEventDao mockDao;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    ActivityEventService mockActivityEventService;
    
    @Mock
    Schedule2Service mockScheduleService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @InjectMocks
    @Spy
    StudyActivityEventService service;
    
    @Captor
    ArgumentCaptor<StudyActivityEvent> eventCaptor;
    
    Study study;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        study = Study.create();
        study.setCustomEvents(ImmutableList.of(
            new StudyCustomEvent("event1", MUTABLE),
            new StudyCustomEvent("event2", IMMUTABLE)
        ));
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        doReturn(CREATED_ON).when(service).getCreatedOn();
    }
    
    private StudyActivityEvent.Builder makeBuilder() { 
        return new StudyActivityEvent.Builder()
                .withAppId(TEST_APP_ID)
                .withStudyId(TEST_STUDY_ID)
                .withUserId(TEST_USER_ID);
    }
    
    @Test
    public void deleteEvent() {
        StudyActivityEvent originEvent = makeBuilder().withObjectType(CUSTOM).withObjectId("event1")
                .withUpdateType(MUTABLE).build();
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any())).thenReturn(PERSISTED_EVENT);

        service.deleteEvent(originEvent, false);
        
        verify(mockDao).deleteEvent(eventCaptor.capture());
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:event1");
        
        verify(mockCacheProvider).setObject(
                ETAG_KEY, CREATED_ON);
    }
    
    @Test
    public void deleteEvent_eventIsImmutable() {
        StudyActivityEvent originEvent = makeBuilder().withObjectType(CUSTOM).withObjectId("event2").build();
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any())).thenReturn(PERSISTED_EVENT);

        service.deleteEvent(originEvent, false);
        
        verify(mockDao, never()).deleteEvent(any());
    }

    @Test
    public void deleteEvent_noEventPersisted() {
        StudyActivityEvent originEvent = makeBuilder().withObjectId("event2").withObjectType(CUSTOM).build();
        
        // no event returned from a query of the DAO 

        service.deleteEvent(originEvent, false);
        
        verify(mockDao, never()).deleteEvent(any());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void deleteEvent_eventInvalid() {
        // this is not a custom event. Object ID needs to be included or you
        // get (correctly) a validation error for not including an eventId.
        StudyActivityEvent originEvent = makeBuilder().withObjectType(CUSTOM).build();
        service.deleteEvent(originEvent, false);
    }
    
    @Test
    public void deleteEvent_throwsError() { 
        StudyActivityEvent originEvent = makeBuilder()
                .withObjectType(CUSTOM).withObjectId("event2")
                .withTimestamp(MODIFIED_ON).build();
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any())).thenReturn(PERSISTED_EVENT);

        try {
            service.deleteEvent(originEvent, true);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("Study event failed to delete: custom:event2"));
        }
    }
    
    @Test
    public void deleteEvent_deletesStudyBurstEvents() {
        // Publish a deletable event. 
        StudyActivityEvent event = makeBuilder().withObjectId("foo")
                .withTimestamp(ENROLLMENT_TS).withObjectType(CUSTOM)
                .withUpdateType(MUTABLE).build();        
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId("custom:foo");
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        StudyActivityEvent persistedEvent = new StudyActivityEvent();
        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, "custom:foo"))
            .thenReturn(persistedEvent);
        
        service.deleteEvent(event, false);
        
        verify(mockDao, times(4)).deleteEvent(eventCaptor.capture());
        
        StudyActivityEvent origin = eventCaptor.getAllValues().get(0);
        assertEquals(origin.getEventId(), "custom:foo");
        
        StudyActivityEvent sb1 = eventCaptor.getAllValues().get(1);
        assertEquals(sb1.getEventId(), "study_burst:foo:01");
        assertEquals(sb1.getAppId(), TEST_APP_ID);
        assertEquals(sb1.getStudyId(), TEST_STUDY_ID);
        assertEquals(sb1.getUserId(), TEST_USER_ID);
        
        StudyActivityEvent sb2 = eventCaptor.getAllValues().get(2);
        assertEquals(sb2.getEventId(), "study_burst:foo:02");
        
        StudyActivityEvent sb3 = eventCaptor.getAllValues().get(3);
        assertEquals(sb3.getEventId(), "study_burst:foo:03");
    }
    
    @Test
    public void publishEvent_eventIsImmutable() {
        StudyActivityEvent originEvent = makeBuilder()
                .withObjectType(CUSTOM).withObjectId("event2").withTimestamp(MODIFIED_ON).build();
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any())).thenReturn(PERSISTED_EVENT);

        service.publishEvent(originEvent, false, true);
        
        verify(mockDao, never()).publishEvent(any());
    }
    
    @Test
    public void publishEvent() {
        StudyActivityEvent originEvent = makeBuilder().withObjectType(CUSTOM)
                .withObjectId("event1").withTimestamp(MODIFIED_ON)
                .withClientTimeZone("America/Los_Angeles").build();
        
        service.publishEvent(originEvent, false, true);
        
        verify(mockDao).publishEvent(eventCaptor.capture());
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:event1");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
        
        verify(mockCacheProvider).setObject(ETAG_KEY, CREATED_ON);
    }
    
    @Test
    public void publishEvent_noEventPersisted() {
        // This event doesn’t update unless there is no persisted event. Here it persists
        StudyActivityEvent event = makeBuilder().withObjectId("timeline_retrieved")
                .withTimestamp(TIMELINE_RETRIEVED_TS).withObjectType(TIMELINE_RETRIEVED).build();
        
        service.publishEvent(event, false, true);
        
        verify(mockDao).publishEvent(any());
        verify(mockCacheProvider).setObject(ETAG_KEY, CREATED_ON);
    }
    
    @Test
    public void publishEvent_eventPersisted() {
        // This event doesn’t update unless there is no persisted event. Here
        // it does not persist.
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT).build();
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any())).thenReturn(PERSISTED_EVENT);

        service.publishEvent(event, false, true);
        
        verify(mockDao, never()).publishEvent(any());
        verify(mockCacheProvider, never()).setObject(any(), any());
    }
    
    @Test
    public void publishEvent_eventInvalid() {
        StudyActivityEvent event = makeBuilder().build();
        
        try {
            service.publishEvent(event, false, true);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
        }
        // In this case, we known nothing will be updated
        verify(mockCacheProvider, never()).setObject(ETAG_KEY, CREATED_ON);
    }
    
    @Test
    public void publishEvent_throwsError() { 
        // This event doesn’t update unless there is no persisted event. Here
        // it does not persist.
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT).build();
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any())).thenReturn(PERSISTED_EVENT);

        try {
            service.publishEvent(event, true, true);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
        }
        // We have to publish an event because we don't know if some succeeded.
        verify(mockCacheProvider, never()).setObject(ETAG_KEY, CREATED_ON);
    }
    
    @Test
    public void publishEvent_throwsErrorWithMultipleFields() { 
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT).build();

        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, "enrollment")).thenReturn(null);
        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, "study_burst:foo:01")).thenReturn(PERSISTED_EVENT);
        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, "study_burst:foo:02")).thenReturn(PERSISTED_EVENT);
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId(ENROLLMENT_FIELD);
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(2);
        burst.setUpdateType(IMMUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        try {
            service.publishEvent(event, true, true);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("Study event(s) failed to publish: study_burst:foo:01, study_burst:foo:02"));
        }
    }
    
    @Test
    public void publishEvent_studyBurstEventThrowsError() { 
        // This event doesn’t update unless there is no persisted event. Here
        // it does not persist.
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT).build();

        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId(ENROLLMENT_FIELD);
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(IMMUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        // The existence of the first event will be enough to throw an error
        when(mockDao.getRecentStudyActivityEvent(any(), any(), eq("study_burst:foo:01"))).thenReturn(PERSISTED_EVENT);
        
        try {
            service.publishEvent(event, true, true);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals(e.getMessage(), "Study event(s) failed to publish: study_burst:foo:01.");
        }
    }
    
    @Test
    public void publishEvent_publishesStudyBursts() {
        // Covers the case of immutable event, study bursts mutable, others tested below
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT)
                .withClientTimeZone("America/Los_Angeles")
                .build();        
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId(ENROLLMENT_FIELD);
        burst.setIdentifier("foo");
        burst.setDelay(Period.parse("P1W"));
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        service.publishEvent(event, false, true);
        
        verify(mockDao, times(4)).publishEvent(eventCaptor.capture());
        
        StudyActivityEvent origin = eventCaptor.getAllValues().get(0);
        assertEquals(origin.getEventId(), "enrollment");
        
        StudyActivityEvent sb1 = eventCaptor.getAllValues().get(1);
        assertEquals(sb1.getEventId(), "study_burst:foo:01");
        assertEquals(sb1.getAppId(), TEST_APP_ID);
        assertEquals(sb1.getStudyId(), TEST_STUDY_ID);
        assertEquals(sb1.getUserId(), TEST_USER_ID);
        assertEquals(sb1.getTimestamp(), ENROLLMENT_TS.plusWeeks(1));
        assertEquals(sb1.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(sb1.getUpdateType(), MUTABLE);
        assertEquals(sb1.getStudyBurstId(), "foo");
        assertEquals(sb1.getOriginEventId(), "enrollment");
        assertEquals(sb1.getPeriodFromOrigin(), Period.parse("P1W"));
        
        StudyActivityEvent sb2 = eventCaptor.getAllValues().get(2);
        assertEquals(sb2.getEventId(), "study_burst:foo:02");
        assertEquals(sb2.getTimestamp(), ENROLLMENT_TS.plusWeeks(2));
        assertEquals(sb2.getPeriodFromOrigin(), Period.parse("P2W"));
        
        StudyActivityEvent sb3 = eventCaptor.getAllValues().get(3);
        assertEquals(sb3.getEventId(), "study_burst:foo:03");
        assertEquals(sb3.getTimestamp(), ENROLLMENT_TS.plusWeeks(3));
        assertEquals(sb3.getPeriodFromOrigin(), Period.parse("P3W"));
        
        verify(mockCacheProvider).setObject(ETAG_KEY, CREATED_ON);
    }
    
    @Test
    public void publishEvent_publishesStudyBurstsNoDelays() {
        // Covers the case of immutable event, study bursts mutable, others tested below
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT)
                .withClientTimeZone("America/Los_Angeles")
                .build();        
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId(ENROLLMENT_FIELD);
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        service.publishEvent(event, false, true);
        
        verify(mockDao, times(4)).publishEvent(eventCaptor.capture());
        
        StudyActivityEvent origin = eventCaptor.getAllValues().get(0);
        assertEquals(origin.getEventId(), "enrollment");
        
        StudyActivityEvent sb1 = eventCaptor.getAllValues().get(1);
        assertEquals(sb1.getEventId(), "study_burst:foo:01");
        assertEquals(sb1.getTimestamp(), ENROLLMENT_TS);
        assertNull(sb1.getPeriodFromOrigin());
        
        StudyActivityEvent sb2 = eventCaptor.getAllValues().get(2);
        assertEquals(sb2.getEventId(), "study_burst:foo:02");
        assertEquals(sb2.getTimestamp(), ENROLLMENT_TS.plusWeeks(1));
        assertEquals(sb2.getPeriodFromOrigin(), Period.parse("P1W"));
        
        StudyActivityEvent sb3 = eventCaptor.getAllValues().get(3);
        assertEquals(sb3.getEventId(), "study_burst:foo:03");
        assertEquals(sb3.getTimestamp(), ENROLLMENT_TS.plusWeeks(2));
        assertEquals(sb3.getPeriodFromOrigin(), Period.parse("P2W"));
    }
    
    @Test
    public void publishEvent_studyBurstsSuppressed() {
        StudyActivityEvent persistedEvent = makeBuilder().withObjectType(ASSESSMENT).withObjectId("foo").withEventType(FINISHED)
                .withTimestamp(ENROLLMENT_TS).build();

        StudyActivityEvent event = makeBuilder().withObjectType(ASSESSMENT).withObjectId("foo").withEventType(FINISHED)
                .withTimestamp(ENROLLMENT_TS.plusDays(1)).build();

        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId("assessment:foo:finished");
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, "assessment:foo:finished"))
            .thenReturn(persistedEvent);
        
        service.publishEvent(event, false, false);
        
        verify(mockDao, times(1)).publishEvent(eventCaptor.capture());
        assertEquals(eventCaptor.getValue().getEventId(), "assessment:foo:finished");
    }
    
    @Test
    public void publishEvent_studyBurstsSuppressedButIgnored() {
        // There is no existing assessment finished event, which means that despite the call setting study burst
        // updates to false, it's still going to create the study bursts.
        StudyActivityEvent event = makeBuilder().withObjectType(ASSESSMENT).withObjectId("foo").withEventType(FINISHED)
                .withTimestamp(ENROLLMENT_TS.plusDays(1)).build();

        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId("assessment:foo:finished");
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        service.publishEvent(event, false, false);
        
        verify(mockDao, times(4)).publishEvent(eventCaptor.capture());
    }
    
    @Test
    public void publishEvent_doesNotPublishStudyBursts() { 
        // Study bursts are specified, but this is not the origin event, so nothing happens.
        // We can just count calls to the DAO for this.
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT)
                .withClientTimeZone("America/Los_Angeles")
                .build();        
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId("some-other-field");

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        service.publishEvent(event, false, true);
        
        verify(mockDao, times(1)).publishEvent(eventCaptor.capture());
        
        StudyActivityEvent origin = eventCaptor.getAllValues().get(0);
        assertEquals(origin.getEventId(), "enrollment");
    }
    
    @Test
    public void publishEvent_studyBurstUpdateMaintainsAllFields() throws Exception {
        StudyActivityEvent persistedStudyBurst = new StudyActivityEvent.Builder()
                .withTimestamp(ENROLLMENT_TS.minusHours(1))
                .withStudyBurstId("burst1")
                .withPeriodFromOrigin(Period.parse("P4W"))
                .withOriginEventId("enrollment")
                .withUpdateType(MUTABLE).build();
        when(mockDao.getRecentStudyActivityEvent(any(), any(), eq("study_burst:burst1:01")))
            .thenReturn(persistedStudyBurst);
        
        StudyActivityEvent event = makeBuilder().withEventId("study_burst:burst1:01")
                .withTimestamp(ENROLLMENT_TS).build();
        
        service.publishEvent(event, false, false);
        
        verify(mockDao, times(1)).publishEvent(eventCaptor.capture());
        
        StudyActivityEvent captured = eventCaptor.getValue();
        assertEquals(captured.getEventId(), "study_burst:burst1:01");
        assertEquals(captured.getTimestamp(), ENROLLMENT_TS);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getUpdateType(), MUTABLE);
        assertEquals(captured.getStudyBurstId(), "burst1");
        assertEquals(captured.getPeriodFromOrigin(), Period.parse("P4W"));
        assertEquals(captured.getOriginEventId(), "enrollment");
    }
    
    @Test
    public void publishEvent_eventMutableStudyBurstsMutable() {
        // Event immutable, study bursts mutable. Study bursts are not updated because they 
        // are presumed to have been created when the immutable event was first created.
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT).build();
        
        // The event is not updated because it exists.
        when(mockDao.getRecentStudyActivityEvent(any(), any(), eq(ENROLLMENT_FIELD)))
            .thenReturn(PERSISTED_EVENT);
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId(ENROLLMENT_FIELD);
        burst.setIdentifier("foo");
        burst.setDelay(Period.parse("P1W"));
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        service.publishEvent(event, false, true);
        
        verify(mockDao, never()).publishEvent(eventCaptor.capture());
    }
    
    @Test
    public void publishEvent_eventMutableStudyBurstsImmutable() {
        StudyActivityEvent event = makeBuilder().withObjectId(ENROLLMENT_FIELD)
                .withTimestamp(ENROLLMENT_TS).withObjectType(ENROLLMENT)
                .withUpdateType(IMMUTABLE).build();
        
        // This time however, the event is not updated because it exists.
        when(mockDao.getRecentStudyActivityEvent(any(), any(), eq(ENROLLMENT_FIELD)))
            .thenReturn(PERSISTED_EVENT);
        
        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId(ENROLLMENT_FIELD);
        burst.setIdentifier("foo");
        burst.setDelay(Period.parse("P1W"));
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setUpdateType(IMMUTABLE);
        
        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
                .thenReturn(Optional.of(schedule));
        
        when(mockDao.getRecentStudyActivityEvent(any(), any(), eq("study_burst:foo:01")))
            .thenReturn(PERSISTED_EVENT);
        when(mockDao.getRecentStudyActivityEvent(any(), any(), eq("study_burst:foo:03")))
            .thenReturn(PERSISTED_EVENT);
        
        service.publishEvent(event, false, true);
        
        // As per BRIDGE-3120 and MTB-496, nothing is updated, not even the missing study burst.
        // The intention (we believe) was to remove the study burst, so it will stay removed.
        verify(mockDao, never()).publishEvent(eventCaptor.capture());
    }
    
    @DataProvider(name = "eventUpdates")
    public Object[][] eventUpdates() {
        return new Object[][] { 
            { 2, MUTABLE, TRUE, MUTABLE, TRUE }, 
            { 2, MUTABLE, TRUE, MUTABLE, FALSE },
            { 1, MUTABLE, TRUE, IMMUTABLE, TRUE }, 
            { 2, MUTABLE, TRUE, IMMUTABLE, FALSE },
            { 2, MUTABLE, FALSE, MUTABLE, TRUE }, 
            { 2, MUTABLE, FALSE, MUTABLE, FALSE },
            { 1, MUTABLE, FALSE, IMMUTABLE, TRUE }, 
            { 2, MUTABLE, FALSE, IMMUTABLE, FALSE },
            { 0, IMMUTABLE, TRUE, MUTABLE, TRUE }, 
            { 0, IMMUTABLE, TRUE, MUTABLE, FALSE },
            { 0, IMMUTABLE, TRUE, IMMUTABLE, TRUE }, 
            { 0, IMMUTABLE, TRUE, IMMUTABLE, FALSE },
            { 2, IMMUTABLE, FALSE, MUTABLE, TRUE }, 
            { 2, IMMUTABLE, FALSE, MUTABLE, FALSE },
            { 1, IMMUTABLE, FALSE, IMMUTABLE, TRUE }, 
            { 2, IMMUTABLE, FALSE, IMMUTABLE, FALSE }
        };
    }
     
    @Test(dataProvider = "eventUpdates")
    public void publishEventTest(int count, ActivityEventUpdateType originEventType, boolean originPersisted,
            ActivityEventUpdateType burstType, boolean burstPersisted) {

        StudyActivityEvent event = makeBuilder().withObjectId("custom:event1")
                .withTimestamp(ENROLLMENT_TS.plusDays(1)).withObjectType(CUSTOM)
                .withUpdateType(originEventType).build();
        
        StudyActivityEvent persistedEvent = null;
        if (originPersisted) {
            persistedEvent = makeBuilder().withObjectId("custom:event1")
                    .withTimestamp(ENROLLMENT_TS).withObjectType(CUSTOM)
                    .withUpdateType(originEventType).build();
        }
        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, "custom:event1"))
            .thenReturn(persistedEvent);

        StudyBurst burst = new StudyBurst();
        burst.setOriginEventId("custom:event1");
        burst.setIdentifier("foo");
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(1);
        burst.setUpdateType(burstType);

        Schedule2 schedule = new Schedule2();
        schedule.setStudyBursts(ImmutableList.of(burst));
        when(mockScheduleService.getScheduleForStudy(TEST_APP_ID, TEST_STUDY_ID))
            .thenReturn(Optional.of(schedule));

        StudyActivityEvent persistedBurst = null;
        if (burstPersisted) {
            persistedBurst = makeBuilder().withObjectId("study_burst:foo:01")
                    .withTimestamp(ENROLLMENT_TS).withObjectType(STUDY_BURST)
                    .withUpdateType(burstType).build();
        }
        when(mockDao.getRecentStudyActivityEvent(TEST_USER_ID, TEST_STUDY_ID, 
                "study_burst:foo:01")).thenReturn(persistedBurst);
        
        service.publishEvent(event, false, true);
        
        verify(mockDao, times(count)).publishEvent(eventCaptor.capture());
    }
    
    @Test
    public void getRecentStudyActivityEvents() {
        StudyActivityEvent event1 = createEvent(ENROLLMENT_FIELD, ENROLLMENT_TS, null);
        StudyActivityEvent event2 = createEvent("timeline_retrieved", TIMELINE_RETRIEVED_TS, null);
        StudyActivityEvent event3 = createEvent("custom:event1", CREATED_ON, 4);
        
        List<StudyActivityEvent> list = Lists.newArrayList(event1, event2, event3);
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, CREATED_ON, 
                INSTALL_LINK_SENT_FIELD, INSTALL_LINK_SENT_TS, "custom:event1", CREATED_ON);
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        Account account = Account.create();
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        ResourceList<StudyActivityEvent> retValue = service
                .getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue.getItems().size(), 5);
        
        StudyActivityEvent createdOn = TestUtils.findByEventId(
                retValue.getItems(), ActivityEventObjectType.CREATED_ON);
        assertEquals(createdOn.getTimestamp(), CREATED_ON);
        assertEquals(createdOn.getRecordCount(), Integer.valueOf(1));

        StudyActivityEvent installLinkSentOn = TestUtils.findByEventId(
                retValue.getItems(), INSTALL_LINK_SENT);
        assertEquals(installLinkSentOn.getTimestamp(), INSTALL_LINK_SENT_TS);
        assertEquals(installLinkSentOn.getRecordCount(), Integer.valueOf(1));
        
        StudyActivityEvent event = TestUtils.findByEventId(
                retValue.getItems(), CUSTOM);
        assertEquals(event.getTimestamp(), CREATED_ON);
        assertEquals(event.getRecordCount(), Integer.valueOf(4));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecentStudyActivityEvents_noAccount() {
        service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void getStudyActivityEventHistory() {
        List<StudyActivityEvent> list = new ArrayList<>();
        PagedResourceList<StudyActivityEvent> page = new PagedResourceList<>(list, 100);
        when(mockDao.getStudyActivityEventHistory(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event1", 10, 100)).thenReturn(page);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(new StudyCustomEvent("event1", MUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, "custom:event1", 10, 100);
        assertSame(retValue, page);
        assertEquals(retValue.getRequestParams().get("offsetBy"), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get("pageSize"), Integer.valueOf(100));
    }

    @Test
    public void getStudyActivityEventHistory_appScopedEvent() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        when(mockActivityEventService.getActivityEventMap(
                TEST_APP_ID, HEALTH_CODE)).thenReturn(ImmutableMap.of("install_link_sent", MODIFIED_ON));
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, "install_link_sent", 10, 100);
        StudyActivityEvent event = retValue.getItems().get(0);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getCreatedOn(), MODIFIED_ON);
        assertNull(event.getRecordCount());
        assertEquals(retValue.getRequestParams().get("offsetBy"), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get("pageSize"), Integer.valueOf(100));
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_negativeOffset() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "event1", -10, 100);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_minPageSize() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "event1", 0, API_MINIMUM_PAGE_SIZE-1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_maxPageSize() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "event1", 0, API_MAXIMUM_PAGE_SIZE+1);
    }
    
    @Test
    public void getStudyActivityEventHistory_createdOn() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, CREATED_ON);
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, CREATED_ON_FIELD, 0, 50);
        assertEquals(retValue.getItems().size(), 1);
        StudyActivityEvent event = retValue.getItems().get(0);
        assertEquals(event.getTimestamp(), CREATED_ON);
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertNull(event.getRecordCount());
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getStudyActivityEventHistory_syntheticEventNoAccount() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        
        service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, CREATED_ON_FIELD, 0, 50);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Event ID is required")
    public void getStudyActivityEventHistory_nullEventId() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, null, 0, 50);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "“nonsense” is not a valid event ID")
    public void getStudyActivityEventHistory_invalidEventId() {
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);

        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "nonsense", 0, 50);
    }
    
    @Test
    public void getStudyActivityEventHistory_noPaging() {
        StudyActivityEvent event = createEvent("custom:event1", MODIFIED_ON, null);

        when(mockDao.getStudyActivityEventHistory(TEST_USER_ID, TEST_STUDY_ID, 
                "custom:event1", null, null)).thenReturn(
                        new PagedResourceList<>(Lists.newArrayList(event), 0, true));
                
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(new StudyCustomEvent("event1", MUTABLE)));
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, "event1", null, null);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0), event);
        
        verify(mockDao).getStudyActivityEventHistory(TEST_USER_ID, TEST_STUDY_ID, "custom:event1", null, null);
    }
    
    @Test
    public void addEnrollmentToRecentIfMissing() {
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setHealthCode(HEALTH_CODE);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, CREATED_ON);
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        ResourceList<StudyActivityEvent> retValue = service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue.getItems().size(), 2);
        
        StudyActivityEvent event = retValue.getItems().get(0);
        assertEquals(event.getEventId(), CREATED_ON_FIELD);
        assertEquals(event.getTimestamp(), CREATED_ON);
        
        event = retValue.getItems().get(1);
        assertEquals(event.getEventId(), "enrollment");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertNull(event.getOriginEventId());
        assertNull(event.getStudyBurstId());
        assertEquals(event.getRecordCount(), Integer.valueOf(1));
        assertEquals(event.getUpdateType(), IMMUTABLE);
    }

    @Test
    public void doNotAddEnrollmentToRecentIfNotMissing() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<StudyActivityEvent> list = Lists.newArrayList(createEvent(ENROLLMENT_FIELD, CREATED_ON, null));
        when(mockDao.getRecentStudyActivityEvents(TEST_USER_ID, TEST_STUDY_ID))
            .thenReturn(list);
        
        ResourceList<StudyActivityEvent> retValue = service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue.getItems().get(0).getEventId(), ENROLLMENT_FIELD);
        assertEquals(retValue.getItems().get(0).getTimestamp(), CREATED_ON); // not modifiedOn
    }

    @Test
    public void addEnrollmentToHistoryIfMissing() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        PagedResourceList<StudyActivityEvent> results = new PagedResourceList<>(ImmutableList.of(), 0, true);
        when(mockDao.getStudyActivityEventHistory(any(), any(), any(), any(), any())).thenReturn(results);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, ENROLLMENT_FIELD, null, null);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(1));
        assertEquals(retValue.getItems().get(0).getTimestamp(), MODIFIED_ON);
        
        StudyActivityEvent event = retValue.getItems().get(0);
        assertEquals(event.getEventId(), "enrollment");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertNull(event.getOriginEventId());
        assertNull(event.getStudyBurstId());
        assertNull(event.getRecordCount());
        assertEquals(event.getUpdateType(), IMMUTABLE);
    }

    @Test
    public void doNotAddEnrollmentToHistoryIfNotMissing() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        PagedResourceList<StudyActivityEvent> results = new PagedResourceList<>(
                ImmutableList.of(createEvent(ENROLLMENT_FIELD, CREATED_ON, null)), 1, true);
        when(mockDao.getStudyActivityEventHistory(any(), any(), any(), any(), any())).thenReturn(results);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, ENROLLMENT_FIELD, null, null);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(1));
        assertEquals(retValue.getItems().get(0).getTimestamp(), CREATED_ON); // not modifiedOn
    }
    
    // BRIDGE-3179
    @Test
    public void getRecentStudyActivityEvents_noDuplicationError() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        StudyActivityEvent event = new StudyActivityEvent.Builder().withEventId(CREATED_ON_FIELD)
                .withTimestamp(CREATED_ON).build();        
        when(mockDao.getRecentStudyActivityEvents(TEST_USER_ID, TEST_STUDY_ID))
                .thenReturn(Lists.newArrayList(event));
        
        // This ALSO returns created_on, so ignore it and the MODIFIED_ON timestamp (this really happens)
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, MODIFIED_ON); // it should ignore this value
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        ResourceList<StudyActivityEvent> list = service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(list.getItems().size(), 1);
        assertEquals(list.getItems().get(0).getTimestamp(), CREATED_ON);
    }
}