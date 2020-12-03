package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.EnrollmentService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.UserAdminService;

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
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Enrollment> enrollmentCaptor;
    
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
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        
        List<EnrollmentDetail> list = ImmutableList.of();
        when(mockEnrollmentService.getEnrollmentsForUser(
                TEST_APP_ID, TEST_STUDY_ID, USER_ID)).thenReturn(list);
        
        PagedResourceList<EnrollmentDetail> page = controller.getEnrollmentsForUser(TEST_STUDY_ID, USER_ID);
        assertSame(page.getItems(), list);
        
        verify(mockEnrollmentService).getEnrollmentsForUser(TEST_APP_ID, TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void searchForAccountSummaries() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
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
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
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
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withLastName("lastName").build();
        when(mockParticipantService.getParticipant(app, USER_ID, true)).thenReturn(participant);
        
        String retValue = controller.getParticipant(TEST_STUDY_ID, USER_ID, true);
        StudyParticipant deser = BridgeObjectMapper.get().readValue(retValue, StudyParticipant.class);
        assertEquals(deser.getLastName(), participant.getLastName());
        
        verify(mockParticipantService).getParticipant(app, USER_ID, true);
    }
    
    @Test
    public void getParticipantExcludeConsents() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        controller.getParticipant(TEST_STUDY_ID, USER_ID, false);
        
        verify(mockParticipantService).getParticipant(app, USER_ID, false);
    }
    
    // TODO
    
    @Test
    public void getRequestInfo() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        RequestInfo requestInfo = new RequestInfo.Builder()
                .withAppId(TEST_APP_ID)
                .withLanguages(LANGUAGES).build();
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(requestInfo);
        
        String retValue = controller.getRequestInfo(TEST_STUDY_ID, USER_ID);
        RequestInfo deser = BridgeObjectMapper.get().readValue(retValue, RequestInfo.class);
        assertEquals(deser.getLanguages(), LANGUAGES);
        
        verify(mockRequestInfoService).getRequestInfo(USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRequestInfoWrongApp() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
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
        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ADMIN);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        StudyParticipant participant = new StudyParticipant.Builder()
                .withId("wrong-id")
                .withLastName("lastName").build();
        mockRequestBody(mockRequest, participant);

        controller.updateParticipant(TEST_STUDY_ID, USER_ID);
        
    }
    
    @Test
    public void signOut() {
    }

    @Test
    public void requestResetPassword() {
    }

    @Test
    public void resendEmailVerification() {
    }

    @Test
    public void  resendPhoneVerification() {
    }
    
    @Test
    public void resendConsentAgreement() {
    }

    @Test
    public void getUploads() {
    }

    @Test
    public void getNotificationRegistrations() {
    }

    @Test
    public void sendNotification() {
    }

    @Test
    public void deleteTestParticipant() {
    }    
    
    @Test
    public void getActivityEvents() {
    }

}
