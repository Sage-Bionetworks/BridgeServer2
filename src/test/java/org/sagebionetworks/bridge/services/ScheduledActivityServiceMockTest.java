package org.sagebionetworks.bridge.services;

import static java.util.stream.Collectors.toSet;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.services.ScheduledActivityService.V3_FILTER;
import static org.sagebionetworks.bridge.validators.ScheduleContextValidator.MAX_DATE_RANGE_IN_DAYS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.Period;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ScheduledActivityServiceMockTest {

    private static final String TIME_PORTION = ":2017-02-23T10:00:00.000";

    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final String HEALTH_CODE = "healthCode";
    
    private static final String USER_ID = "CCC";
    
    private static final String ACTIVITY_GUID = "activityGuid";
    
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2015-04-03T10:40:34.000-07:00");
    
    /** Note that the time zone has changed at the time of the request */
    private static final DateTime NOW = DateTime.parse("2017-02-23T14:25:51.195-08:00");
    
    private static final DateTime STARTS_ON = NOW.minusDays(1);
    
    private static final DateTime ENDS_ON = NOW.plusDays(2);

    private static final DateTimeZone TIME_ZONE = STARTS_ON.getChronology().getZone();
    
    private ScheduledActivityService service;
    
    @Mock
    private SchedulePlanService schedulePlanService;
    
    @Mock
    private ScheduledActivityDao activityDao;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private SurveyService surveyService;
    
    @Mock
    private AppConfigService appConfigService;
    
    @Mock
    private Survey survey;
    
    @Mock
    private Study study;
    
    @Captor
    private ArgumentCaptor<List<ScheduledActivity>> scheduledActivityListCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        
        service = new ScheduledActivityService();
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false))
                .thenReturn(TestUtils.getSchedulePlans(TEST_STUDY));

        Map<String,DateTime> map = ImmutableMap.of();
        when(activityEventService.getActivityEventMap(anyString())).thenReturn(map);
        
        when(activityDao.getActivity(any(), anyString(), anyString(), eq(true))).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
            schActivity.setTimeZone((DateTimeZone)args[0]);
            schActivity.setHealthCode((String)args[1]);
            schActivity.setGuid((String)args[2]);
            return schActivity;
        });
        
        doReturn(SURVEY_CREATED_ON.getMillis()).when(survey).getCreatedOn();
        doReturn("identifier").when(survey).getIdentifier();
        when(surveyService.getSurveyMostRecentlyPublishedVersion(
                eq(TEST_STUDY), any(), eq(false))).thenReturn(survey);
        
        service.setSchedulePlanService(schedulePlanService);
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        service.setSurveyService(surveyService);
        service.setAppConfigService(appConfigService);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
        BridgeUtils.setRequestContext(null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityHistoryEnforcesMinPageSize() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON, ENDS_ON, null, 2);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityHistoryEnforcesMaxPageSize() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON, ENDS_ON, null, 200);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityHistoryEnforcesFullDateRangeWhenNoStart() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, null, ENDS_ON, null, 40);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityHistoryEnforcesFullDateRangeWhenNoEnd() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, STARTS_ON, null, null, 40);
    }
    
    @Test
    public void getScheduledActivitiesPublishesEvent() {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        service.getScheduledActivities(study, context);
        
        verify(activityEventService).publishActivitiesRetrieved(eq(study), eq(HEALTH_CODE), any(DateTime.class));
    }
    
    @Test
    public void getScheduledActivitiesV4PublishesEvent() {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        service.getScheduledActivitiesV4(study, context);

        verify(activityEventService).publishActivitiesRetrieved(eq(study), eq(HEALTH_CODE), any(DateTime.class));
    }
    
    @Test
    public void activityHistoryDefaultsDateRange() {
        DateTimeUtils.setCurrentMillisFixed(STARTS_ON.getMillis());
        
        ArgumentCaptor<DateTime> startCaptor = ArgumentCaptor.forClass(DateTime.class);
        ArgumentCaptor<DateTime> endCaptor = ArgumentCaptor.forClass(DateTime.class);
        
        ScheduledActivityService serviceSpy = Mockito.spy(service);
        when(serviceSpy.getDateTime()).thenReturn(DateTime.now(TIME_ZONE));
        
        serviceSpy.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, null, null, null, 40);
        verify(activityDao).getActivityHistoryV2(eq(HEALTH_CODE), eq(ACTIVITY_GUID), startCaptor.capture(),
                endCaptor.capture(), eq(null), eq(40));
        
        assertTrue(STARTS_ON.minusDays(MAX_DATE_RANGE_IN_DAYS / 2).isEqual(startCaptor.getValue()));
        assertTrue(STARTS_ON.plusDays(MAX_DATE_RANGE_IN_DAYS / 2).isEqual(endCaptor.getValue()));
        
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityHistoryEnforcesDateRangeEndAfterStart() {
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, ENDS_ON, STARTS_ON, null, 200);
    }

    // This used to be a condition of using the service, but we have removed it because it's not
    // necessary to process the request and at least some clients break this constraint when submitting
    // a time range that spans a time zone change (such as daylight savings time).
    @Test
    public void activityHistoryDoesNotEnforceSameEndAndStartTimeZone() {
        // won't be same as default time zone, since this is not a real timezone
        DateTimeZone otherTimeZone = DateTimeZone.forOffsetHoursMinutes(4, 17);
        service.getActivityHistory(HEALTH_CODE, ACTIVITY_GUID,STARTS_ON,
                ENDS_ON.withZone(otherTimeZone), null, 100);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getScheduledActivities(study, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .withInitialTimeZone(DateTimeZone.UTC).withEndsOn(NOW.minusSeconds(1)).build());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getScheduledActivities(study, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withAccountCreatedOn(ENROLLMENT.minusHours(2))
            .withInitialTimeZone(DateTimeZone.UTC)
            .withEndsOn(NOW.plusDays(ScheduleContextValidator.MAX_DATE_RANGE_IN_DAYS).plusSeconds(1)).build());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void rejectsListOfActivitiesWithNullElement() {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        scheduledActivities.set(0, null);
        
        service.updateScheduledActivities("AAA", scheduledActivities);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void rejectsListOfActivitiesWithTaskThatLacksGUID() {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        scheduledActivities.get(0).setGuid(null);
        
        service.updateScheduledActivities("AAA", scheduledActivities);
    }
    
    @Test
    public void removesDuplicateActivities() {
        ScheduleContext context = createScheduleContext(ENDS_ON.plusDays(3)).build();
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        int size = scheduledActivities.size();
        
        // Now duplicate the members of the list
        scheduledActivities.add(scheduledActivities.get(0));
        
        // Now mark them all finished so they are all persisted
        for (ScheduledActivity activity : scheduledActivities) {
            activity.setFinishedOn(DateTimeUtils.currentTimeMillis());
        }
        
        service.updateScheduledActivities("AAA", scheduledActivities);
        
        verify(activityDao).updateActivities(eq("AAA"), scheduledActivityListCaptor.capture());
        assertEquals(scheduledActivityListCaptor.getValue().size(), size);
    }
    
    @Test
    public void missingEnrollmentEventIsSuppliedFromAccountCreatedOn() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withInitialTimeZone(DateTimeZone.UTC)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2))
                .withEndsOn(ENDS_ON)
                .withHealthCode(HEALTH_CODE)
                .withUserId(USER_ID).build();        
        
        List<ScheduledActivity> activities = service.getScheduledActivities(study, context);
        assertTrue(activities.size() > 0);
    }
    
    @Test
    public void surveysAreResolved() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withInitialTimeZone(DateTimeZone.UTC)
                .withAccountCreatedOn(ENROLLMENT.minusHours(2))
                .withEndsOn(ENDS_ON)
                .withHealthCode(HEALTH_CODE)
                .withUserId(USER_ID).build();        
        
        List<ScheduledActivity> activities = service.getScheduledActivities(study, context);
        //noinspection Convert2streamapi
        for (ScheduledActivity activity : activities) {
            if (activity.getActivity().getActivityType() == ActivityType.SURVEY) {
                assertEquals(activity.getActivity().getSurvey().getCreatedOn().getMillis(),
                        SURVEY_CREATED_ON.getMillis());
            }
        }
    }
    
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void updateActivitiesWorks() throws Exception {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(context);
        
        int count = scheduledActivities.size();
        scheduledActivities.get(0).setStartedOn(NOW.getMillis());
        scheduledActivities.get(1).setFinishedOn(NOW.getMillis());
        scheduledActivities.get(2).setFinishedOn(NOW.getMillis());
        scheduledActivities.get(3).setClientData(TestUtils.getClientData());
        
        ArgumentCaptor<List> updateCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ScheduledActivity> publishCapture = ArgumentCaptor.forClass(ScheduledActivity.class);
        
        service.updateScheduledActivities("BBB", scheduledActivities);
        
        verify(activityDao).updateActivities(anyString(), updateCapture.capture());
        // Three activities have timestamp updates and need to be persisted
        verify(activityDao, times(count)).getActivity(any(), anyString(), anyString(), eq(true));
        // Two activities have been finished and generate activity finished events
        verify(activityEventService, times(2)).publishActivityFinishedEvent(publishCapture.capture());
        
        List<DynamoScheduledActivity> dbActivities = (List<DynamoScheduledActivity>)updateCapture.getValue();
        assertEquals(dbActivities.size(), 4);
        
        // Correct saved activities
        assertEquals(dbActivities.get(0).getGuid(), scheduledActivities.get(0).getGuid());
        assertEquals(dbActivities.get(1).getGuid(), scheduledActivities.get(1).getGuid());
        assertEquals(dbActivities.get(2).getGuid(), scheduledActivities.get(2).getGuid());
        assertEquals(dbActivities.get(3).getClientData(), scheduledActivities.get(3).getClientData());
        
        // Correct published activities.
        ScheduledActivity publishedActivity2 = publishCapture.getAllValues().get(0);
        assertEquals(publishedActivity2.getGuid(), scheduledActivities.get(2).getGuid());
        ScheduledActivity publishedActivity1 = publishCapture.getAllValues().get(1);
        assertEquals(publishedActivity1.getGuid(), scheduledActivities.get(1).getGuid());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityListsWithTooLargeClientDataRejected() throws Exception {
        JsonNode node = TestUtils.getClientData();
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (int i=0; i < 140; i++) {
            array.add(node);
        }
        
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.get(0).setClientData(array);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityListWithNullsRejected() {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.set(0, null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void activityListWithNullGuidRejected() {
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(context);
        activities.get(1).setGuid(null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test
    public void deleteActivitiesDoesDelete() {
        service.deleteActivitiesForUser("BBB");
        
        verify(activityDao).deleteActivitiesForUser("BBB");
        verifyNoMoreInteractions(activityDao);
    }

    @Test
    public void deleteScheduledActivitiesForUser() {
        service.deleteActivitiesForUser("AAA");
        verify(activityDao).deleteActivitiesForUser("AAA");
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void deleteActivitiesForUserRejectsBadValue() {
        service.deleteActivitiesForUser(null);
    }
    
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void deleteActivitiesForSchedulePlanRejectsBadValue() {
        service.deleteActivitiesForUser("  ");
    }

    @Test
    public void newActivitiesIncludedInSaveAndResultsV3() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb));
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(createStartedActivities("BBB"+TIME_PORTION));
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, createScheduleContext(NOW).build());
        assertActivityGuids(returnedActivities, "AAA", "BBB");
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves, "AAA");
    }
    
    @Test
    public void newActivitiesIncludedInSaveAndResultsV4() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb));
        
        ForwardCursorPagedResourceList<ScheduledActivity> list = new ForwardCursorPagedResourceList<>(createStartedActivities("BBB"+TIME_PORTION), null);
        when(activityDao.getActivityHistoryV2(HEALTH_CODE, "BBB", NOW, NOW, null, API_MAXIMUM_PAGE_SIZE))
                .thenReturn(list);        
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivitiesV4(study, createScheduleContext(NOW).build());
        assertActivityGuids(returnedActivities, "AAA", "BBB");
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves, "AAA");
    }
    
    @Test
    public void persistedAndScheduledIncludedInResultsV3() {
        SchedulePlan ccc = schedulePlan("CCC");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(ccc));
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(createStartedActivities("CCC"+TIME_PORTION));
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, createScheduleContext(NOW).build());
        assertNotNull(returnedActivities.get(0).getStartedOn());
        assertActivityGuids(returnedActivities, "CCC");
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves);
    }

    @Test
    public void expiredTasksExcludedFromCalculationsV3() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb));
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(createExpiredActivities("AAA"+TIME_PORTION,"CCC"+TIME_PORTION));
        
        // Ask for activities in the past so they will be expired.
        ScheduleContext contextAhead = new ScheduleContext.Builder()
                .withContext(createScheduleContext(NOW).build())
                .withStartsOn(NOW.minusMonths(1).minusDays(1))
                .withEndsOn(NOW.minusMonths(1)).build();
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, contextAhead);
        assertActivityGuids(returnedActivities);
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves);
    }
    
    @Test
    public void finishedTasksExcludedFromResultsV3() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        SchedulePlan ccc = schedulePlan("CCC");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb,ccc));
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(Lists.newArrayList(createFinishedActivities("AAA"+TIME_PORTION).get(0),
                createStartedActivities("BBB"+TIME_PORTION).get(0)));
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, createScheduleContext(NOW).build());
        assertActivityGuids(returnedActivities, "BBB", "CCC");
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves, "CCC");
    }
    
    @Test
    public void expiredFinishedTasksIncludedInResultsV4() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        SchedulePlan ccc = schedulePlan("CCC");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb,ccc));
        
        List<ScheduledActivity> db = Lists.newArrayList(createExpiredActivities("AAA"+TIME_PORTION).get(0),
                createFinishedActivities("BBB"+TIME_PORTION).get(0));
        when(activityDao.getActivityHistoryV2(HEALTH_CODE, "BBB", NOW, NOW, null, API_MAXIMUM_PAGE_SIZE))
                .thenReturn(new ForwardCursorPagedResourceList<>(db, null));
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivitiesV4(study, createScheduleContext(NOW).build());
        assertActivityGuids(returnedActivities, "AAA", "BBB", "CCC");
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves, "CCC");
    }

    @Test
    public void newAndExistingActivitiesAreMergedV3() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        SchedulePlan ccc = schedulePlan("CCC");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb,ccc));
        List<ScheduledActivity> db = Lists.newArrayList(createFinishedActivities("AAA"+TIME_PORTION).get(0),
                createStartedActivities("BBB"+TIME_PORTION).get(0));
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(db);
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, createScheduleContext(NOW).build());
        assertActivityGuids(returnedActivities, "BBB", "CCC");
        
        ScheduledActivity activity = returnedActivities.stream()
                .filter(act -> { return act.getGuid().startsWith("BBB"); }).findFirst().get();
        assertTrue(activity.getStartedOn() > 0L);
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves, "CCC");
    }
    
    @Test
    public void newAndExistingActivitiesAreMergedV4() {
        SchedulePlan aaa = schedulePlan("AAA");
        SchedulePlan bbb = schedulePlan("BBB");
        SchedulePlan ccc = schedulePlan("CCC");
        
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(aaa,bbb,ccc));
        
        List<ScheduledActivity> db = createStartedActivities("AAA"+TIME_PORTION,"CCC"+TIME_PORTION);
        when(activityDao.getActivityHistoryV2(HEALTH_CODE, "BBB", NOW, NOW, null, API_MAXIMUM_PAGE_SIZE))
                .thenReturn(new ForwardCursorPagedResourceList<>(db, null));
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivitiesV4(study, createScheduleContext(NOW).build());
        assertActivityGuids(returnedActivities, "AAA", "BBB", "CCC");
        assertNotNull(getByGuidPrefix(returnedActivities, "AAA").getStartedOn());
        assertNotNull(getByGuidPrefix(returnedActivities, "CCC").getStartedOn());
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertActivityGuids(saves, "BBB");
    }
    
    private ScheduledActivity getByGuidPrefix(List<ScheduledActivity> activities, String prefix) {
        for (ScheduledActivity activity : activities) {
            if (activity.getGuid().startsWith(prefix)) {
                return activity;
            }
        }
        return null;
    }

    @Test
    public void taskNotStartedIsUpdated() {
        // Activity in DDB has survey reference pointing to createdOn 1234. Newly created activity has survey reference
        // pointing to createdOn 5678. Activity in DDB hasn't been started. We should update the activity in DDB.

        // Create task references and activities.
        // Schedule plan has been updated with a new activity that will be used in scheduled activity
        Activity newActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(5678)).build();
        SchedulePlan ccc = schedulePlan(newActivity);
        
        // This is the schedule plan returned from the DB with the new Activity
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(ccc));
        
        // This is the persisted activity with the oldActivity
        Activity oldActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(1234)).build();
        List<ScheduledActivity> db = createNewActivities("CCC"+TIME_PORTION);
        db.get(0).setActivity(oldActivity);
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(db);
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, createScheduleContext(NOW).build());
        assertEquals(returnedActivities.size(), 1);
        assertEquals(returnedActivities.get(0).getActivity().getSurvey().getCreatedOn().getMillis(), 5678);
        
        verify(activityDao).getActivities(eq(TIME_ZONE), any());
    }

    @Test
    public void taskNotStartedIsUpdatedV4() {
        // Activity in DDB has survey reference pointing to createdOn 1234. Newly created activity has survey reference
        // pointing to createdOn 5678. Activity in DDB hasn't been started. We should update the activity in DDB.

        // Create task references and activities.
        // Schedule plan has been updated with a new activity that will be used in scheduled activity
        Activity newActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(5678)).build();
        SchedulePlan ccc = schedulePlan(newActivity);
        
        // This is the schedule plan returned from the DB with the new Activity
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(ccc));
        
        // This is the persisted activity with the oldActivity
        Activity oldActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(1234)).build();
        List<ScheduledActivity> db = createNewActivities("CCC"+TIME_PORTION);
        db.get(0).setActivity(oldActivity);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = new ForwardCursorPagedResourceList<>(db, null);
        when(activityDao.getActivityHistoryV2(any(), any(), any(), any(), any(), anyInt())).thenReturn(page);
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivitiesV4(study, createScheduleContext(NOW).build());
        assertEquals(returnedActivities.size(), 1);
        assertEquals(returnedActivities.get(0).getActivity().getSurvey().getCreatedOn().getMillis(), 5678);
        
        verify(activityDao).getActivityHistoryV2(any(), any(), any(), any(), any(), anyInt());
    }
    
    @Test
    public void taskNotStartedWithClientDataNotUpdated() {
        // Activity in DDB has survey reference pointing to createdOn 1234. Newly created activity has survey reference
        // pointing to createdOn 5678. Activity in DDB has client data. DB activity should be in returned activities.

        // Create task references and activities.
        // Schedule plan has been updated with a new activity that will be used in scheduled activity
        Activity newActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(5678)).build();
        SchedulePlan ccc = schedulePlan(newActivity);
        
        // This is the schedule plan returned from the DB with the new Activity
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(ccc));
        
        // This is the persisted activity with the oldActivity
        Activity oldActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(1234)).build();
        List<ScheduledActivity> db = createNewActivities("CCC"+TIME_PORTION);
        for (ScheduledActivity activity : db) {
            activity.setClientData(TestUtils.getClientData());
        }
        db.get(0).setActivity(oldActivity);
        when(activityDao.getActivities(eq(TIME_ZONE), any())).thenReturn(db);
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivities(study, createScheduleContext(NOW).build());
        assertEquals(returnedActivities.size(), 1);
        assertEquals(returnedActivities.get(0).getActivity().getSurvey().getCreatedOn().getMillis(), 1234);
        assertNotNull(returnedActivities.get(0).getClientData());
        
        verify(activityDao).getActivities(eq(TIME_ZONE), any());
    }

    @Test
    public void taskNotStartedWithClientDataNotUpdatedV4() {
        // Activity in DDB has survey reference pointing to createdOn 1234. Newly created activity has survey reference
        // pointing to createdOn 5678. Activity in DDB has client data. DB activity should be in returned activities.

        // Create task references and activities.
        // Schedule plan has been updated with a new activity that will be used in scheduled activity
        Activity newActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(5678)).build();
        SchedulePlan ccc = schedulePlan(newActivity);
        
        // This is the schedule plan returned from the DB with the new Activity
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(ccc));
        
        // This is the persisted activity with the oldActivity
        Activity oldActivity = new Activity.Builder().withGuid("CCC")
                .withSurvey("my-survey", "my-survey-guid", new DateTime(1234)).build();
        List<ScheduledActivity> db = createNewActivities("CCC"+TIME_PORTION);
        for (ScheduledActivity activity : db) {
            activity.setClientData(TestUtils.getClientData());
        }
        db.get(0).setActivity(oldActivity);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = new ForwardCursorPagedResourceList<>(db, null);
        when(activityDao.getActivityHistoryV2(any(), any(), any(), any(), any(), anyInt())).thenReturn(page);
        
        List<ScheduledActivity> returnedActivities = service.getScheduledActivitiesV4(study, createScheduleContext(NOW).build());
        assertEquals(returnedActivities.size(), 1);
        assertEquals(returnedActivities.get(0).getActivity().getSurvey().getCreatedOn().getMillis(), 1234);
        assertNotNull(returnedActivities.get(0).getClientData());
        
        verify(activityDao).getActivityHistoryV2(any(), any(), any(), any(), any(), anyInt());
    }
    
    @Test
    public void orderActivitiesFiltersAndSorts() {
        DateTime time1 = DateTime.parse("2014-10-01T00:00:00.000Z");
        DateTime time2 = DateTime.parse("2014-10-02T00:00:00.000Z");
        DateTime time3 = DateTime.parse("2014-10-03T00:00:00.000Z");
        DateTime time4 = DateTime.parse("2014-10-04T00:00:00.000Z");
        
        List<ScheduledActivity> list = createNewActivities("AAA", "BBB", "CCC", "DDD");
        list.get(0).setLocalScheduledOn(time2.toLocalDateTime());
        list.get(1).setLocalScheduledOn(time1.toLocalDateTime());
        list.get(2).setLocalScheduledOn(time4.toLocalDateTime());
        list.get(3).setLocalScheduledOn(time3.toLocalDateTime());
        list.get(3).setLocalExpiresOn(NOW.toLocalDateTime().minusDays(1));
        
        List<ScheduledActivity> result = service.orderActivities(list, V3_FILTER);
        assertEquals(result.size(), 3);
        assertEquals(result.get(0).getScheduledOn(), time1);
        assertEquals(result.get(1).getScheduledOn(), time2);
        assertEquals(result.get(2).getScheduledOn(), time4);
        
    }
    
    @Test
    public void complexCriteriaBasedScheduleWorksThroughService() throws Exception {
        // As long as time zone is consistent, the right number of tasks will be generated on 
        // the day of the request, regardless of the hour of the day.
        executeComplexTestInTimeZone(3, DateTimeZone.forOffsetHours(-7));
        executeComplexTestInTimeZone(23, DateTimeZone.forOffsetHours(-7));
        executeComplexTestInTimeZone(3, DateTimeZone.forOffsetHours(8));
        executeComplexTestInTimeZone(23, DateTimeZone.forOffsetHours(8));
    }
    
    @Test
    public void getActivityHistoryV3() throws Exception {
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(-4);
        DateTime startsOn = DateTime.now(timeZone).minusDays(1);
        DateTime endsOn = DateTime.now(timeZone).plusDays(1);
        
        service.getActivityHistory(HEALTH_CODE, ActivityType.SURVEY, "GUID", startsOn, endsOn, "offsetKey", 20);
        
        verify(activityDao).getActivityHistoryV3(HEALTH_CODE, ActivityType.SURVEY, "GUID", startsOn, endsOn,
                "offsetKey", 20);
    }
    
    // Verify in the next two tests that the v3 api is being validated the same way the v2 api is being validated.
    // They share the same code path so it isn't necessary to test every single validation.
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getActivityHistoryV3ValidatesPageSize() {
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(-4);
        DateTime startsOn = DateTime.now(timeZone).minusDays(1);
        DateTime endsOn = DateTime.now(timeZone).plusDays(1);
        
        service.getActivityHistory(HEALTH_CODE, ActivityType.SURVEY, "GUID", startsOn, endsOn, "offsetKey", 20000);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getActivityHistoryV3ValidatesDateRange() {
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(-4);
        DateTime startsOn = DateTime.now(timeZone).minusDays(1);
        DateTime endsOn = DateTime.now(timeZone).plusDays(1);
        
        service.getActivityHistory(HEALTH_CODE, ActivityType.SURVEY, "GUID", endsOn, startsOn, "offsetKey", 20);
    }
    
    @Test
    public void oneTimeActivityDisappearsOutOfTimeRange() throws Exception {
        DateTime endsOn = DateTime.now().plusDays(3);
        ScheduleContext context = createScheduleContext(endsOn).build();
        
        // Reset the mock because we're going to create a one-time schedule and an enrollment date outside of the time window.
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.addActivity(new Activity.Builder().withGuid("guidForCCC").withLabel("Do task CCC").withTask("CCC").build());
        schedule.setLabel("One-time task");
        
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TEST_STUDY);
        ((SimpleScheduleStrategy)plan.getStrategy()).setSchedule(schedule);
        
        reset(schedulePlanService);
        when(schedulePlanService.getSchedulePlans(any(), eq(false))).thenReturn(Lists.newArrayList(plan));
        
        // The time stamp is derived from the account creation timestamp because there are no events in the event
        // map. It's in the time zone that the scheduler is working in.
        String guid = "guidForCCC:" + context.getAccountCreatedOn().withZone(context.getInitialTimeZone()).toLocalDateTime();
        
        ScheduledActivity oneTimeActivity = new DynamoScheduledActivity();
        oneTimeActivity.setGuid(guid);
        oneTimeActivity.setStartedOn(NOW.plusMinutes(5).getMillis());
        oneTimeActivity.setFinishedOn(NOW.plusMinutes(5).getMillis());
        
        mockAllCallsForDbActivities(ImmutableList.of());
        when(activityDao.getActivity(context.getStartsOn().getZone(), HEALTH_CODE, guid, false))
                .thenReturn(oneTimeActivity);
        
        List<ScheduledActivity> scheduledActivities = service.getScheduledActivitiesV4(study, context);
        assertEquals(scheduledActivities.size(), 1);
        assertEquals(scheduledActivities.get(0).getGuid(), guid);
        assertNotNull(scheduledActivities.get(0).getStartedOn());
        assertNotNull(scheduledActivities.get(0).getFinishedOn());
        
        verify(activityDao, times(1)).getActivityHistoryV2(HEALTH_CODE, "guidForCCC", context.getStartsOn(), context.getEndsOn(),
                null, BridgeConstants.API_MAXIMUM_PAGE_SIZE);
        // Retrieve any remaining scheduled activity from the DB to ensure state is maintained. 
        verify(activityDao, times(1)).getActivity(context.getStartsOn().getZone(), HEALTH_CODE, guid, false);
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        
        List<ScheduledActivity> saves = scheduledActivityListCaptor.getValue();
        assertTrue(saves.isEmpty());
    }
    
    private void assertActivityGuids(List<ScheduledActivity> activities, String... guids) {
        Set<String> activityGuids = activities.stream().map(ScheduledActivity::getGuid).collect(toSet());

        Set<String> expectedGuids = Arrays.stream(guids).map(guid -> guid + TIME_PORTION).collect(toSet());
        
        assertEquals(activityGuids, expectedGuids);
    }
    
    private SchedulePlan schedulePlan(String activityGuid) {
        return schedulePlan(new Activity.Builder().withGuid(activityGuid)
                .withLabel("Do task "+activityGuid).withTask(activityGuid).build());
    }
    
    private SchedulePlan schedulePlan(Activity activity) {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval(Period.parse("P1D"));
        schedule.addActivity(activity);
        schedule.setExpires(Period.parse("P1D"));
        schedule.addTimes("10:00");
        schedule.setLabel("Test label for the user");
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid(BridgeUtils.generateGuid());
        plan.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        plan.setStudyKey(TEST_STUDY.getIdentifier());
        plan.setStrategy(strategy);
        return plan;
    }

    private void executeComplexTestInTimeZone(int hourOfDay, DateTimeZone timeZone) throws Exception {
        DateTime startsOn = NOW.withZone(timeZone).withHourOfDay(hourOfDay);
        String json = TestUtils.createJson("{"+  
                "'guid':'5fe9029e-beb6-4163-ac35-23d048deeefe',"+
                "'label':'Voice Activity',"+
                "'version':4,"+
                "'modifiedOn':'2016-03-04T20:21:10.487Z',"+
                "'strategy':{  "+
                    "'type':'CriteriaScheduleStrategy',"+
                    "'scheduleCriteria':[  "+
                        "{  "+
                            "'schedule':{"+  
                                "'scheduleType':'recurring',"+
                                "'eventId':'enrollment',"+
                                "'activities':[  "+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'33669208-1d07-4b89-8ec5-1eb5aad6dd75',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "},"+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'822f7666-ce7b-4854-98ec-8a6fffa708d9',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "},"+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'644dfee6-eb88-49b4-9472-a8ef79d9865f',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "}"+
                                "],"+
                                "'persistent':false,"+
                                "'interval':'P1D',"+
                                "'expires':'PT24H',"+
                                "'times':[  "+
                                    "'00:00:00.000'"+
                                "],"+
                                "'type':'Schedule'"+
                            "},"+
                            "'criteria':{  "+
                                "'allOfGroups':['parkinson'],"+
                                "'noneOfGroups':[],"+
                                "'type':'Criteria'"+
                            "},"+
                            "'type':'ScheduleCriteria'"+
                        "},"+
                        "{  "+
                            "'schedule':{"+  
                                "'scheduleType':'recurring',"+
                                "'eventId':'enrollment',"+
                                "'activities':[  "+
                                    "{  "+
                                        "'label':'Voice Activity',"+
                                        "'labelDetail':'20 Seconds',"+
                                        "'guid':'7e9514ba-b32d-4124-8977-38cb227ad285',"+
                                        "'task':{  "+
                                            "'identifier':'3-APHPhonation-C614A231-A7B7-4173-BDC8-098309354292',"+
                                            "'type':'TaskReference'"+
                                        "},"+
                                        "'activityType':'task',"+
                                        "'type':'Activity'"+
                                    "}"+
                                "],"+
                                "'persistent':false,"+
                                "'interval':'P1D',"+
                                "'expires':'PT24H',"+
                                "'times':[  "+
                                    "'00:00:00.000'"+
                                "],"+
                                "'type':'Schedule'"+
                            "},"+
                            "'criteria':{"+  
                                "'allOfGroups':[],"+
                                "'noneOfGroups':[],"+
                                "'type':'Criteria'"+
                            "},"+
                            "'type':'ScheduleCriteria'"+
                        "}"+
                    "]"+
                "},"+
                "'minAppVersion':36,"+
                "'type':'SchedulePlan'"+
            "}");
            
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", startsOn.withZone(DateTimeZone.UTC).minusDays(3));
        when(activityEventService.getActivityEventMap("AAA")).thenReturn(events);
        
        SchedulePlan voiceActivityPlan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        List<SchedulePlan> schedulePlans = Lists.newArrayList(voiceActivityPlan);
        when(schedulePlanService.getSchedulePlans(new StudyIdentifierImpl("test-study"), false)).thenReturn(schedulePlans);
        
        ClientInfo info = ClientInfo.fromUserAgentCache("Parkinson-QA/36 (iPhone 5S; iPhone OS/9.2.1) BridgeSDK/7");
        ScheduleContext context = new ScheduleContext.Builder()
            .withClientInfo(info)
            .withStudyIdentifier("test-study")
            .withUserDataGroups(Sets.newHashSet("parkinson","test_user"))
                .withEndsOn(startsOn.plusDays(1).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59))
                .withInitialTimeZone(timeZone)
            .withHealthCode("AAA")
            .withUserId(USER_ID)
            .withStartsOn(startsOn)
            .withAccountCreatedOn(startsOn.minusDays(4))
            .build();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerClientInfo(info)
                .withCallerStudyId(new StudyIdentifierImpl("test-study"))
                .withCallerDataGroups(ImmutableSet.of("parkinson","test_user"))
                .withCallerHealthCode("AAA")
                .withCallerUserId(USER_ID)
                .build());
        
        // Is a parkinson patient, gets 3 tasks (or 6 tasks late in the day, see BRIDGE-1603
        List<ScheduledActivity> schActivities = service.getScheduledActivities(study, context);

        // See BRIDGE-1603
        assertEquals(schActivities.size(), 6);
        
        // Not a parkinson patient, get 1 task
        context = new ScheduleContext.Builder()
                .withContext(context)
                .withUserDataGroups(Sets.newHashSet("test_user")).build();
        
        BridgeUtils.setRequestContext(BridgeUtils.getRequestContext().toBuilder()
                .withCallerDataGroups(ImmutableSet.of("test_user")).build());
        
        schActivities = service.getScheduledActivities(study, context);
        // See BRIDGE-1603
        assertEquals(schActivities.size(), 2);
    }
    
    @Test
    public void surveysAreCached() {
        DynamoSurvey survey = new DynamoSurvey();
        survey.setIdentifier("surveyId");
        survey.setGuid("guid");
        doReturn(survey).when(surveyService).getSurveyMostRecentlyPublishedVersion(any(), any(), eq(false));
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withInitialTimeZone(DateTimeZone.UTC)
                .withUserId("userId")
                .withAccountCreatedOn(NOW.minusDays(3))
                .withHealthCode("healthCode")
                .withEndsOn(NOW.plusDays(3))
                .withStudyIdentifier("studyId").build();
        
        Activity activity = new Activity.Builder().withLabel("Label").withSurvey("surveyId", "guid", null).build();
        
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval(Period.parse("P1D"));
        schedule.setActivities(Lists.newArrayList(activity));
        
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        DynamoSchedulePlan plan1 = new DynamoSchedulePlan();
        plan1.setStrategy(strategy);
        
        DynamoSchedulePlan plan2 = new DynamoSchedulePlan();
        plan2.setStrategy(strategy);
        
        doReturn(Lists.newArrayList(plan1, plan2)).when(schedulePlanService).getSchedulePlans(any(), eq(false));
        
        List<ScheduledActivity> schActivities = service.getScheduledActivities(study, context);
        
        assertTrue(schActivities.size() > 1);
        for (ScheduledActivity act : schActivities) {
            assertEquals(act.getActivity().getSurvey().getGuid(), "guid");
        }
        
        verify(surveyService, times(1)).getSurveyMostRecentlyPublishedVersion(any(), any(), eq(false));
    }
    
    // These cases suggested by Dwayne, there all good to verify further we don't have a date change
    
    // Scheduler is interpreting the event to the correct date. Examples:
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=-08:00, endsOnTimeZone=-08:00, schedule=daily at 11pm, expected=2017-02-19T23:00-0800
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=+09:00, endsOnTimeZone=+09:00, schedule=daily at 11pm, expected=2017-02-20T23:00+0900
    @Test
    public void schedulerInterpretsEventToCorrectDate() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setInterval("P1D");
        schedule.addTimes("23:00");
        
        String timestamp = firstTimeStampFor(-8, -8, schedule);
        assertEquals(timestamp, "2017-02-19T23:00:00.000-08:00");
        
        timestamp = firstTimeStampFor(9, 9, schedule);
        assertEquals(timestamp, "2017-02-20T23:00:00.000+09:00");
    }
    
    // Scheduler is interpreting the event to the right local time. Examples:
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=-08:00, endsOnTimeZone=-08:00, schedule=one hour after enrollment, expected=2017-02-19T18:00-0800
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=+09:00, endsOnTimeZone=+09:00, schedule=one hour after enrollment, expected=2017-02-20T11:00+0900
    @Test
    public void schedulerInterpetsEventToRightLocalTime() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay(Period.parse("PT1H"));
        
        String timestamp = firstTimeStampFor(-8, -8, schedule);
        assertEquals(timestamp, "2017-02-19T18:00:00.000-08:00");
        
        timestamp = firstTimeStampFor(9, 9, schedule);
        assertEquals(timestamp, "2017-02-20T11:00:00.000+09:00");
    }

    // Local times having timezone correctly applied. Examples:
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=-08:00, endsOnTimeZone=+09:00, schedule=one hour after enrollment, expected=2017-02-19T18:00+0900 (Should this be the 19th or the 20th?)
    // Enrollment=1487552400000 (2017-02-20T01:00Z), timeZone=+09:00, endsOnTimeZone=-08:00, schedule=one hour after enrollment, expected=2017-02-20T11:00-0800
    @Test
    public void localTimeZonesCorrectlyApplied() {
        Schedule schedule = new Schedule();
        schedule.getActivities().add(TestUtils.getActivity1());
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setDelay(Period.parse("PT1H"));
        
        String timestamp = firstTimeStampFor(-8, +9, schedule);
        assertEquals(timestamp, "2017-02-19T18:00:00.000+09:00");
        
        timestamp = firstTimeStampFor(9, -8, schedule);
        assertEquals(timestamp, "2017-02-20T11:00:00.000-08:00");
    }
    
    @Test
    public void detectClientDataAdded() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setClientData(TestUtils.getClientData());
        
        assertTrue(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataRemoved() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        dbActivity.setClientData(TestUtils.getClientData());
        
        ScheduledActivity activity = ScheduledActivity.create();
        
        assertTrue(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataChanged() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        dbActivity.setClientData(TestUtils.getClientData());
        
        ScheduledActivity activity = ScheduledActivity.create();
        JsonNode changedClientData = TestUtils.getClientData();
        ((ObjectNode)changedClientData).put("type", "ChangedNode");
        activity.setClientData(changedClientData);
        
        assertTrue(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataRemainsNull() {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        
        ScheduledActivity activity = ScheduledActivity.create();
        
        assertFalse(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void detectClientDataRemainsSame() throws Exception {
        ScheduledActivity dbActivity = ScheduledActivity.create();
        dbActivity.setClientData(TestUtils.getClientData());
        
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setClientData(TestUtils.getClientData());
        
        assertFalse(service.hasUpdatedClientData(activity, dbActivity));
    }
    
    @Test
    public void getActivitiesV4ReturnsAllScheduledActivities() throws Exception {
        DateTime endsOn = DateTime.now().plusDays(3);
        ScheduleContext context = createScheduleContext(endsOn).build();
        
        mockAllCallsForDbActivities(Lists.newArrayList());
        
        List<ScheduledActivity> activities = service.getScheduledActivitiesV4(study, context);
        assertTrue(activities.size() > 0);
        
        verify(activityDao, times(1)).getActivityHistoryV2(HEALTH_CODE, "AAA", context.getStartsOn(), context.getEndsOn(),
                null, BridgeConstants.API_MAXIMUM_PAGE_SIZE);
        
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> activitiesOnSave = scheduledActivityListCaptor.getValue();
        assertEquals(toGuids(activitiesOnSave), toGuids(activities));
    }
    
    /** 
     * The v4 API is used to store client data, and must return finished activities. Instead of retrieving 
     * activities as found by the scheduler (v3 API), we retrieve all activities in the time range, then 
     * merge them into the scheduled tasks (also replacing any newly scheduled tasks that have already been 
     * persisted).
     */
    @Test
    public void getActivitiesV4ReturnsPersistedButNotScheduledActivities() throws Exception {
        // Only one schedule plan, with a persistent task.
        List<SchedulePlan> schedulePlans = Lists.newArrayList();
        String json2 = TestUtils.createJson("{'guid':'05edf4bd-3f31-40ef-b8e8-edca753268e3',"+
                "'label':'Do this all the time','version':1,'modifiedOn':'2017-07-12T19:42:17.460Z',"+
                "'strategy':{'type':'SimpleScheduleStrategy','schedule':{'scheduleType':'persistent',"+
                "'activities':[{'label':'Do this all the time','labelDetail':'2 min',"+
                "'guid':'0c48dbe7-4091-4024-b199-e81a8f7327ed','task':{'identifier':'Brain Baseline',"+
                "'type':'TaskReference'},'activityType':'task','type':'Activity'}],'persistent':true,"+
                "'times':[],'type':'Schedule'}},'type':'SchedulePlan'}");
        SchedulePlan plan2 = BridgeObjectMapper.get().readValue(json2, SchedulePlan.class);
        schedulePlans.add(plan2);
        reset(schedulePlanService);
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false))
            .thenReturn(schedulePlans);        
        
        ScheduleContext context = createScheduleContext(ENDS_ON).build();
        List<ScheduledActivity> activities = service.getScheduledActivitiesV4(study, context);

        assertEquals(activities.get(0).getStatus(), ScheduledActivityStatus.AVAILABLE);
        
        List<ScheduledActivity> dbActivities = Lists.newArrayList();
        
        // finish this task.
        ScheduledActivity firstFinishedActivity = activities.get(0);
        firstFinishedActivity.setStartedOn(NOW.minusMinutes(4).getMillis());
        firstFinishedActivity.setFinishedOn(NOW.minusMinutes(3).getMillis());
        dbActivities.add(firstFinishedActivity);
        mockAllCallsForDbActivities(dbActivities);
        
        // Second call should return two tasks
        context = createContextWithFinishedEvent(firstFinishedActivity);
        activities = service.getScheduledActivitiesV4(study, context);
        
        assertEquals(activities.size(), 2);
        assertEquals(activities.get(0), firstFinishedActivity);
        assertEquals(activities.get(1).getStatus(), ScheduledActivityStatus.AVAILABLE);

        // finish this second task.
        ScheduledActivity secondFinishedActivity = activities.get(1);
        secondFinishedActivity.setStartedOn(NOW.minusMinutes(2).getMillis());
        secondFinishedActivity.setFinishedOn(NOW.minusMinutes(1).getMillis());
        dbActivities.add(secondFinishedActivity);
        mockAllCallsForDbActivities(dbActivities);

        // Third call should return three tasks
        context = createContextWithFinishedEvent(secondFinishedActivity);
        activities = service.getScheduledActivitiesV4(study, context);

        assertEquals(activities.size(), 3);
        assertEquals(activities.get(0), firstFinishedActivity);
        assertEquals(activities.get(1), secondFinishedActivity);
        assertEquals(activities.get(2).getStatus(), ScheduledActivityStatus.AVAILABLE);
    }

    private ScheduleContext createContextWithFinishedEvent(ScheduledActivity finishedActivity) {
        Map<String,DateTime> eventsMap = new ImmutableMap.Builder<String,DateTime>()
                .put("enrollment", ENROLLMENT.withZone(DateTimeZone.UTC))
                .put("activity:0c48dbe7-4091-4024-b199-e81a8f7327ed:finished", 
                        new DateTime(finishedActivity.getFinishedOn(), TIME_ZONE))
                .build();
        when(activityEventService.getActivityEventMap(HEALTH_CODE)).thenReturn(eventsMap);
        return createScheduleContext(ENDS_ON).withEvents(eventsMap).build();
    }
    
    @Test
    public void getActivitiesV4ReturnsExpiredAndFinishedActivities() throws Exception {
        DateTime startsOn = DateTime.now().minusDays(2);
        DateTime endsOn = DateTime.now().plusDays(2);
        
        ScheduleContext context = createScheduleContext(endsOn).withStartsOn(startsOn).build();
        
        // Get the activities that will be scheduled. We're going to change their state and return
        // them from the getActivities() call. This should not prevent any of the newly scheduled
        // activities from being returned, with the state we've created
        List<ScheduledActivity> dbActivities = service.scheduleActivitiesForPlans(context);
        for (ScheduledActivity activity : dbActivities) {
            activity.setStartedOn(DateUtils.getCurrentMillisFromEpoch());
            activity.setFinishedOn(DateUtils.getCurrentMillisFromEpoch());
        }
        mockAllCallsForDbActivities(dbActivities);
        
        List<ScheduledActivity> activities = service.getScheduledActivitiesV4(study, context);
        
        // None of the activities are saved
        verify(activityDao).saveActivities(scheduledActivityListCaptor.capture());
        List<ScheduledActivity> activitiesOnSave = scheduledActivityListCaptor.getValue();
        assertTrue(activitiesOnSave.isEmpty());
        
        // The returned list is the same as the db list we created earlier
        assertEquals(toGuids(activities), toGuids(dbActivities));
    }
    
    // BRIDGE-1964. This test reproduces the stack trace in production. 
    @Test
    public void nullsRemovedAndLogged() {
        ScheduledActivityDao mockedActivityDao = mock(ScheduledActivityDao.class);
        service.setScheduledActivityDao(mockedActivityDao);
        
        DateTime startsOn = NOW.minusDays(2);
        DateTime endsOn = NOW.plusDays(2);
        ScheduleContext context = createScheduleContext(endsOn).withStartsOn(startsOn).build();
        
        ScheduledActivity dbActivity = new DynamoScheduledActivity();
        dbActivity.setHealthCode(HEALTH_CODE);
        dbActivity.setGuid("AAA:2017-02-23T13:00:00.000");
        dbActivity.setActivity(TestUtils.getActivity1());
        dbActivity.setStartedOn(NOW.getMillis()); 
        dbActivity.setSchedulePlanGuid("schedulePlanGuid");
        dbActivity.setLocalScheduledOn(LocalDateTime.parse("2017-02-23T13:00:00.000"));
        // This is the critical line. Without this, the service fails. Also tested in DAO code.
        dbActivity.setTimeZone(context.getStartsOn().getZone());
        when(mockedActivityDao.getActivity(context.getStartsOn().getZone(), HEALTH_CODE, "AAA:2017-02-23T13:00:00.000", false)).thenReturn(dbActivity);
        
        service.getScheduledActivitiesV4(study, context);
        verify(mockedActivityDao).getActivity(context.getStartsOn().getZone(), HEALTH_CODE, "AAA:2017-02-23T13:00:00.000", false);
    }
    
    private void mockAllCallsForDbActivities(List<ScheduledActivity> dbActivities) {
        reset(activityDao);
        Map<String,List<ScheduledActivity>> map = Maps.newHashMap();
        for (ScheduledActivity oneActivity : dbActivities) {
            String guid = oneActivity.getGuid().split(":")[0];
            if (!map.containsKey(guid)) {
                map.put(guid, Lists.newArrayList());
            }
            map.get(guid).add(oneActivity);
        }
        for (String guid : map.keySet()) {
            mockGetActivitiesV2(guid, map.get(guid));
        }
    }
    
    private void mockGetActivitiesV2(String activityGuid, List<ScheduledActivity> activities) {
        ForwardCursorPagedResourceList<ScheduledActivity> list = new ForwardCursorPagedResourceList<>(activities, null);

        when(activityDao.getActivityHistoryV2(eq(HEALTH_CODE), eq(activityGuid), any(), any(), 
                eq(null), eq(BridgeConstants.API_MAXIMUM_PAGE_SIZE))).thenReturn(list);
    }
    
    private String firstTimeStampFor(int initialTZOffset, int requestTZOffset, Schedule schedule) {
        // Tests calling this method set up different mocked environment from other tests.
        reset(schedulePlanService);
        reset(activityEventService);
        
        DateTime enrollment = DateTime.parse("2017-02-20T01:00:00.000Z");
        DateTimeZone initialTimeZone = DateTimeZone.forOffsetHours(initialTZOffset);
        DateTimeZone requestTimeZone = DateTimeZone.forOffsetHours(requestTZOffset);
        DateTime startsOn = DateTime.parse("2017-04-06T17:10:10.000Z").withZone(requestTimeZone);
        
        Map<String,DateTime> eventMap = Maps.newHashMap();
        eventMap.put("enrollment", enrollment);
        when(activityEventService.getActivityEventMap("healthCode")).thenReturn(eventMap);

        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid("BBB");
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        when(schedulePlanService.getSchedulePlans(TEST_STUDY, false)).thenReturn(Lists.newArrayList(plan));
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withStudyIdentifier(TEST_STUDY)
                .withUserId("userId")
                .withStartsOn(startsOn)
                .withAccountCreatedOn(enrollment)
                .withInitialTimeZone(initialTimeZone)
                .withEndsOn(startsOn.plusDays(4).withZone(requestTimeZone))
                .withHealthCode("healthCode").build();
        
        List<ScheduledActivity> activities = service.getScheduledActivities(study, context);
        
        verify(activityEventService).getActivityEventMap("healthCode");
        verify(schedulePlanService).getSchedulePlans(TEST_STUDY, false);
        
        return activities.get(0).getScheduledOn().toString();
    }

    private List<ScheduledActivity> createNewActivities(String... guids) {
        return createActivities(false, false, false, guids);
    }

    private List<ScheduledActivity> createStartedActivities(String... guids) {
        return createActivities(true, false, false, guids);
    }

    private List<ScheduledActivity> createFinishedActivities(String... guids) {
        return createActivities(true, false, true, guids);
    }

    private List<ScheduledActivity> createExpiredActivities(String... guids) {
        return createActivities(false, true, false, guids);
    }
    
    private List<ScheduledActivity> createActivities(boolean isStarted, boolean isExpired, boolean isFinished, String... guids) {
        DateTime startedOn = NOW.minusMonths(6);
        DateTime expiresOn = NOW.minusMonths(5);
        List<ScheduledActivity> list = Lists.newArrayListWithCapacity(guids.length);
        for (String guid : guids) {
            ScheduledActivity activity = ScheduledActivity.create();
            activity.setGuid(guid);
            activity.setTimeZone(DateTimeZone.UTC);
            activity.setLocalScheduledOn(NOW.toLocalDateTime());
            activity.setHealthCode(HEALTH_CODE);

            if (isStarted) {
                activity.setStartedOn(startedOn.getMillis());
            }
            if (isExpired) {
                activity.setLocalExpiresOn(expiresOn.toLocalDateTime());
            }
            if (isFinished) {
                activity.setFinishedOn(startedOn.getMillis());
            }
            list.add(activity);
        }
        return list;
    }

    private Set<String> toGuids(List<ScheduledActivity> activities) {
        return activities.stream().map(ScheduledActivity::getGuid).collect(toSet());
    }
    
    private ScheduleContext.Builder createScheduleContext(DateTime endsOn) {
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        return new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).withInitialTimeZone(DateTimeZone.UTC)
                .withStartsOn(NOW).withAccountCreatedOn(ENROLLMENT.minusHours(2)).withEndsOn(endsOn)
                .withHealthCode(HEALTH_CODE).withUserId(USER_ID).withEvents(events);
    }
    
}
