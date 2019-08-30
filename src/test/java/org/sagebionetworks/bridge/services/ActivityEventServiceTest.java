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
import static org.testng.Assert.assertEquals;
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
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ActivityEventServiceTest {
    private static final String STUDY_ID = "test-study";
    private static final String HEALTH_CODE = "BBB";
    private static final DateTime ACTIVITIES_RETRIEVED = DateTime.parse("2017-05-27T00:00:00.000Z");
    private static final DateTime ENROLLMENT = DateTime.parse("2017-05-28T00:00:00.000Z");
    private static final DateTime CREATED_ON = DateTime.parse("2017-05-26T00:00:00.000Z");
    
    @Spy
    private ActivityEventService activityEventService;
    
    @Mock
    private ActivityEventDao activityEventDao;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private ParticipantService mockParticipantService;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        activityEventService = new ActivityEventService();
        
        activityEventService.setActivityEventDao(activityEventDao);
        activityEventService.setStudyService(mockStudyService);
        activityEventService.setParticipantService(mockParticipantService);
    }

    @Test
    public void canPublishCustomEvent() throws Exception {
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("eventKey1", "eventKey2"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(study, "healthCode", "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:eventKey1");
        assertEquals(activityEvent.getHealthCode(), "healthCode");
        assertEquals(activityEvent.getTimestamp().longValue(), timestamp.getMillis());
    }

    @Test
    public void canPublishCustomEventFromAutomaticEvents() {
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("myEvent"));
        study.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "myEvent:P3D"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now().plusDays(3);
        activityEventService.publishCustomEvent(study, "healthCode", "3-days-after-enrollment",
                timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals(activityEvent.getEventId(), "custom:3-days-after-enrollment");
        assertEquals(activityEvent.getHealthCode(), "healthCode");
        assertEquals(activityEvent.getTimestamp().longValue(), timestamp.getMillis());
    }

    @Test
    public void cannotPublishUnknownCustomEvent() throws Exception {
        Study study = Study.create();
        try {
            activityEventService.publishCustomEvent(study, "healthCode", "eventKey5",
                    DateTime.now());
            fail("expected exception");
        } catch (BadRequestException e) {
            assertTrue(e.getMessage().endsWith("eventKey5"));
        }
    }
    
    @Test
    public void canPublishEvent() {
        ActivityEvent event = new Builder().withHealthCode(HEALTH_CODE)
            .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(DateTime.now()).build();

        activityEventService.publishActivityEvent(event);
        
        verify(activityEventDao).publishEvent(eq(event));
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canPublishCreatedOn() {
        DateTime now = DateTime.now();
        
        activityEventService.publishCreatedOnEvent(HEALTH_CODE, now);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "created_on");
        assertEquals(argument.getValue().getTimestamp(), new Long(now.getMillis()));
        assertEquals(argument.getValue().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void canGetActivityEventMap() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE)).thenReturn(map);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(STUDY_ID, HEALTH_CODE);
        assertEquals(results.get("activities_retrieved"), ACTIVITIES_RETRIEVED);
        assertEquals(results.get("enrollment"), ENROLLMENT);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), ACTIVITIES_RETRIEVED);
        assertEquals(results.size(), 4);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE);
        verify(mockStudyService, never()).getStudy(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), anyBoolean());
    }
    
    @Test
    public void canSetStudyStartDateWithEnrollment() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE)).thenReturn(map);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(STUDY_ID, HEALTH_CODE);
        assertEquals(results.get("enrollment"), ENROLLMENT);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), ENROLLMENT);
        assertEquals(results.size(), 3);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE);
        verify(mockStudyService, never()).getStudy(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), eq(false));
    }
    
    @Test
    public void canSetStudyStartDateWithActivitiesRetrieved() {
        Map<String, DateTime> map = Maps.newHashMap();
        map.put("enrollment", ENROLLMENT);
        map.put("created_on", CREATED_ON);
        map.put("activities_retrieved", ACTIVITIES_RETRIEVED);
        when(activityEventDao.getActivityEventMap(HEALTH_CODE)).thenReturn(map);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(STUDY_ID, HEALTH_CODE);
        assertEquals(results.get("activities_retrieved"), ACTIVITIES_RETRIEVED);
        assertEquals(results.get("enrollment"), ENROLLMENT);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), ACTIVITIES_RETRIEVED);
        assertEquals(results.size(), 4);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE);
        verify(mockStudyService, never()).getStudy(anyString());
        verify(mockParticipantService, never()).getParticipant(any(), anyString(), eq(false));
    }
    
    @Test
    public void canSetStudyStartDateWithCreatedOn() {
        Map<String, DateTime> map = Maps.newHashMap();
        when(activityEventDao.getActivityEventMap(HEALTH_CODE)).thenReturn(map);
        
        Study study = Study.create();
        study.setIdentifier(STUDY_ID);
        when(mockStudyService.getStudy(STUDY_ID)).thenReturn(study);
        
        StudyParticipant studyParticipant = new StudyParticipant.Builder().withCreatedOn(CREATED_ON).build();
        when(mockParticipantService.getParticipant(study, "healthcode:BBB", false)).thenReturn(studyParticipant);
        
        Map<String, DateTime> results = activityEventService.getActivityEventMap(STUDY_ID, HEALTH_CODE);
        assertEquals(results.get("created_on"), CREATED_ON);
        assertEquals(results.get("study_start_date"), CREATED_ON);
        assertEquals(results.size(), 2);
        
        verify(activityEventDao).getActivityEventMap(HEALTH_CODE);
        verify(mockStudyService).getStudy(STUDY_ID);
        verify(mockParticipantService).getParticipant(study, "healthcode:"+HEALTH_CODE, false);
    }
    
    @Test
    public void canDeleteActivityEvents() {
        activityEventService.deleteActivityEvents(HEALTH_CODE);
        
        verify(activityEventDao).deleteActivityEvents(HEALTH_CODE);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badPublicDoesntCallDao() {
        try {
            activityEventService.publishActivityEvent((ActivityEvent) null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badGetDoesntCallDao() {
        try {
            activityEventService.getActivityEventMap(STUDY_ID, null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badDeleteDoesntCallDao() {
        try {
            activityEventService.deleteActivityEvents(null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void canPublishEnrollmentEvent() {
        DateTime now = DateTime.now();
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();

        activityEventService.publishEnrollmentEvent(Study.create(),"AAA-BBB-CCC", signature);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "enrollment");
        assertEquals(argument.getValue().getTimestamp(), new Long(now.getMillis()));
        assertEquals(argument.getValue().getHealthCode(), "AAA-BBB-CCC");
    }

    @Test
    public void canPublishEnrollmentEventWithAutomaticCustomEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
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
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", signature);

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
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", new ConsentSignature.Builder().build());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void whenActivitiesRetrievedEventFailsPublishNoAutomaticEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishActivitiesRetrieved(study,"AAA-BBB-CCC", DateTime.now());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void whenEnrollmentEventFailsPublishNoAutomaticEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", new ConsentSignature.Builder().build());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }

    @Test
    public void whenCustomEventFailsPublishNoAutomaticEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("myEvent"));
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishCustomEvent(study,"AAA-BBB-CCC", "myEvent", DateTime.now());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void canPublishActivitiesRetrievedEventWithAutomaticCustomEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these events include events that should be triggered for enrollment, 
        // not activities retrieved. These are ignore.
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "activities_retrieved:P3D")
                .put("1-week-after", "activities_retrieved:P1W")
                .put("13-weeks-after", "activities_retrieved:P13W")
                .put("5-years-after", "enrollment:P5Y")
                .put("10-years-after", "enrollment:P10Y").build());

        // Create consent signature
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishActivitiesRetrieved(study, "AAA-BBB-CCC", retrieved);

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
    public void canPublishCustomEventWithAutomaticCustomEvents() {
        // This also verifies the correct parsing of the custom event key, which contains a colon.
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("myEvent"));
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        DateTime timestamp = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishCustomEvent(study, "AAA-BBB-CCC", "myEvent", timestamp);

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

        activityEventService.publishQuestionAnsweredEvent("healthCode", answer);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals(argument.getValue().getEventId(), "question:BBB-CCC-DDD:answered");
        assertEquals(argument.getValue().getTimestamp(), new Long(now.getMillis()));
        assertEquals(argument.getValue().getHealthCode(), "healthCode");
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
}
