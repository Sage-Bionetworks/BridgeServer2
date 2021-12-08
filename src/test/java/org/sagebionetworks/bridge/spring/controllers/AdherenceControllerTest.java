package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
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
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.TestUtils;
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
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AdherenceService;

public class AdherenceControllerTest extends Mockito {

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
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession(new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .build());
        session.setAppId(TEST_APP_ID);
        
        doReturn(MODIFIED_ON).when(controller).getDateTime();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
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
        account.setClientTimeZone("America/Chicago");
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                CREATED_ON.withZone(DateTimeZone.forID("America/Chicago")), true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID,
                CREATED_ON.toString(), "true");
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
                CREATED_ON, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID,
                CREATED_ON.toString(), "true");
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
        account.setClientTimeZone("America/Chicago");
        when(mockAccountService.getAccount(AccountId.forId(TEST_APP_ID, TEST_USER_ID)))
            .thenReturn(Optional.of(account));
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                MODIFIED_ON.withZone(DateTimeZone.forID("America/Chicago")), false)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReport(TEST_STUDY_ID, TEST_USER_ID, null, null);
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf() {
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withClientTimeZone("America/Chicago").build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                CREATED_ON.withZone(DateTimeZone.forID("America/Chicago")), true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, CREATED_ON.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf_noTimeZone() {
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                CREATED_ON, true)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, 
                CREATED_ON.toString(), "true");
        assertSame(retValue, report);
    }
    
    @Test
    public void getEventStreamAdherenceReportForSelf_defaults() {
        session.setParticipant(new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withClientTimeZone("America/Chicago").build());
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    
        EventStreamAdherenceReport report = new EventStreamAdherenceReport();
        when(mockService.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, 
                MODIFIED_ON.withZone(DateTimeZone.forID("America/Chicago")), false)).thenReturn(report);
        
        EventStreamAdherenceReport retValue = controller.getEventStreamAdherenceReportForSelf(TEST_STUDY_ID, null, null);
        assertSame(retValue, report);
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
}