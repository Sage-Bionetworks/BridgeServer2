package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.EnrollmentService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class StudyParticipantControllerTest extends Mockito {

    @Mock
    AppService mockAppService;
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    EnrollmentService mockEnrollmentService;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Enrollment> enrollmentCaptor;
    
    @Captor
    ArgumentCaptor<NotificationMessage> messageCaptor;
    
    @InjectMocks
    @Spy
    StudyParticipantController controller;
    
    App app;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        
        // These are pretty much the same for all calls
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void getEnrollmentsForUser() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        // this mocks both the main call, and the way we check to verify the user is in 
        // the target study.
        mockAccountInStudy();
        
        PagedResourceList<EnrollmentDetail> page = controller.getEnrollmentsForUser(TEST_STUDY_ID, USER_ID);
        assertEquals(page.getItems().size(), 1);
        
        verify(mockEnrollmentService, times(2)).getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getEnrollmentsForUserWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        // this mocks both the main call, and the way we check to verify the user is in 
        // the target study.
        mockAccountNotInStudy();
        
        controller.getEnrollmentsForUser(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void searchForAccountSummaries() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockParticipantService.getPagedAccountSummaries(eq(app), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAdminOnly(true).build();
        mockRequestBody(mockRequest, search);
        
        PagedResourceList<AccountSummary> retValue = controller.searchForAccountSummaries(TEST_STUDY_ID);
        assertSame(retValue, page);
        
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());
        
        AccountSummarySearch captured = searchCaptor.getValue();
        assertEquals(captured.getEnrolledInStudyId(), TEST_STUDY_ID);
        assertTrue(captured.isAdminOnly());
    }
    
    @Test
    public void createParticipant() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockRequestBody(mockRequest, new StudyParticipant.Builder().withLastName("lastName").build());
        
        IdentifierHolder id = new IdentifierHolder("id");
        when(mockParticipantService.createParticipant(eq(app), any(), eq(true))).thenReturn(id);
        
        IdentifierHolder retValue = controller.createParticipant(TEST_STUDY_ID);
        assertSame(retValue, id);
        
        verify(mockParticipantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getLastName(), "lastName");
        
        verify(mockEnrollmentService).enroll(enrollmentCaptor.capture());
        Enrollment en = enrollmentCaptor.getValue();
        assertEquals(en.getAppId(), TEST_APP_ID);
        assertEquals(en.getStudyId(), TEST_STUDY_ID);
        assertEquals(en.getAccountId(), "id");
        assertTrue(en.isConsentRequired());
    }

    @Test
    public void getParticipantIncludeConsents() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withLastName("lastName").build();
        when(mockParticipantService.getParticipant(app, USER_ID, true)).thenReturn(participant);
        
        mockAccountInStudy();

        String retValue = controller.getParticipant(TEST_STUDY_ID, USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getLastName(), participant.getLastName());
        
        verify(mockParticipantService).getParticipant(app, USER_ID, true);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantIncludeConsentsWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withLastName("lastName").build();
        when(mockParticipantService.getParticipant(app, USER_ID, true)).thenReturn(participant);
        
        mockAccountNotInStudy();

        controller.getParticipant(TEST_STUDY_ID, USER_ID, true);
    }
    
    @Test
    public void getParticipantExcludeConsents() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        controller.getParticipant(TEST_STUDY_ID, USER_ID, false);
        
        verify(mockParticipantService).getParticipant(app, USER_ID, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantExcludeConsentsWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getParticipant(TEST_STUDY_ID, USER_ID, false);
    }
    
    @Test
    public void getParticipantIncludesHealthCodeForAdmin() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(ADMIN))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, USER_ID, true)).thenReturn(participant);
        
        mockAccountInStudy();

        String retValue = controller.getParticipant(TEST_STUDY_ID, USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getHealthCode(), "healthCode");
    }

    @Test
    public void getParticipantIncludesHealthCodeIfConfigured() throws Exception {
        app.setHealthCodeExportEnabled(true);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, USER_ID, true)).thenReturn(participant);
        
        mockAccountInStudy();

        String retValue = controller.getParticipant(TEST_STUDY_ID, USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getHealthCode(), "healthCode");
    }
    
    @Test//(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantPreventsUnauthorizedHealthCodeRequests() throws Exception {
        app.setHealthCodeExportEnabled(false);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("healthCode").build();
        when(mockParticipantService.getParticipant(app, "healthcode:"+USER_ID, true)).thenReturn(participant);
        
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "healthcode:"+USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, "healthcode:"+USER_ID))
            .thenReturn(list);

        controller.getParticipant(TEST_STUDY_ID, "healthcode:"+USER_ID, true);
    }
    
    @Test
    public void getRequestInfo() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId(TEST_APP_ID)
                .withLanguages(LANGUAGES).build();
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(requestInfo);
        
        mockAccountInStudy();

        String retValue = controller.getRequestInfo(TEST_STUDY_ID, USER_ID);
        RequestInfo deser = BridgeObjectMapper.get().readValue(retValue, RequestInfo.class);
        assertEquals(deser.getLanguages(), LANGUAGES);
        
        verify(mockRequestInfoService).getRequestInfo(USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRequestInfoWrongSTudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId(TEST_APP_ID)
                .withLanguages(LANGUAGES).build();
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(requestInfo);
        
        mockAccountNotInStudy();

        controller.getRequestInfo(TEST_STUDY_ID, USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRequestInfoWrongApp() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId("not-the-test-app")
                .withLanguages(LANGUAGES).build();
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(requestInfo);
        
        controller.getRequestInfo(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void updateParticipant() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        StudyParticipant participant = new StudyParticipant.Builder()
                .withId("wrong-id")
                .withLastName("lastName").build();
        mockRequestBody(mockRequest, participant);
        
        mockAccountInStudy();

        StatusMessage retValue = controller.updateParticipant(TEST_STUDY_ID, USER_ID);
        assertEquals(retValue.getMessage(), "Participant updated.");
        
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getId(), USER_ID);
        assertEquals(captured.getLastName(), "lastName");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateParticipantWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        StudyParticipant participant = new StudyParticipant.Builder()
                .withId("wrong-id")
                .withLastName("lastName").build();
        mockRequestBody(mockRequest, participant);
        
        mockAccountNotInStudy();

        controller.updateParticipant(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void signOutDeleteToken() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();

        StatusMessage retValue = controller.signOut(TEST_STUDY_ID, USER_ID, true);
        assertEquals(retValue.getMessage(), "User signed out.");
        
        verify(mockParticipantService).signUserOut(app, USER_ID, true);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signOutDeleteTokenWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();

        controller.signOut(TEST_STUDY_ID, USER_ID, true);
    }
    
    @Test
    public void signOutDoNotDeleteToken() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();

        StatusMessage retValue = controller.signOut(TEST_STUDY_ID, USER_ID, false);
        assertEquals(retValue.getMessage(), "User signed out.");
        
        verify(mockParticipantService).signUserOut(app, USER_ID, false);
    }
    
    @Test
    public void requestResetPassword() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        StatusMessage retValue = controller.requestResetPassword(TEST_STUDY_ID, USER_ID);
        assertEquals(retValue.getMessage(), "Request to reset password sent to user.");
        
        verify(mockParticipantService).requestResetPassword(app, USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void requestResetPasswordWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.requestResetPassword(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void resendEmailVerification() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        controller.resendEmailVerification(TEST_STUDY_ID, USER_ID);
        
        verify(mockParticipantService).resendVerification(app, ChannelType.EMAIL, USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendEmailVerificationWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountNotInStudy();
        
        controller.resendEmailVerification(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void  resendPhoneVerification() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        controller.resendPhoneVerification(TEST_STUDY_ID, USER_ID);
        
        verify(mockParticipantService).resendVerification(app, ChannelType.PHONE, USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void  resendPhoneVerificationWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountNotInStudy();
        
        controller.resendPhoneVerification(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void resendConsentAgreement() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountInStudy();
        
        StatusMessage retValue = controller.resendConsentAgreement(TEST_STUDY_ID, USER_ID, "guid");
        assertEquals(retValue.getMessage(), "Consent agreement resent to user.");
        
        verify(mockParticipantService).resendConsentAgreement(app, SubpopulationGuid.create("guid"), USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void resendConsentAgreementWrongSTudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        mockAccountNotInStudy();
        
        controller.resendConsentAgreement(TEST_STUDY_ID, USER_ID, "guid");
    }
    
    @Test
    public void getUploads() {
        List<UploadView> list = ImmutableList.of();
        ForwardCursorPagedResourceList<UploadView> page = new ForwardCursorPagedResourceList<>(list, "key");
        
        DateTime start = TestConstants.CREATED_ON;
        DateTime end = TestConstants.MODIFIED_ON;
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        when(mockParticipantService
                .getUploads(app, USER_ID, start, end, 50, "offsetKey")).thenReturn(page);

        mockAccountInStudy();
        
        ForwardCursorPagedResourceList<UploadView> retValue = controller.getUploads(TEST_STUDY_ID, USER_ID, start.toString(), end.toString(), 50, "offsetKey");
        assertSame(retValue, page);
        
        verify(mockParticipantService).getUploads(app, USER_ID, start, end, 50, "offsetKey");
    }

    @Test
    public void getUploadsDefaultParams() {
        List<UploadView> list = ImmutableList.of();
        ForwardCursorPagedResourceList<UploadView> page = new ForwardCursorPagedResourceList<>(list, "key");
        
        DateTime start = TestConstants.CREATED_ON;
        DateTime end = TestConstants.MODIFIED_ON;
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        when(mockParticipantService.getUploads(app, USER_ID, null, null, null, null)).thenReturn(page);

        mockAccountInStudy();
        
        ForwardCursorPagedResourceList<UploadView> retValue = controller.getUploads(TEST_STUDY_ID, USER_ID, null, null, null, null);
        assertSame(retValue, page);
        
        verify(mockParticipantService).getUploads(app, USER_ID, null, null, null, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadsWrongStudy() {
        DateTime start = TestConstants.CREATED_ON;
        DateTime end = TestConstants.MODIFIED_ON;
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getUploads(TEST_STUDY_ID, USER_ID, start.toString(), end.toString(), 50, "offsetKey");
    }
    
    @Test
    public void getNotificationRegistrations() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        List<NotificationRegistration> list = ImmutableList.of(NotificationRegistration.create());
        when(mockParticipantService.listRegistrations(app, USER_ID)).thenReturn(list);
        
        ResourceList<NotificationRegistration> retValue = controller.getNotificationRegistrations(TEST_STUDY_ID, USER_ID);
        assertSame(retValue.getItems(), list);
        
        verify(mockParticipantService).listRegistrations(app, USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getNotificationRegistrationsWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getNotificationRegistrations(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void sendNotification() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        NotificationMessage msg = new NotificationMessage.Builder().withSubject("subject")
                .withMessage("message").build();
        mockRequestBody(mockRequest, msg);
        
        controller.sendNotification(TEST_STUDY_ID, USER_ID);
        
        verify(mockParticipantService).sendNotification(eq(app), eq(USER_ID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        assertEquals(captured.getSubject(), "subject");
        assertEquals(captured.getMessage(), "message");
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void sendNotificationWrongStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.sendNotification(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void deleteTestParticipant() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        StatusMessage retValue = controller.deleteTestParticipant(TEST_STUDY_ID, USER_ID);
        assertEquals(retValue.getMessage(), "User deleted.");
        
        verify(mockUserAdminService).deleteUser(app, USER_ID);
    }    
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTestParticipantWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, "wrong-study", USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        controller.deleteTestParticipant(TEST_STUDY_ID, USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTestParticipantNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(null);
        
        controller.deleteTestParticipant(TEST_STUDY_ID, USER_ID);
    }    

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteTestParticipantNotTestUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        Account account = Account.create();
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID);
        account.setEnrollments(ImmutableSet.of(en));
        
        AccountId accountId = BridgeUtils.parseAccountId(TEST_APP_ID, USER_ID);
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        controller.deleteTestParticipant(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void getActivityEvents() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountInStudy();
        
        List<ActivityEvent> list = ImmutableList.of();
        when(mockParticipantService.getActivityEvents(app, USER_ID)).thenReturn(list);
        
        ResourceList<ActivityEvent> retValue = controller.getActivityEvents(TEST_STUDY_ID, USER_ID);
        assertSame(retValue.getItems(), list);
        
        verify(mockParticipantService).getActivityEvents(app, USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getActivityEventsWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        mockAccountNotInStudy();
        
        controller.getActivityEvents(TEST_STUDY_ID, USER_ID);
    }
    
    private void mockAccountInStudy() {
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, USER_ID))
            .thenReturn(list);
    }
    
    private void mockAccountNotInStudy() {
        Enrollment en = Enrollment.create(TEST_APP_ID, "some other study", USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, USER_ID))
            .thenReturn(list);
    }
}
