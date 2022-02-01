package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.AccountTestFilter.BOTH;
import static org.sagebionetworks.bridge.models.AccountTestFilter.PRODUCTION;
import static org.sagebionetworks.bridge.models.AccountTestFilter.TEST;
import static org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress.IN_PROGRESS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AdherenceService;

public class AdherenceControllerTest extends Mockito {
    
    private static final String CLIENT_TIME_ZONE = "America/Chicago";
    private static final DateTime SYSTEM_NOW = MODIFIED_ON;
    private static final DateTime NOW = CREATED_ON;

    @Mock
    AdherenceService mockService;

    @Mock
    AccountService mockAccountService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;

    @Spy
    @InjectMocks
    AdherenceController controller;
    
    @Captor
    ArgumentCaptor<AdherenceRecordList> listCaptor;
    
    @Captor
    ArgumentCaptor<AdherenceRecordsSearch> searchCaptor;

    @Captor
    ArgumentCaptor<AdherenceRecord> recordCaptor;
    
    @Captor
    ArgumentCaptor<AdherenceReportSearch> searchParamsCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession(new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .build());
        session.setAppId(TEST_APP_ID);
        
        doReturn(SYSTEM_NOW).when(controller).getDateTime();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AdherenceController.class);
        assertGet(AdherenceController.class, "getEventStreamAdherenceReport");
        assertGet(AdherenceController.class, "getEventStreamAdherenceReportForSelf");
        assertPost(AdherenceController.class, "updateAdherenceRecords");
        assertPost(AdherenceController.class, "searchForAdherenceRecordsForSelf");
        assertPost(AdherenceController.class, "searchForAdherenceRecords");
        assertDelete(AdherenceController.class, "deleteAdherenceRecord");
    }

    @Test
    public void getEventStreamAdherenceReport() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
    
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setClientTimeZone(CLIENT_TIME_ZONE);
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                NOW, CLIENT_TIME_ZONE, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID,
                NOW.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReport_noTimeZone() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
    
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                NOW, null, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID,
                NOW.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReport_withTimeZone() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
    
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setClientTimeZone(CLIENT_TIME_ZONE);
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                NOW, CLIENT_TIME_ZONE, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID,
                NOW.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getEventStreamAdherenceReport_accountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.empty());
        
        controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID, CREATED_ON.toString(), "true");
    }
    
    @Test
    public void getEventStreamAdherenceReport_defaults() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
    
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setClientTimeZone(CLIENT_TIME_ZONE);
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                SYSTEM_NOW, CLIENT_TIME_ZONE, false)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID, null, null);
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf() {
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withClientTimeZone(CLIENT_TIME_ZONE).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                NOW, CLIENT_TIME_ZONE, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, CREATED_ON.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf_noTimeZone() {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                NOW, null, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, 
                NOW.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf_withTimeZone() {
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withClientTimeZone(CLIENT_TIME_ZONE).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                NOW, CLIENT_TIME_ZONE, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, 
                NOW.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf_defaults() {
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withClientTimeZone(CLIENT_TIME_ZONE).build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                SYSTEM_NOW, CLIENT_TIME_ZONE, false)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, null, null);
        assertSame(retValue, report);
    }
    
    @Test
    public void getWeeklyAdherenceReport() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);    
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setClientTimeZone(CLIENT_TIME_ZONE);
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        when(mockService.getWeeklyAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, account))
            .thenReturn(report);
        
        WeeklyAdherenceReport retValue = controller.getWeeklyAdherenceReport(TEST_STUDY_ID, TEST_USER_ID);
        assertSame(retValue, report);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getWeeklyAdherenceReport_accountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);    
        
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.empty());
        
        controller.getWeeklyAdherenceReport(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void updateAdherenceRecords() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccountId(TEST_APP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(TEST_USER_ID));
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        AdherenceRecordList list = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        mockRequestBody(mockRequest, list);
        
        StatusMessage retValue = controller.updateAdherenceRecords(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue, AdherenceController.SAVED_MSG);
        
        verify(mockService).updateAdherenceRecords(eq(TEST_APP_ID), listCaptor.capture());
        AdherenceRecordList recordsList = listCaptor.getValue();
        for (AdherenceRecord record : recordsList.getRecords()) {
            assertEquals(record.getStudyId(), TEST_STUDY_ID);
            assertEquals(record.getUserId(), TEST_USER_ID);
        }
    }    
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAdherenceRecords_accountNotFound() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccountId(TEST_APP_ID, TEST_USER_ID))
            .thenReturn(Optional.empty());
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        AdherenceRecordList list = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        mockRequestBody(mockRequest, list);
        
        controller.updateAdherenceRecords(TEST_STUDY_ID, TEST_USER_ID);
    }
    
    @Test
    public void updateAdherenceRecordsForSelf() throws Exception {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        AdherenceRecordList list = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        mockRequestBody(mockRequest, list);
        
        StatusMessage retValue = controller.updateAdherenceRecordsForSelf(TEST_STUDY_ID);
        assertEquals(retValue, AdherenceController.SAVED_MSG);
        
        verify(mockService).updateAdherenceRecords(eq(TEST_APP_ID), listCaptor.capture());
        AdherenceRecordList recordsList = listCaptor.getValue();
        for (AdherenceRecord record : recordsList.getRecords()) {
            assertEquals(record.getStudyId(), TEST_STUDY_ID);
            assertEquals(record.getUserId(), TEST_USER_ID);
        }
    }

    @Test
    public void searchForAdherenceRecordsForSelf() throws Exception { 
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        List<AdherenceRecord> list = ImmutableList.of(rec1, rec2);
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<>(list, 100);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withOffsetBy(10).withPageSize(50).build();
        mockRequestBody(mockRequest, search);
        
        when(mockService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> retValue = controller
                .searchForAdherenceRecordsForSelf(TEST_STUDY_ID);
        assertSame(retValue, page);
        
        verify(mockService).getAdherenceRecords(eq(TEST_APP_ID), searchCaptor.capture());
        AdherenceRecordsSearch captured = searchCaptor.getValue();
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getOffsetBy(), Integer.valueOf(10));
        assertEquals(captured.getPageSize(), Integer.valueOf(50));
    }

    @Test
    public void searchForAdherenceRecords() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccountId(any(), any())).thenReturn(Optional.of("some-other-id"));

        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");
        AdherenceRecord rec2 = TestUtils.getAdherenceRecord("BBB");
        List<AdherenceRecord> list = ImmutableList.of(rec1, rec2);
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<>(list, 100);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withOffsetBy(10).withPageSize(50).build();
        mockRequestBody(mockRequest, search);
        
        when(mockService.getAdherenceRecords(eq(TEST_APP_ID), any())).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> retValue = controller
                .searchForAdherenceRecords(TEST_STUDY_ID, "some-other-id");
        assertSame(retValue, page);
        
        verify(mockService).getAdherenceRecords(eq(TEST_APP_ID), searchCaptor.capture());
        AdherenceRecordsSearch captured = searchCaptor.getValue();
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getUserId(), "some-other-id");
        assertEquals(captured.getOffsetBy(), Integer.valueOf(10));
        assertEquals(captured.getPageSize(), Integer.valueOf(50));        
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void searchForAdherenceRecords_accountNotFound() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        when(mockAccountService.getAccountId(any(), any())).thenReturn(Optional.empty());

        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withOffsetBy(10).withPageSize(50).build();
        mockRequestBody(mockRequest, search);
        
        controller.searchForAdherenceRecords(TEST_STUDY_ID, "some-other-id");
    }
    
    @Test
    public void deleteAdherenceRecord() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        when(mockAccountService.getAccountId(TEST_APP_ID, TEST_USER_ID))
                .thenReturn(Optional.of(TEST_USER_ID));

        AdherenceRecord rec1 = TestUtils.getAdherenceRecord("AAA");

        StatusMessage retValue = controller.deleteAdherenceRecord(
                TEST_STUDY_ID, TEST_USER_ID,
                rec1.getInstanceGuid(),
                rec1.getEventTimestamp().toString(),
                rec1.getStartedOn().toString()
        );

        assertEquals(retValue, AdherenceController.DELETED_MSG);

        verify(mockService).deleteAdherenceRecord(recordCaptor.capture());

        AdherenceRecord captured = recordCaptor.getValue();
        assertEquals(captured.getInstanceGuid(), rec1.getInstanceGuid());
        assertEquals(captured.getStudyId(), rec1.getStudyId());
        assertEquals(captured.getUserId(), rec1.getUserId());
        assertEquals(captured.getStartedOn(), rec1.getStartedOn());
        assertEquals(captured.getEventTimestamp(), rec1.getEventTimestamp());
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void deleteWithInvalidUserIdThrowsException() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        controller.deleteAdherenceRecord(
                "fake-study-id", "fake-user-id",
                "fake-instance-guid",
                "2021-07-06T18:03:23.009Z",
                "2021-07-06T18:03:23.009Z"
        );
    }

    @Test
    public void getWeeklyAdherenceReports() throws Exception {
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(PRODUCTION);
        search.setLabelFilters(ImmutableSet.of("label1", "label2"));
        search.setProgressionFilters(ImmutableSet.of(IN_PROGRESS));
        search.setIdFilter("id");
        search.setAdherenceMax(75);
        search.setOffsetBy(50);
        search.setPageSize(100);
        
        TestUtils.mockRequestBody(mockRequest, search);

        PagedResourceList<WeeklyAdherenceReport> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search)).thenReturn(page);

        PagedResourceList<WeeklyAdherenceReport> retValue = controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
        assertSame(retValue, page);

        verify(mockService).getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
    }

    @Test
    public void getWeeklyAdherenceReports_defaults() throws Exception {
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AdherenceReportSearch search = new AdherenceReportSearch();

        TestUtils.mockRequestBody(mockRequest, search);
        
        PagedResourceList<WeeklyAdherenceReport> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search)).thenReturn(page);

        PagedResourceList<WeeklyAdherenceReport> retValue = controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
        assertSame(retValue, page);

        verify(mockService).getWeeklyAdherenceReports(eq(TEST_APP_ID), eq(TEST_STUDY_ID), searchParamsCaptor.capture());
        assertEquals(searchParamsCaptor.getValue().getOffsetBy(), Integer.valueOf(0));
        assertEquals(searchParamsCaptor.getValue().getPageSize(), Integer.valueOf(API_DEFAULT_PAGE_SIZE));
    }
    
    @Test
    public void getWeeklyAdherenceReports_forcesTestForDevelopers() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(PRODUCTION);

        TestUtils.mockRequestBody(mockRequest, search);

        PagedResourceList<WeeklyAdherenceReport> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getWeeklyAdherenceReports(eq(TEST_APP_ID), eq(TEST_STUDY_ID), any())).thenReturn(page);

        PagedResourceList<WeeklyAdherenceReport> retValue = controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
        assertSame(retValue, page);
        
        verify(mockService).getWeeklyAdherenceReports(eq(TEST_APP_ID), eq(TEST_STUDY_ID), searchParamsCaptor.capture());
        assertEquals(searchParamsCaptor.getValue().getTestFilter(), TEST);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getWeeklyAdherenceReports_authRequiredDevelopers() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);
        
        AdherenceReportSearch search = new AdherenceReportSearch();

        TestUtils.mockRequestBody(mockRequest, search);

        controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
    }

    @Test
    public void getWeeklyAdherenceReports_allowsTestOrProdForResearchers() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(BOTH);
        
        TestUtils.mockRequestBody(mockRequest, search);
        
        PagedResourceList<WeeklyAdherenceReport> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search)).thenReturn(page);

        PagedResourceList<WeeklyAdherenceReport> retValue = controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
        assertSame(retValue, page);

        verify(mockService).getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
    }

    @Test
    public void getWeeklyAdherenceReports_allowsProdForResearchers() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(PRODUCTION);
        
        TestUtils.mockRequestBody(mockRequest, search);

        PagedResourceList<WeeklyAdherenceReport> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search)).thenReturn(page);

        PagedResourceList<WeeklyAdherenceReport> retValue = controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
        assertSame(retValue, page);

        verify(mockService).getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
    }

    @Test
    public void getWeeklyAdherenceReports_allowsTestForResearchers() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        doReturn(session).when(controller)
            .getAuthenticatedSession(DEVELOPER, RESEARCHER, STUDY_DESIGNER, STUDY_COORDINATOR);

        AdherenceReportSearch search = new AdherenceReportSearch();
        search.setTestFilter(TEST);
        
        TestUtils.mockRequestBody(mockRequest, search);

        PagedResourceList<WeeklyAdherenceReport> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search)).thenReturn(page);

        PagedResourceList<WeeklyAdherenceReport> retValue = controller.getWeeklyAdherenceReports(TEST_STUDY_ID);
        assertSame(retValue, page);

        verify(mockService).getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, search);
    }
    
    @Test
    public void getWeeklyAdherenceReportForWorker() {
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);

        Account account = Account.create();
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));

        WeeklyAdherenceReport report = new WeeklyAdherenceReport();
        when(mockService.getWeeklyAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, account))
            .thenReturn(report);

        WeeklyAdherenceReport retValue = controller.getWeeklyAdherenceReportForWorker(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertSame(retValue, report);
        
        verify(mockService).getWeeklyAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, account);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getWeeklyAdherenceReportForWorker_rejectsNonWorker() {
        doThrow(new UnauthorizedException()).when(controller).getAuthenticatedSession(WORKER);
        
        controller.getWeeklyAdherenceReportForWorker(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);    
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getWeeklyAdherenceReportForWorker_accountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);

        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.empty());

        controller.getWeeklyAdherenceReportForWorker(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
   }
}