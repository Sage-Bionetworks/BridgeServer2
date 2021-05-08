package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertDatesWithTimeZoneEqual;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ActivityEventServiceTest {
    private static final DateTime ACTIVITIES_RETRIEVED = DateTime.parse("2017-05-27T00:00:00.000Z");
    private static final DateTime ENROLLMENT = DateTime.parse("2017-05-28T00:00:00.000Z");
    private static final DateTime CREATED_ON = DateTime.parse("2017-05-26T00:00:00.000Z");
    private static final DateTime FINISHED_ON = DateTime.parse("2020-12-26T00:00:00.000Z");
    
    @Spy
    private ActivityEventService activityEventService;
    
    @Mock
    private ActivityEventDao activityEventDao;
    
    @Mock
    private AppService mockAppService;
    
    @Mock
    private ParticipantService mockParticipantService;
    
    @Captor
    private ArgumentCaptor<ActivityEvent> eventCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        activityEventService = new ActivityEventService();
        
        activityEventService.setActivityEventDao(activityEventDao);
        activityEventService.setAppService(mockAppService);
        activityEventService.setParticipantService(mockParticipantService);
    }

    @Test
    public void canPublishGlobalCustomEvent() throws Exception {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("eventKey1", FUTURE_ONLY, "eventKey2", FUTURE_ONLY));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(app, null, HEALTH_CODE, "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:eventKey1");
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE);
        assertEquals(activityEvent.getUpdateType(), FUTURE_ONLY);
        assertNull(activityEvent.getStudyId());
        assertEquals(activityEvent.getTimestamp(), timestamp);
    }

    @Test
    public void canPublishGlobalCustomEventPassesUpdateType() throws Exception {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("eventKey1", IMMUTABLE, "eventKey2", FUTURE_ONLY));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(app, null, HEALTH_CODE, "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();
        assertEquals(activityEvent.getUpdateType(), IMMUTABLE);
    }
    
    @Test
    public void canPublishStudyScopedCustomEvent() throws Exception {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("eventKey1", FUTURE_ONLY, "eventKey2", FUTURE_ONLY));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(app, TEST_STUDY_ID, HEALTH_CODE, "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:eventKey1");
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
        assertEquals(activityEvent.getUpdateType(), FUTURE_ONLY);
        assertEquals(activityEvent.getStudyId(), TEST_STUDY_ID);
        assertEquals(activityEvent.getTimestamp(), timestamp);
    }
    
    @Test
    public void canPublishStudyScopedCustomEventPassesUpdateType() throws Exception {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("eventKey1", MUTABLE, "eventKey2", FUTURE_ONLY));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(app, TEST_STUDY_ID, HEALTH_CODE, "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();
        assertEquals(activityEvent.getUpdateType(), MUTABLE);
    }
    
    @Test
    public void canPublishCustomEventFromAutomaticEvents() {
        DateTime timestamp1 = DateTime.now();
        DateTime timestamp2 = timestamp1.plusDays(3);
        
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("myEvent", FUTURE_ONLY));
        app.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "myEvent:P3D"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        activityEventService.publishCustomEvent(app, null, HEALTH_CODE, "myEvent", timestamp1);

        verify(activityEventDao, times(2)).publishEvent(any());

        ActivityEvent activityEvent = activityEventArgumentCaptor.getAllValues().get(0);
        assertEquals(activityEvent.getEventId(), "custom:myEvent");
        assertEquals(activityEvent.getUpdateType(), FUTURE_ONLY);
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE);
        assertEquals(activityEvent.getTimestamp(), timestamp1);

        activityEvent = activityEventArgumentCaptor.getAllValues().get(1);
        assertEquals(activityEvent.getEventId(), "custom:3-days-after-enrollment");
        assertEquals(activityEvent.getUpdateType(), MUTABLE
                );
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE);
        assertEquals(activityEvent.getTimestamp(), timestamp2);
    }
    
    @Test
    public void canPublishCustomEventAutomaticEventsSkippedOnFailure() {
        DateTime timestamp1 = DateTime.now();
        
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("myEvent", FUTURE_ONLY));
        app.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "myEvent:P3D"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(false);

        activityEventService.publishCustomEvent(app, null, HEALTH_CODE, "myEvent", timestamp1);

        verify(activityEventDao, times(1)).publishEvent(any());

        ActivityEvent activityEvent = activityEventArgumentCaptor.getAllValues().get(0);
        assertEquals(activityEvent.getEventId(), "custom:myEvent");
        assertEquals(activityEvent.getUpdateType(), FUTURE_ONLY);
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE);
        assertEquals(activityEvent.getTimestamp(), timestamp1);
    }

    @Test
    public void cannotPublishUnknownCustomEvent() throws Exception {
        App app = App.create();
        try {
            activityEventService.publishCustomEvent(app, null, HEALTH_CODE, "eventKey5", DateTime.now());
            fail("expected exception");
        } catch (BadRequestException e) {
            assertTrue(e.getMessage().endsWith("eventKey5"));
        }
    }
    
    @Test
    public void canPublishGlobalCreatedOn() {
        DateTime now = DateTime.now();
        
        activityEventService.publishCreatedOnEvent(null, HEALTH_CODE, now);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "created_on");
        assertEquals(argument.getValue().getTimestamp(), now);
        assertEquals(argument.getValue().getUpdateType(), IMMUTABLE);
        assertNull(argument.getValue().getStudyId());
        assertEquals(argument.getValue().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void canPublishStudyScopedCreatedOn() {
        DateTime now = DateTime.now();
        
        activityEventService.publishCreatedOnEvent(TEST_STUDY_ID, HEALTH_CODE, now);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(2)).publishEvent(argument.capture());
        
        assertEquals(argument.getAllValues().get(0).getEventId(), "created_on");
        assertEquals(argument.getAllValues().get(0).getTimestamp(), now);
        assertEquals(argument.getAllValues().get(0).getUpdateType(), IMMUTABLE);
        assertNull(argument.getAllValues().get(0).getStudyId());
        assertEquals(argument.getAllValues().get(0).getHealthCode(), HEALTH_CODE);

        assertEquals(argument.getAllValues().get(1).getEventId(), "created_on");
        assertEquals(argument.getAllValues().get(1).getTimestamp(), now);
        assertEquals(argument.getAllValues().get(1).getUpdateType(), IMMUTABLE);
        assertEquals(argument.getAllValues().get(1).getStudyId(), TEST_STUDY_ID);
        assertEquals(argument.getAllValues().get(1).getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
    }
    
    @Test
    public void canGetGlobalActivityEventMap() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, null)).thenReturn(map);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, null, HEALTH_CODE);
        assertEquals(results.get("activities_retrieved"), ACTIVITIES_RETRIEVED);
        assertEquals(results.get("enrollment"), ENROLLMENT);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), ACTIVITIES_RETRIEVED);
        assertEquals(results.size(), 4);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, null);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), anyBoolean());
    }
    
    @Test
    public void canSetGlobalStudyStartDateWithEnrollment() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, null)).thenReturn(map);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, null, HEALTH_CODE);
        assertEquals(results.get("enrollment"), ENROLLMENT);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), ENROLLMENT);
        assertEquals(results.size(), 3);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, null);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), eq(false));
    }

    @Test
    public void canSetStudyScopedEventMap() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, TEST_STUDY_ID)).thenReturn(map);
        
        activityEventService.getActivityEventMap(TEST_APP_ID, TEST_STUDY_ID, HEALTH_CODE);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, TEST_STUDY_ID);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), eq(false));
    }
    
    @Test
    public void canSetGlobalStudyStartDateWithActivitiesRetrieved() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, null)).thenReturn(map);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, null, HEALTH_CODE);
        assertEquals(results.get("activities_retrieved"), ACTIVITIES_RETRIEVED);
        assertEquals(results.get("enrollment"), ENROLLMENT);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), ACTIVITIES_RETRIEVED);
        assertEquals(results.size(), 4);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, null);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), eq(false));
    }

    @Test
    public void canSetGlobalStudyStartDateWithCreatedOn() {
        Map<String, DateTime> map = Maps.newHashMap();
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, null)).thenReturn(map);
        
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withCreatedOn(CREATED_ON).build();
        when(mockParticipantService.getParticipant(app, "healthcode:" + HEALTH_CODE, false)).thenReturn(studyParticipant);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, null, HEALTH_CODE);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), CREATED_ON);
        assertEquals(results.size(), 2);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, null);
        verify(mockAppService).getApp(TEST_APP_ID);
        verify(mockParticipantService).getParticipant(app, "healthcode:"+HEALTH_CODE, false);
    }
    
    @Test
    public void canDeleteGlobalActivityEvents() {
        activityEventService.deleteActivityEvents(null, HEALTH_CODE);
        
        verify(activityEventDao).deleteActivityEvents(HEALTH_CODE, null);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canDeleteStudyScopedActivityEvents() {
        activityEventService.deleteActivityEvents(TEST_STUDY_ID, HEALTH_CODE);
        
        verify(activityEventDao).deleteActivityEvents(HEALTH_CODE, TEST_STUDY_ID);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badGetDoesntCallDao() {
        try {
            activityEventService.getActivityEventMap(TEST_APP_ID, null, null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badDeleteDoesntCallDao() {
        try {
            activityEventService.deleteActivityEvents(null, null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void canPublishGlobalEnrollmentEvent() {
        DateTime now = DateTime.now(DateTimeZone.UTC);
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();

        activityEventService.publishEnrollmentEvent(App.create(), null, "AAA-BBB-CCC", signature.getSignedOnAsDateTime());
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "enrollment");
        assertEquals(argument.getValue().getTimestamp(), now);
        assertEquals(argument.getValue().getUpdateType(), IMMUTABLE);
        assertNull(argument.getValue().getStudyId());
        assertEquals(argument.getValue().getHealthCode(), "AAA-BBB-CCC");
    }

    @Test
    public void canPublishStudyScopedEnrollmentEvent() {
        // Configure app with automatic custom events
        App app = App.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D").build());
        
        DateTime now = DateTime.now(DateTimeZone.UTC);
        DateTime now3DaysLater = now.plusDays(3);
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();
        when(activityEventDao.publishEvent(any())).thenReturn(true);
        
        activityEventService.publishEnrollmentEvent(app, TEST_STUDY_ID, HEALTH_CODE, signature.getSignedOnAsDateTime());
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(argument.capture());
        
        ActivityEvent event1 = argument.getAllValues().get(0);
        assertEquals(event1.getEventId(), "enrollment");
        assertEquals(event1.getTimestamp(), now);
        assertEquals(event1.getUpdateType(), IMMUTABLE);
        assertNull(event1.getStudyId());
        assertEquals(event1.getHealthCode(), HEALTH_CODE);

        ActivityEvent event2 = argument.getAllValues().get(1);
        assertEquals(event2.getEventId(), "custom:3-days-after");
        assertEquals(event2.getTimestamp(), now3DaysLater);
        assertEquals(event2.getUpdateType(), MUTABLE);
        assertNull(event2.getStudyId());
        assertEquals(event2.getHealthCode(), HEALTH_CODE);

        ActivityEvent event3 = argument.getAllValues().get(2);
        assertEquals(event3.getEventId(), "enrollment");
        assertEquals(event3.getTimestamp(), now);
        assertEquals(event3.getUpdateType(), IMMUTABLE);
        assertEquals(event3.getStudyId(), TEST_STUDY_ID);
        assertEquals(event3.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);

        ActivityEvent event4 = argument.getAllValues().get(3);
        assertEquals(event4.getEventId(), "custom:3-days-after");
        assertEquals(event4.getTimestamp(), now3DaysLater);
        assertEquals(event4.getUpdateType(), MUTABLE);
        assertEquals(event4.getStudyId(), TEST_STUDY_ID);
        assertEquals(event4.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
    }
    
    @Test
    public void canPublishStudyScopedEnrollmentEventSkipsAutomaticEventsOnFailure() {
        DateTime now = DateTime.now(DateTimeZone.UTC);
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();
        when(activityEventDao.publishEvent(any())).thenReturn(false);

        activityEventService.publishEnrollmentEvent(App.create(), TEST_STUDY_ID, HEALTH_CODE, signature.getSignedOnAsDateTime());
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(2)).publishEvent(argument.capture());
        
        assertEquals(argument.getAllValues().get(0).getEventId(), "enrollment");
        assertEquals(argument.getAllValues().get(0).getTimestamp(), now);
        assertEquals(argument.getAllValues().get(0).getUpdateType(), IMMUTABLE);
        assertNull(argument.getAllValues().get(0).getStudyId());
        assertEquals(argument.getAllValues().get(0).getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void canPublishEnrollmentEventWithAutomaticCustomEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D") // defaults to enrollment
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());

        // Create consent signature
        DateTime enrollment = DateTime.parse("2018-04-04T16:00Z");
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(enrollment.minusDays(10).getMillis())
                .withSignedOn(enrollment.getMillis()).build();

        when(activityEventDao.publishEvent(any())).thenReturn(true);
        
        // Execute
        activityEventService.publishEnrollmentEvent(app,null, "AAA-BBB-CCC", signature.getSignedOnAsDateTime());

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals(publishedEventList.get(0).getEventId(), "enrollment");
        assertDatesWithTimeZoneEqual(publishedEventList.get(0).getTimestamp(), enrollment);
        assertEquals(publishedEventList.get(0).getUpdateType(), IMMUTABLE);
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(1).getEventId(), "custom:3-days-after");
        assertEquals(publishedEventList.get(1).getTimestamp(),
                DateTime.parse("2018-04-07T16:00Z"));
        assertEquals(publishedEventList.get(1).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(2).getEventId(), "custom:1-week-after");
        assertEquals(publishedEventList.get(2).getTimestamp(),
                DateTime.parse("2018-04-11T16:00Z"));
        assertEquals(publishedEventList.get(2).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(2).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(3).getEventId(), "custom:13-weeks-after");
        assertEquals(publishedEventList.get(3).getTimestamp(),
                DateTime.parse("2018-07-04T16:00Z"));
        assertEquals(publishedEventList.get(3).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(3).getHealthCode(), "AAA-BBB-CCC");
    }
    
    @Test
    public void whenNoEnrollmentEventPublishNoCustomEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        // timestamp here does not matter
        activityEventService.publishEnrollmentEvent(app, null, "AAA-BBB-CCC", CREATED_ON);
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void whenActivitiesRetrievedEventFailsPublishNoAutomaticEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishActivitiesRetrieved(app,null, "AAA-BBB-CCC", DateTime.now());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void whenEnrollmentEventFailsPublishNoAutomaticEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        // timestamp here does not matter
        activityEventService.publishEnrollmentEvent(app,null, "AAA-BBB-CCC", CREATED_ON);
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }

    @Test
    public void whenCustomEventFailsPublishNoAutomaticEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("myEvent", FUTURE_ONLY));
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishCustomEvent(app,null, "AAA-BBB-CCC", "myEvent", DateTime.now());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void canPublishGlobalActivitiesRetrievedEventWithAutomaticCustomEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        // Note that these events include events that should be triggered for enrollment, 
        // not activities retrieved. These are ignore.
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "activities_retrieved:P3D")
                .put("1-week-after", "activities_retrieved:P1W")
                .put("13-weeks-after", "activities_retrieved:P13W")
                .put("5-years-after", "enrollment:P5Y")
                .put("10-years-after", "enrollment:P10Y").build());

        // Create consent signature
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-07:00");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishActivitiesRetrieved(app, null, "AAA-BBB-CCC", retrieved);

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals(publishedEventList.get(0).getEventId(), "activities_retrieved");
        assertEquals(publishedEventList.get(0).getTimestamp(), retrieved);
        assertEquals(publishedEventList.get(0).getUpdateType(), IMMUTABLE);
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(1).getEventId(), "custom:3-days-after");
        assertEquals(publishedEventList.get(1).getTimestamp(),
                DateTime.parse("2018-04-07T16:00-0700"));
        assertEquals(publishedEventList.get(1).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(2).getEventId(), "custom:1-week-after");
        assertEquals(publishedEventList.get(2).getTimestamp(),
                DateTime.parse("2018-04-11T16:00-0700"));
        assertEquals(publishedEventList.get(2).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(2).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(3).getEventId(), "custom:13-weeks-after");
        assertEquals(publishedEventList.get(3).getTimestamp(),
                DateTime.parse("2018-07-04T16:00-0700"));
        assertEquals(publishedEventList.get(3).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(3).getHealthCode(), "AAA-BBB-CCC");
    }
    
    @Test
    public void canPublishStudyScopedActivitiesRetrievedEvent() {
        // Configure app with automatic custom events
        App app = App.create();
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "activities_retrieved:P3D").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Create consent signature
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-0700");
        DateTime retrievedAfter3Days = retrieved.plusDays(3);
        
        // Execute
        activityEventService.publishActivitiesRetrieved(app, TEST_STUDY_ID, "AAA-BBB-CCC", retrieved);

        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());
        
        ActivityEvent event1 = publishedEventCaptor.getAllValues().get(0);
        ActivityEvent event2 = publishedEventCaptor.getAllValues().get(1);
        ActivityEvent event3 = publishedEventCaptor.getAllValues().get(2);
        ActivityEvent event4 = publishedEventCaptor.getAllValues().get(3);
        
        assertEquals(event1.getEventId(), "activities_retrieved");
        assertEquals(event1.getTimestamp(), retrieved);
        assertEquals(event1.getUpdateType(), IMMUTABLE);
        assertEquals(event1.getHealthCode(), "AAA-BBB-CCC");
        assertNull(event1.getStudyId());

        assertEquals(event2.getEventId(), "custom:3-days-after");
        assertEquals(event2.getTimestamp(), retrievedAfter3Days);
        assertEquals(event2.getUpdateType(), MUTABLE);
        assertEquals(event2.getHealthCode(), "AAA-BBB-CCC");
        assertNull(event2.getStudyId());
        
        assertEquals(event3.getEventId(), "activities_retrieved");
        assertEquals(event3.getTimestamp(), retrieved);
        assertEquals(event3.getUpdateType(), IMMUTABLE);
        assertEquals(event3.getHealthCode(), "AAA-BBB-CCC:" + TEST_STUDY_ID);
        assertEquals(event3.getStudyId(), TEST_STUDY_ID);

        assertEquals(event4.getEventId(), "custom:3-days-after");
        assertEquals(event4.getTimestamp(), retrievedAfter3Days);
        assertEquals(event4.getUpdateType(), MUTABLE);
        assertEquals(event4.getHealthCode(), "AAA-BBB-CCC:" + TEST_STUDY_ID);
        assertEquals(event4.getStudyId(), TEST_STUDY_ID);
    }
    
    @Test
    public void canPublishStudyScopedActivitiesRetrievedEventNoAutoEventsOnFailrue() {
        // Configure app with automatic custom events
        App app = App.create();
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "activities_retrieved:P3D").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);

        // Create consent signature
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-0700");

        // Execute
        activityEventService.publishActivitiesRetrieved(app, TEST_STUDY_ID, "AAA-BBB-CCC", retrieved);

        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        
        verify(activityEventDao, times(2)).publishEvent(publishedEventCaptor.capture());
        
        ActivityEvent event1 = publishedEventCaptor.getAllValues().get(0);
        ActivityEvent event2 = publishedEventCaptor.getAllValues().get(1);
        
        assertEquals(event1.getEventId(), "activities_retrieved");
        assertEquals(event1.getTimestamp(), retrieved);
        assertEquals(event1.getUpdateType(), IMMUTABLE);
        assertEquals(event1.getHealthCode(), "AAA-BBB-CCC");
        assertNull(event1.getStudyId());

        assertEquals(event2.getEventId(), "activities_retrieved");
        assertEquals(event2.getTimestamp(), retrieved);
        assertEquals(event2.getUpdateType(), IMMUTABLE);
        assertEquals(event2.getHealthCode(), "AAA-BBB-CCC:" + TEST_STUDY_ID);
        assertEquals(event2.getStudyId(), TEST_STUDY_ID);
    }
    
    @Test
    public void canPublishCustomEventWithAutomaticCustomEvents() {
        // This also verifies the correct parsing of the custom event key, which contains a colon.
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("myEvent", FUTURE_ONLY));
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        DateTime timestamp = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishCustomEvent(app, null, "AAA-BBB-CCC", "myEvent", timestamp);

        // Verify published events (3)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(3)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();
        
        assertEquals(publishedEventList.get(0).getEventId(), "custom:myEvent");
        assertEquals(publishedEventList.get(0).getTimestamp(), timestamp);
        assertEquals(publishedEventList.get(0).getUpdateType(), FUTURE_ONLY);
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(1).getEventId(), "custom:3-days-after");
        assertDatesWithTimeZoneEqual(publishedEventList.get(1).getTimestamp(), new DateTime("2018-04-07T16:00-0700"));
        assertEquals(publishedEventList.get(1).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(2).getEventId(), "custom:1-week-after");
        assertDatesWithTimeZoneEqual(publishedEventList.get(2).getTimestamp(),
                new DateTime("2018-04-11T16:00-0700"));
        assertEquals(publishedEventList.get(2).getUpdateType(), MUTABLE);
        assertEquals(publishedEventList.get(2).getHealthCode(), "AAA-BBB-CCC");
    }

    @Test
    public void canPublishSurveyAnswer() {
        DateTime now = DateTime.now();
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnsweredOn(now.getMillis());
        answer.setQuestionGuid("BBB-CCC-DDD");
        answer.setAnswers(Lists.newArrayList("belgium"));

        activityEventService.publishQuestionAnsweredEvent(HEALTH_CODE, answer);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "question:BBB-CCC-DDD:answered");
        assertEquals(argument.getValue().getTimestamp(), now);
        assertEquals(argument.getValue().getUpdateType(), FUTURE_ONLY);
        assertEquals(argument.getValue().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void doesNotPublishActivityFinishedEventForOldActivity() {
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setGuid("AAA");

        activityEventService.publishActivityFinishedEvent(activity);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canPublishActivityFinishedEvents() {
        DateTime finishedOn = DateTime.now();
        
        ScheduledActivity schActivity = ScheduledActivity.create();
        schActivity.setGuid("AAA:"+DateTime.now().toLocalDateTime());
        schActivity.setActivity(TestUtils.getActivity1());
        schActivity.setLocalExpiresOn(LocalDateTime.now().plusDays(1));
        schActivity.setStartedOn(DateTime.now().getMillis());
        schActivity.setFinishedOn(finishedOn.getMillis());
        schActivity.setHealthCode(HEALTH_CODE);

        activityEventService.publishActivityFinishedEvent(schActivity);
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());

        ActivityEvent event = argument.getValue();
        assertEquals(event.getHealthCode(), HEALTH_CODE);
        assertEquals(event.getEventId(), "activity:AAA:finished");
        assertEquals(event.getTimestamp(), finishedOn);
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void getGlobalActivityEventList() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, null)).thenReturn(map);
        
        List<ActivityEvent> results = activityEventService.getActivityEventList(TEST_APP_ID, null, HEALTH_CODE);
        
        ActivityEvent ar = getEventByKey(results, "activities_retrieved");
        assertEquals(ar.getTimestamp(), ACTIVITIES_RETRIEVED);
        
        ActivityEvent en = getEventByKey(results, "enrollment");
        assertEquals(en.getTimestamp(), ENROLLMENT);
        
        ActivityEvent co = getEventByKey(results, "created_on");
        assertEquals(co.getTimestamp(), CREATED_ON);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, null);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), anyBoolean());
    }
    
    @Test
    public void getGlobalActivityEventStudyScoped() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, TEST_STUDY_ID)).thenReturn(map);
        
        List<ActivityEvent> results = activityEventService
                .getActivityEventList(TEST_APP_ID, TEST_STUDY_ID, HEALTH_CODE);
        
        ActivityEvent ar = getEventByKey(results, "activities_retrieved");
        assertEquals(ar.getTimestamp(), ACTIVITIES_RETRIEVED);
        
        ActivityEvent en = getEventByKey(results, "enrollment");
        assertEquals(en.getTimestamp(), ENROLLMENT);
        
        ActivityEvent co = getEventByKey(results, "created_on");
        assertEquals(co.getTimestamp(), CREATED_ON);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, TEST_STUDY_ID);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), anyBoolean());
    }
    
    @Test
    public void autoCustomEventsCanBeUsedToCloneEvents() {
        // newEvent = oldEvent:P0D should create a new custom event with the same 
        // timestamp. We can use this in MTB to clone an event like created_on to 
        // a custom event that the admin client can then change. This verifies that
        // the libraries do what we want here.
        Period automaticEventDelay = Period.parse("P0D"); // no difference
        DateTime automaticEventTime = CREATED_ON.plus(automaticEventDelay);
        assertEquals(automaticEventTime, CREATED_ON); // no difference
    }
    
    @Test
    public void canDeleteCustomEvent() {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("eventKey", FUTURE_ONLY));
        
        activityEventService.deleteCustomEvent(app, TEST_STUDY_ID, HEALTH_CODE, "eventKey");
        
        verify(activityEventDao).deleteCustomEvent(eventCaptor.capture());
        
        ActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getEventId(), "custom:eventKey");
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
        assertEquals(event.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
    }
    
    @Test
    public void canDeleteCustomEventPassesUpdateType() {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("eventKey", MUTABLE));
        
        activityEventService.deleteCustomEvent(app, TEST_STUDY_ID, HEALTH_CODE, "eventKey");
        
        verify(activityEventDao).deleteCustomEvent(eventCaptor.capture());
        
        ActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getUpdateType(), MUTABLE);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = ".*App's customEvents do not contain event ID: eventKey.*")
    public void deleteCustomEventBadEventId() {
        App app = App.create();
        
        activityEventService.deleteCustomEvent(app, TEST_STUDY_ID, HEALTH_CODE, "eventKey");
    }
    
    @Test
    public void publishSessionFinishedEvent() {
        activityEventService.publishSessionFinishedEvent(TEST_STUDY_ID, HEALTH_CODE, "sessionGuid", FINISHED_ON);
        
        verify(activityEventDao).publishEvent(eventCaptor.capture());
        
        ActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
        assertEquals(event.getEventId(), "session:sessionGuid:finished");
        assertNull(event.getAnswerValue());
        assertEquals(event.getTimestamp(), FINISHED_ON);
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void publishSessionFinishedEventValidates() {
        activityEventService.publishSessionFinishedEvent(TEST_STUDY_ID, HEALTH_CODE, "sessionGuid", null);
    }
    
    @Test
    public void publishAssessmentFinishedEvent() {
        activityEventService.publishAssessmentFinishedEvent(TEST_STUDY_ID, HEALTH_CODE, "asmt-id", FINISHED_ON);
        
        verify(activityEventDao).publishEvent(eventCaptor.capture());
        
        ActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
        assertEquals(event.getEventId(), "assessment:asmt-id:finished");
        assertNull(event.getAnswerValue());
        assertEquals(event.getTimestamp(), FINISHED_ON);
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void publishAssessmentFinishedEventValidates() {
        activityEventService.publishAssessmentFinishedEvent(TEST_STUDY_ID, null, "asmt-id", FINISHED_ON);
    }
    
    private ActivityEvent getEventByKey(List<ActivityEvent> results, String key) {
        return results.stream()
                .filter(event -> event.getEventId().equals(key))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find activity event"));
    }
}
