package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.ACTIVITY_1;
import static org.sagebionetworks.bridge.TestConstants.ACTIVITY_3;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockEditAccount;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.config.Environment.UAT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.ScheduledActivityService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.time.DateUtils;

public class ScheduledActivityControllerTest extends Mockito {

    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> ACTIVITY_TYPE_REF = new TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>>() {
    };

    private static final ObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final String ACTIVITY_GUID = "activityGuid";

    private static final DateTime ENDS_ON = DateTime.now();
    
    private static final DateTime STARTS_ON = ENDS_ON.minusWeeks(1);
    
    private static final String OFFSET_BY = "2000";
    
    private static final String PAGE_SIZE = "77";

    private static final DateTime ACCOUNT_CREATED_ON = DateTime.now();
    
    private static final String ID = "id";
    
    private static final String USER_AGENT = "App Name/4 SDK/2";
    
    private static final ClientInfo CLIENT_INFO = ClientInfo.fromUserAgentCache(USER_AGENT);
    
    private static final Study STUDY = Study.create();  
    
    private static final TypeReference<ForwardCursorPagedResourceList<ScheduledActivity>> FORWARD_CURSOR_PAGED_ACTIVITIES_REF =
            ACTIVITY_TYPE_REF;
    
    @InjectMocks
    @Spy
    private ScheduledActivityController controller;

    @Mock
    ScheduledActivityService mockScheduledActivityService;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    Study mockStudy;
    
    @Mock
    Account mockAccount;
    
    @Mock
    BridgeConfig mockBridgeConfig;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    SessionUpdateService sessionUpdateService;
    
    @Captor
    ArgumentCaptor<ScheduleContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @Captor
    ArgumentCaptor<DateTime> startsOnCaptor;
    
    @Captor
    ArgumentCaptor<DateTime> endsOnCaptor;
    
    @Captor
    ArgumentCaptor<DateTimeZone> timeZoneCaptor;
    
    @Captor
    ArgumentCaptor<List<ScheduledActivity>> activitiesCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setLocalScheduledOn(LocalDateTime.now().minusDays(1));
        schActivity.setActivity(ACTIVITY_3);
        schActivity.setReferentGuid("referentGuid");
        List<ScheduledActivity> list = ImmutableList.of(schActivity);
        
        mockRequestBody(mockRequest, list);
        when(mockRequest.getHeader(HttpHeaders.USER_AGENT)).thenReturn(USER_AGENT);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE)
                .withDataGroups(USER_DATA_GROUPS)
                .withSubstudyIds(USER_SUBSTUDY_IDS)
                .withLanguages(LANGUAGES)
                .withCreatedOn(ACCOUNT_CREATED_ON)
                .withId(ID).build();
        session = new UserSession(participant);
        session.setAppId(TEST_APP_ID);
        
        when(mockScheduledActivityService.getScheduledActivities(eq(STUDY), any(ScheduleContext.class))).thenReturn(list);

        STUDY.setIdentifier(TEST_APP_ID);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(STUDY);
        when(mockBridgeConfig.getEnvironment()).thenReturn(UAT);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerClientInfo(CLIENT_INFO).build());
    }
    
    @AfterMethod
    public void afterMethod( ) {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void verifyAnnotations() throws Exception { 
        assertCrossOrigin(ScheduledActivityController.class);
        assertGet(ScheduledActivityController.class, "getTasks");
        assertGet(ScheduledActivityController.class, "getScheduledActivities");
        assertGet(ScheduledActivityController.class, "getActivityHistory");
        assertGet(ScheduledActivityController.class, "getActivityHistoryV3WithActivityType");
        assertGet(ScheduledActivityController.class, "getActivityHistoryV3");
        assertGet(ScheduledActivityController.class, "getScheduledActivitiesByDateRange");
        assertPost(ScheduledActivityController.class, "updateScheduledActivities");
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void timeZoneCapturedFirstTime() throws Exception {
        mockEditAccount(mockAccountService, mockAccount);
        
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(mockAccount).setTimeZone(MSK);
        verify(sessionUpdateService).updateTimeZone(session, MSK);
        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        assertEquals(contextCaptor.getValue().getInitialTimeZone(), MSK);
        assertEquals(contextCaptor.getValue().getStartsOn().getZone(), MSK);
        assertEquals(contextCaptor.getValue().getEndsOn().getZone(), MSK);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testZoneUsedFromPersistenceWhenAvailable() throws Exception {
        DateTimeZone UNK = DateTimeZone.forOffsetHours(4);
        StudyParticipant updatedParticipant = new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withTimeZone(UNK).build();
        session.setParticipant(updatedParticipant);
        
        controller.getScheduledActivities(null, "-07:00", "3", "5");
        
        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(context.getInitialTimeZone(), UNK);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void utcTimeZoneParsedCorrectly() throws Exception {
        controller.getScheduledActivities(null, "+0:00", "3", "5");
        
        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(DateUtils.timeZoneToOffsetString(context.getInitialTimeZone()), "+00:00");
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivtiesAssemblesCorrectContext() throws Exception {
        mockEditAccount(mockAccountService, mockAccount);
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        List<ScheduledActivity> list = ImmutableList.of();
        when(mockScheduledActivityService.getScheduledActivities(eq(STUDY), any(ScheduleContext.class))).thenReturn(list);
        
        controller.getScheduledActivities(null, "+03:00", "3", "5");
        
        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(context.getInitialTimeZone(), MSK);
        assertEquals(context.getCriteriaContext().getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(context.getMinimumPerSchedule(), 5);
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals(critContext.getHealthCode(), HEALTH_CODE);
        assertEquals(critContext.getLanguages(), LANGUAGES);
        assertEquals(critContext.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(critContext.getAppId(), TEST_APP_ID);
        assertEquals(critContext.getClientInfo(), CLIENT_INFO);
        
        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals(requestInfo.getUserId(), "id");
        assertEquals(requestInfo.getLanguages(), LANGUAGES);
        assertEquals(requestInfo.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(requestInfo.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(requestInfo.getAppId(), TEST_APP_ID);
        assertEquals(requestInfo.getUserAgent(), USER_AGENT);
        assertEquals(requestInfo.getClientInfo(), CLIENT_INFO);
        assertNotNull(requestInfo.getActivitiesAccessedOn());
        assertEquals(requestInfo.getActivitiesAccessedOn().getZone(), MSK);
        verify(sessionUpdateService).updateTimeZone(session, MSK);
        // Verify that the account mock was updated with the anticipated time zone (however 
        // it's a mock as is sessionUpdateService, so this doesn't change RequestInfo, as it's
        // based on changes to the session.
        verify(mockAccount).setTimeZone(MSK);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesAsScheduledActivitiesReturnsCorrectType() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        String result = controller.getScheduledActivities(now.toString(), null, null, null);

        JsonNode results = BridgeObjectMapper.get().readTree(result);
        ArrayNode items = (ArrayNode)results.get("items");
        for (int i=0; i < items.size(); i++) {
            assertEquals("ScheduledActivity", items.get(i).get("type").asText());
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitesAsTasks() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        JsonNode result = controller.getTasks(now.toString(), null, null);
        
        // Verify that even without the writer, we are not leaking these values
        // through the API, and they are typed as "Task"s.
        JsonNode items = result.get("items");
        assertTrue(items.size() > 0);
        for (int i=0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            assertNotNull(item.get("guid"));
            assertNull(item.get("healthCode"));
            assertNull(item.get("schedulePlanGuid"));
            assertEquals("Task", item.get("type").asText());
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesWithUntil() throws Exception {
        // Until value is simply passed along as is to the scheduler.
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        controller.getScheduledActivities(now.toString(), null, null, null);

        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        verifyNoMoreInteractions(mockScheduledActivityService);
        assertEquals(contextCaptor.getValue().getEndsOn(), now);
        assertEquals(contextCaptor.getValue().getInitialTimeZone(), now.getZone());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesWithDaysAheadTimeZoneAndMinimum() throws Exception {
        // We expect the endsOn value to be three days from now at the end of the day 
        // (set millis to 0 so the values match at the end of the test).
        DateTime expectedEndsOn = DateTime.now().withZone(DateTimeZone.forOffsetHours(3)).plusDays(3).withHourOfDay(23)
                .withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(0);
        
        controller.getScheduledActivities(null, "+03:00", "3", null);
        
        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        verifyNoMoreInteractions(mockScheduledActivityService);
        assertEquals(contextCaptor.getValue().getEndsOn().withMillisOfSecond(0), expectedEndsOn);
        assertEquals(contextCaptor.getValue().getInitialTimeZone(), expectedEndsOn.getZone());
        assertEquals(contextCaptor.getValue().getMinimumPerSchedule(), 0);
        assertEquals(contextCaptor.getValue().getCriteriaContext().getClientInfo(), CLIENT_INFO);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void updateScheduledActivities() throws Exception {
        StatusMessage result = controller.updateScheduledActivities();
        assertEquals(result, ScheduledActivityController.UPDATED_MSG);

        verify(mockScheduledActivityService).updateScheduledActivities(anyString(), any(List.class));
        verifyNoMoreInteractions(mockScheduledActivityService);
    }

    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields:.*")
    public void updateScheduledActivitiesBadJson() throws Exception {
        mockRequestBody(mockRequest, "[\"+1234567890\"]");
        
        controller.updateScheduledActivities();
    }
    
    @SuppressWarnings("deprecation")
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        reset(controller);
        doReturn(mockRequest).when(controller).request();
        controller.getScheduledActivities(DateTime.now().toString(), null, null, null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void fullyInitializedSessionProvidesAccountCreatedOnInScheduleContext() throws Exception {
        controller.getScheduledActivities(null, "-07:00", "3", null);
        
        verify(mockScheduledActivityService).getScheduledActivities(eq(STUDY), contextCaptor.capture());
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(context.getAccountCreatedOn(), ACCOUNT_CREATED_ON.withZone(DateTimeZone.UTC));
    }

    @Test
    public void activityHistoryWithDefaults() throws Exception {
        doReturn(createActivityResultsV2(77, null)).when(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), eq(null), eq(null), eq(null), eq(API_DEFAULT_PAGE_SIZE));
        
        JsonNode result = controller.getActivityHistory(ACTIVITY_GUID, null, null, null, null, null);

        verify(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID), eq(null),
                eq(null), eq(null), eq(API_DEFAULT_PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ScheduledActivity> list = MAPPER.readValue(MAPPER.treeAsTokens(result),
                ACTIVITY_TYPE_REF);
        assertNull(list.getItems().get(0).getHealthCode());
    }

    @Test
    public void getActivityHistoryV3() throws Exception {
        doReturn(createActivityResultsV2(20, "offsetKey")).when(mockScheduledActivityService).getActivityHistory(
                eq(HEALTH_CODE), eq(ActivityType.TASK), eq("referentGuid"), any(), any(), eq("offsetKey"), eq(20));
        
        String result = controller.getActivityHistoryV3WithActivityType("tasks", "referentGuid", STARTS_ON.toString(),
                ENDS_ON.toString(), "offsetKey", "20");
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = BridgeObjectMapper.get()
                .readValue(result, FORWARD_CURSOR_PAGED_ACTIVITIES_REF);
        assertEquals((int)page.getRequestParams().get("pageSize"), 20);
        assertEquals((String)page.getRequestParams().get("offsetKey"), "offsetKey");
        
        verify(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ActivityType.TASK), eq("referentGuid"),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("offsetKey"), eq(20));
        assertEquals(startsOnCaptor.getValue().toString(), STARTS_ON.toString());
        assertEquals(endsOnCaptor.getValue().toString(), ENDS_ON.toString());
    }

    @Test
    public void getActivityHistoryV3SetsNullDefaults() throws Exception {
        controller.getActivityHistoryV3WithActivityType("wrongtypes", null, null, null, null, null);

        verify(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(null), eq(null),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq(null), eq(API_DEFAULT_PAGE_SIZE));
        assertNull(startsOnCaptor.getValue());
        assertNull(endsOnCaptor.getValue());
    }

    @Test
    public void activityHistoryWithAllValues() throws Exception {
        doReturn(createActivityResultsV2(77, "2000")).when(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), any(), any(), eq("2000"), eq(77));
        
        JsonNode result = controller.getActivityHistory(ACTIVITY_GUID, STARTS_ON.toString(),
                ENDS_ON.toString(), OFFSET_BY, null, PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(MAPPER.treeAsTokens(result),
                ACTIVITY_TYPE_REF);
        assertEquals(result.get("offsetBy").textValue(), OFFSET_BY);
        
        assertEquals(page.getItems().size(), 1);
        assertEquals(page.getNextPageOffsetKey(), "777");
        assertEquals(page.getRequestParams().get("pageSize"), 77);
        assertEquals(page.getRequestParams().get("offsetKey"), OFFSET_BY);

        verify(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("2000"), eq(77));
        assertTrue(STARTS_ON.isEqual(startsOnCaptor.getValue()));
        assertTrue(ENDS_ON.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void activityHistoryWithOffsetKey() throws Exception {
        doReturn(createActivityResultsV2(77, "2000")).when(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE),
                eq(ACTIVITY_GUID), any(), any(), eq("2000"), eq(77));

        JsonNode result = controller.getActivityHistory(ACTIVITY_GUID, STARTS_ON.toString(),
                ENDS_ON.toString(), null, OFFSET_BY, PAGE_SIZE);
        
        ForwardCursorPagedResourceList<ScheduledActivity> page = MAPPER.readValue(MAPPER.treeAsTokens(result),
                ACTIVITY_TYPE_REF);        
        
        assertEquals(page.getItems().size(), 1);
        assertEquals(page.getNextPageOffsetKey(), "777");
        assertEquals(page.getRequestParams().get("pageSize"), 77);
        assertEquals(page.getRequestParams().get("offsetKey"), OFFSET_BY);

        verify(mockScheduledActivityService).getActivityHistory(eq(HEALTH_CODE), eq(ACTIVITY_GUID),
                startsOnCaptor.capture(), endsOnCaptor.capture(), eq("2000"), eq(77));
        assertTrue(STARTS_ON.isEqual(startsOnCaptor.getValue()));
        assertTrue(ENDS_ON.isEqual(endsOnCaptor.getValue()));
    }

    @Test
    public void updateScheduledActivitiesWithClientData() throws Exception {
        JsonNode clientData = TestUtils.getClientData();
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setLocalScheduledOn(LocalDateTime.now().minusDays(1));
        schActivity.setActivity(ACTIVITY_3);
        schActivity.setClientData(clientData);
        
        TestUtils.mockRequestBody(mockRequest, ImmutableList.of(schActivity));
        
        StatusMessage result = controller.updateScheduledActivities();
        assertEquals(result, ScheduledActivityController.UPDATED_MSG);
        
        verify(mockScheduledActivityService).updateScheduledActivities(eq(HEALTH_CODE), activitiesCaptor.capture());
        
        List<ScheduledActivity> capturedActivities = activitiesCaptor.getValue();
        assertEquals(capturedActivities.get(0).getClientData(), clientData);
    }

    @Test
    public void getScheduledActivitiesV4() throws Exception {
        DateTimeZone zone = DateTimeZone.forOffsetHours(4);
        DateTime startsOn = DateTime.now(zone).minusMinutes(1);
        DateTime endsOn = DateTime.now(zone).plusDays(7);

        mockEditAccount(mockAccountService, mockAccount);
        
        String result = controller.getScheduledActivitiesByDateRange(startsOn.toString(), endsOn.toString());
        
        JsonNode node = BridgeObjectMapper.get().readTree(result);
        assertEquals(node.get("startTime").textValue(), startsOn.toString());
        assertEquals(node.get("endTime").textValue(), endsOn.toString());
        
        verify(sessionUpdateService).updateTimeZone(any(UserSession.class), timeZoneCaptor.capture());
        verify(mockRequestInfoService).updateRequestInfo(requestInfoCaptor.capture());
        verify(mockScheduledActivityService).getScheduledActivitiesV4(eq(STUDY), contextCaptor.capture());
        verify(controller).persistTimeZone(session, zone);
        verify(mockAccount).setTimeZone(zone);
        
        assertEquals(startsOn.getZone(), timeZoneCaptor.getValue());
        
        RequestInfo requestInfo = requestInfoCaptor.getValue();
        assertEquals(requestInfo.getUserId(), "id");
        assertEquals(requestInfo.getClientInfo(), CLIENT_INFO);
        assertEquals(requestInfo.getUserAgent(), USER_AGENT);
        assertEquals(requestInfo.getLanguages(), LANGUAGES);
        assertEquals(requestInfo.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(requestInfo.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertTrue(requestInfo.getActivitiesAccessedOn().isAfter(startsOn));
        assertNull(requestInfo.getSignedInOn());
        assertEquals(requestInfo.getAppId(), TEST_APP_ID);
        
        ScheduleContext context = contextCaptor.getValue();
        assertEquals(context.getInitialTimeZone(), startsOn.getZone());
        // To make the range inclusive, we need to adjust timestamp to right before the start instant
        // This value is not mirrored back in the response (see test above of the response).
        assertEquals(context.getStartsOn(), startsOn.minusMillis(1));
        assertEquals(context.getEndsOn(), endsOn);
        assertEquals(0, context.getMinimumPerSchedule());
        assertEquals(context.getAccountCreatedOn(), ACCOUNT_CREATED_ON.withZone(DateTimeZone.UTC));
        
        CriteriaContext critContext = context.getCriteriaContext();
        assertEquals(critContext.getAppId(), TEST_APP_ID);
        assertEquals(critContext.getHealthCode(), HEALTH_CODE);
        assertEquals(critContext.getUserId(), ID);
        assertEquals(critContext.getClientInfo(), CLIENT_INFO);
        assertEquals(critContext.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(critContext.getUserSubstudyIds(), USER_SUBSTUDY_IDS);
        assertEquals(critContext.getLanguages(), LANGUAGES);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getScheduledActivitiesMissingStartsOn() throws Exception {
        DateTime endsOn = DateTime.now().plusDays(7);
        controller.getScheduledActivitiesByDateRange(null, endsOn.toString());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getScheduledActivitiesMissingEndsOn() throws Exception {
        DateTime startsOn = DateTime.now().plusDays(7);
        controller.getScheduledActivitiesByDateRange(startsOn.toString(), null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getScheduledActivitiesMalformattedDateTimeStampOn() throws Exception {
        controller.getScheduledActivitiesByDateRange("2010-01-01", null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getScheduledActivitiesMismatchedTimeZone() throws Exception {
        DateTime startsOn = DateTime.now(DateTimeZone.forOffsetHours(4));
        DateTime endsOn = DateTime.now(DateTimeZone.forOffsetHours(-7)).plusDays(7);
        controller.getScheduledActivitiesByDateRange(startsOn.toString(), endsOn.toString());
    }

    private ForwardCursorPagedResourceList<ScheduledActivity> createActivityResultsV2(int pageSize, String offsetKey) {
        DynamoScheduledActivity activity = new DynamoScheduledActivity();
        activity.setActivity(ACTIVITY_1);
        activity.setHealthCode("healthCode");
        activity.setSchedulePlanGuid("schedulePlanGuid");
        
        return new ForwardCursorPagedResourceList<ScheduledActivity>(ImmutableList.of(activity), "777")
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey);
    }
}
