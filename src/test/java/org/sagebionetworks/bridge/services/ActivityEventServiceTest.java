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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
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
    
    @Spy
    private ActivityEventService activityEventService;
    
    @Mock
    private ActivityEventDao activityEventDao;
    
    @Mock
    private AppService mockAppService;
    
    @Mock
    private ParticipantService mockParticipantService;
    
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
        app.setActivityEventKeys(ImmutableSet.of("eventKey1", "eventKey2"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(app, HEALTH_CODE, "eventKey1", timestamp, null);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:eventKey1");
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE);
        assertNull(activityEvent.getStudyId());
        assertEquals(activityEvent.getTimestamp().longValue(), timestamp.getMillis());
    }

    @Test
    public void canPublishStudyScopedCustomEvent() throws Exception {
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of("eventKey1", "eventKey2"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(app, HEALTH_CODE, "eventKey1", timestamp, TEST_STUDY_ID);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:eventKey1");
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
        assertEquals(activityEvent.getStudyId(), TEST_STUDY_ID);
        assertEquals(activityEvent.getTimestamp().longValue(), timestamp.getMillis());
    }
    
    @Test
    public void canPublishCustomEventFromAutomaticEvents() {
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of("myEvent"));
        app.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "myEvent:P3D"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now().plusDays(3);
        activityEventService.publishCustomEvent(app, HEALTH_CODE, "3-days-after-enrollment",
                timestamp, null);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:3-days-after-enrollment");
        assertEquals(activityEvent.getHealthCode(), HEALTH_CODE);
        assertEquals(activityEvent.getTimestamp().longValue(), timestamp.getMillis());
    }

    @Test
    public void cannotPublishUnknownCustomEvent() throws Exception {
        App app = App.create();
        try {
            activityEventService.publishCustomEvent(app, HEALTH_CODE, "eventKey5", DateTime.now(), null);
            fail("expected exception");
        } catch (BadRequestException e) {
            assertTrue(e.getMessage().endsWith("eventKey5"));
        }
    }
    
    @Test
    public void canPublishGlobalCreatedOn() {
        DateTime now = DateTime.now();
        
        activityEventService.publishCreatedOnEvent(HEALTH_CODE, now, null);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "created_on");
        assertEquals(argument.getValue().getTimestamp(), new Long(now.getMillis()));
        assertNull(argument.getValue().getStudyId());
        assertEquals(argument.getValue().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void canPublishStudyScopedCreatedOn() {
        DateTime now = DateTime.now();
        
        activityEventService.publishCreatedOnEvent(HEALTH_CODE, now, TEST_STUDY_ID);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(2)).publishEvent(argument.capture());
        
        assertEquals(argument.getAllValues().get(0).getEventId(), "created_on");
        assertEquals(argument.getAllValues().get(0).getTimestamp(), new Long(now.getMillis()));
        assertNull(argument.getAllValues().get(0).getStudyId());
        assertEquals(argument.getAllValues().get(0).getHealthCode(), HEALTH_CODE);

        assertEquals(argument.getAllValues().get(1).getEventId(), "created_on");
        assertEquals(argument.getAllValues().get(1).getTimestamp(), new Long(now.getMillis()));
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
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE, null);
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
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE, null);
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
        
        activityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE, TEST_STUDY_ID);
        
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
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE, null);
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
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE, null);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), CREATED_ON);
        assertEquals(results.size(), 2);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, null);
        verify(mockAppService).getApp(TEST_APP_ID);
        verify(mockParticipantService).getParticipant(app, "healthcode:"+HEALTH_CODE, false);
    }
    
    @Test
    public void canDeleteGlobalActivityEvents() {
        activityEventService.deleteActivityEvents(HEALTH_CODE, null);
        
        verify(activityEventDao).deleteActivityEvents(HEALTH_CODE, null);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canDeleteStudyScopedActivityEvents() {
        activityEventService.deleteActivityEvents(HEALTH_CODE, TEST_STUDY_ID);
        
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
        DateTime now = DateTime.now();
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();

        activityEventService.publishEnrollmentEvent(App.create(),"AAA-BBB-CCC", signature, null);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "enrollment");
        assertEquals(argument.getValue().getTimestamp(), new Long(now.getMillis()));
        assertNull(argument.getValue().getStudyId());
        assertEquals(argument.getValue().getHealthCode(), "AAA-BBB-CCC");
    }

    @Test
    public void canPublishStudyScopedEnrollmentEvent() {
        DateTime now = DateTime.now();
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();

        activityEventService.publishEnrollmentEvent(App.create(), HEALTH_CODE, signature, TEST_STUDY_ID);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(2)).publishEvent(argument.capture());
        
        assertEquals(argument.getAllValues().get(0).getEventId(), "enrollment");
        assertEquals(argument.getAllValues().get(0).getTimestamp(), new Long(now.getMillis()));
        assertNull(argument.getAllValues().get(0).getStudyId());
        assertEquals(argument.getAllValues().get(0).getHealthCode(), HEALTH_CODE);

        assertEquals(argument.getAllValues().get(1).getEventId(), "enrollment");
        assertEquals(argument.getAllValues().get(1).getTimestamp(), new Long(now.getMillis()));
        assertEquals(argument.getAllValues().get(1).getStudyId(), TEST_STUDY_ID);
        assertEquals(argument.getAllValues().get(1).getHealthCode(), HEALTH_CODE + ":" + TEST_STUDY_ID);
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
        DateTime enrollment = DateTime.parse("2018-04-04T16:00-0700");
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(enrollment.minusDays(10).getMillis())
                .withSignedOn(enrollment.getMillis()).build();

        when(activityEventDao.publishEvent(any())).thenReturn(true);
        
        // Execute
        activityEventService.publishEnrollmentEvent(app,"AAA-BBB-CCC", signature, null);

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals(publishedEventList.get(0).getEventId(), "enrollment");
        assertEquals(publishedEventList.get(0).getTimestamp().longValue(), enrollment.getMillis());
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(1).getEventId(), "custom:3-days-after");
        assertEquals(publishedEventList.get(1).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"));
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(2).getEventId(), "custom:1-week-after");
        assertEquals(publishedEventList.get(2).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"));
        assertEquals(publishedEventList.get(2).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(3).getEventId(), "custom:13-weeks-after");
        assertEquals(publishedEventList.get(3).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-07-04T16:00-0700"));
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
        
        activityEventService.publishEnrollmentEvent(app,"AAA-BBB-CCC", new ConsentSignature.Builder().build(), null);
        
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
        
        activityEventService.publishActivitiesRetrieved(app,"AAA-BBB-CCC", DateTime.now(), null);
        
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
        
        activityEventService.publishEnrollmentEvent(app,"AAA-BBB-CCC", new ConsentSignature.Builder().build(), null);
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }

    @Test
    public void whenCustomEventFailsPublishNoAutomaticEvents() {
        // Configure app with automatic custom events
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of("myEvent"));
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishCustomEvent(app,"AAA-BBB-CCC", "myEvent", DateTime.now(), null);
        
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
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishActivitiesRetrieved(app, "AAA-BBB-CCC", retrieved, null);

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals(publishedEventList.get(0).getEventId(), "activities_retrieved");
        assertEquals(publishedEventList.get(0).getTimestamp().longValue(), retrieved.getMillis());
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(1).getEventId(), "custom:3-days-after");
        assertEquals(publishedEventList.get(1).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"));
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(2).getEventId(), "custom:1-week-after");
        assertEquals(publishedEventList.get(2).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"));
        assertEquals(publishedEventList.get(2).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(3).getEventId(), "custom:13-weeks-after");
        assertEquals(publishedEventList.get(3).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-07-04T16:00-0700"));
        assertEquals(publishedEventList.get(3).getHealthCode(), "AAA-BBB-CCC");
    }
    
    @Test
    public void canPublishStudyScopedActivitiesRetrievedEvent() {
        // Configure app with automatic custom events
        App app = App.create();

        // Create consent signature
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishActivitiesRetrieved(app, "AAA-BBB-CCC", retrieved, TEST_STUDY_ID);

        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        
        verify(activityEventDao, times(2)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();
        assertEquals(publishedEventList.get(0).getEventId(), "activities_retrieved");
        assertEquals(publishedEventList.get(0).getTimestamp().longValue(), retrieved.getMillis());
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");
        assertNull(publishedEventList.get(0).getStudyId());

        assertEquals(publishedEventList.get(1).getEventId(), "activities_retrieved");
        assertEquals(publishedEventList.get(1).getTimestamp().longValue(), retrieved.getMillis());
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC:" + TEST_STUDY_ID);
        assertEquals(publishedEventList.get(1).getStudyId(), TEST_STUDY_ID);
    }
    
    @Test
    public void canPublishCustomEventWithAutomaticCustomEvents() {
        // This also verifies the correct parsing of the custom event key, which contains a colon.
        App app = App.create();
        app.setActivityEventKeys(ImmutableSet.of("myEvent"));
        app.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        DateTime timestamp = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishCustomEvent(app, "AAA-BBB-CCC", "myEvent", timestamp, null);

        // Verify published events (3)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(3)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();
        
        assertEquals(publishedEventList.get(0).getEventId(), "custom:myEvent");
        assertEquals(publishedEventList.get(0).getTimestamp().longValue(), timestamp.getMillis());
        assertEquals(publishedEventList.get(0).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(1).getEventId(), "custom:3-days-after");
        assertEquals(publishedEventList.get(1).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"));
        assertEquals(publishedEventList.get(1).getHealthCode(), "AAA-BBB-CCC");

        assertEquals(publishedEventList.get(2).getEventId(), "custom:1-week-after");
        assertEquals(publishedEventList.get(2).getTimestamp().longValue(),
                DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"));
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
        assertEquals(argument.getValue().getTimestamp(), new Long(now.getMillis()));
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
        long finishedOn = DateTime.now().getMillis();
        
        ScheduledActivity schActivity = ScheduledActivity.create();
        schActivity.setGuid("AAA:"+DateTime.now().toLocalDateTime());
        schActivity.setActivity(TestUtils.getActivity1());
        schActivity.setLocalExpiresOn(LocalDateTime.now().plusDays(1));
        schActivity.setStartedOn(DateTime.now().getMillis());
        schActivity.setFinishedOn(finishedOn);
        schActivity.setHealthCode(HEALTH_CODE);


        activityEventService.publishActivityFinishedEvent(schActivity);
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());

        ActivityEvent event = argument.getValue();
        assertEquals(event.getHealthCode(), HEALTH_CODE);
        assertEquals(event.getEventId(), "activity:AAA:finished");
        assertEquals(event.getTimestamp().longValue(), finishedOn);
    }
    
    @Test
    public void getGlobalActivityEventList() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE, null)).thenReturn(map);
        
        List<ActivityEvent> results = activityEventService.getActivityEventList(TEST_APP_ID, HEALTH_CODE, null);
        
        ActivityEvent ar = getEventByKey(results, "activities_retrieved");
        assertEquals(ar.getTimestamp(), Long.valueOf(ACTIVITIES_RETRIEVED.getMillis()));
        
        ActivityEvent en = getEventByKey(results, "enrollment");
        assertEquals(en.getTimestamp(), Long.valueOf(ENROLLMENT.getMillis()));
        
        ActivityEvent co = getEventByKey(results, "created_on");
        assertEquals(co.getTimestamp(), Long.valueOf(CREATED_ON.getMillis()));
        
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
                .getActivityEventList(TEST_APP_ID, HEALTH_CODE, TEST_STUDY_ID);
        
        ActivityEvent ar = getEventByKey(results, "activities_retrieved");
        assertEquals(ar.getTimestamp(), Long.valueOf(ACTIVITIES_RETRIEVED.getMillis()));
        
        ActivityEvent en = getEventByKey(results, "enrollment");
        assertEquals(en.getTimestamp(), Long.valueOf(ENROLLMENT.getMillis()));
        
        ActivityEvent co = getEventByKey(results, "created_on");
        assertEquals(co.getTimestamp(), Long.valueOf(CREATED_ON.getMillis()));
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE, TEST_STUDY_ID);
        verify(mockAppService, never()).getApp(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), anyBoolean());
    }
    
    private ActivityEvent getEventByKey(List<ActivityEvent> results, String key) {
        return results.stream()
                .map(event -> {
                    System.out.println(event.getEventId());
                    return event;
                })
                .filter(event -> event.getEventId().equals(key))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find activity event"));
    }
}
