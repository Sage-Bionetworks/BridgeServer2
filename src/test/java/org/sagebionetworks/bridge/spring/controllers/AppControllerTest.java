package org.sagebionetworks.bridge.spring.controllers;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.APP_ACCESS_EXCEPTION_MSG;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.getValidApp;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.services.EmailVerificationStatus.VERIFIED;
import static org.sagebionetworks.bridge.services.AppEmailType.CONSENT_NOTIFICATION;
import static org.sagebionetworks.bridge.spring.controllers.AppController.CONSENT_EMAIL_VERIFIED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.AppController.RESEND_EMAIL_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CmsPublicKey;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.apps.AppAndUsers;
import org.sagebionetworks.bridge.models.apps.SynapseProjectIdTeamIdHolder;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UploadService;

public class AppControllerTest extends Mockito {
    private static final TypeReference<ResourceList<App>> APP_RESOURCE_LIST_TYPE = new TypeReference<ResourceList<App>>() {};
    private static final String DUMMY_VERIFICATION_TOKEN = "dummy-token";
    private static final String EMAIL_ADDRESS = "foo@foo.com";

    private static final String PEM_TEXT = "-----BEGIN CERTIFICATE-----\nMIIExDCCA6ygAwIBAgIGBhCnnOuXMA0GCSqGSIb3DQEBBQUAMIGeMQswCQYDVQQG\nEwJVUzELMAkGA1UECAwCV0ExEDAOBgNVBAcMB1NlYXR0bGUxGTAXBgNVBAoMEFNh\nVlOwuuAxumMyIq5W4Dqk8SBcH9Y4qlk7\nEND CERTIFICATE-----";

    private static final String TEST_PROJECT_ID = "synapseProjectId";
    private static final Long TEST_TEAM_ID = Long.parseLong("123");
    private static final String TEST_USER_ID = "1234";
    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";

    @Mock
    UserSession mockSession;
    
    @Mock
    UploadCertificateService mockUploadCertService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    EmailVerificationService mockVerificationService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    UploadService mockUploadService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    BridgeConfig mockBridgeConfig;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Spy
    @InjectMocks
    AppController controller;
    
    @Captor
    ArgumentCaptor<App> appCaptor;
    
    @Captor
    ArgumentCaptor<AppAndUsers> appAndUsersCaptor;
    
    private App app;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        // mock session with appId
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        when(mockSession.getId()).thenReturn(TEST_USER_ID);
        
        app = new DynamoApp();
        app.setSupportEmail(EMAIL_ADDRESS);
        app.setIdentifier(TEST_APP_ID);
        app.setSynapseProjectId(TEST_PROJECT_ID);
        app.setSynapseDataAccessTeamId(TEST_TEAM_ID);
        app.setActive(true);
     
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(Account.create()));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        when(mockAppService.createSynapseProjectTeam(any(), any())).thenReturn(app);
        when(mockVerificationService.getEmailStatus(EMAIL_ADDRESS)).thenReturn(VERIFIED);
        when(mockUploadCertService.getPublicKeyAsPem(TEST_APP_ID)).thenReturn(PEM_TEXT);
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AppController.class);
        assertGet(AppController.class, "getCurrentApp");
        assertPost(AppController.class, "updateAppForDeveloperOrAdmin");
        assertPost(AppController.class, "updateApp");
        assertGet(AppController.class, "getApp");
        assertGet(AppController.class, "getAllApps");
        assertCreate(AppController.class, "createApp");
        assertCreate(AppController.class, "createAppAndUsers");
        assertCreate(AppController.class, "createSynapse");
        assertDelete(AppController.class, "deleteApp");
        assertGet(AppController.class, "getAppPublicKeyAsPem");
        assertGet(AppController.class, "getEmailStatus");
        assertPost(AppController.class, "resendVerifyEmail");
        assertPost(AppController.class, "verifyEmail");
        assertPost(AppController.class, "verifySenderEmail");
        assertGet(AppController.class, "getUploads");
        assertGet(AppController.class, "getUploadsForApp");
        assertGet(AppController.class, "getAppMemberships");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).build();
        UserSession session = new UserSession(participant);
        session.setAppId(TEST_APP_ID);
        session.setAuthenticated(true);
        
        doReturn(session).when(controller).getSessionIfItExists();

        controller.getAppPublicKeyAsPem();
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotAccessGetUploadsForSpecifiedAppUnlessWorker () throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).build();
        UserSession session = new UserSession(participant);
        session.setAppId(TEST_APP_ID);
        session.setAuthenticated(true);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        doReturn(session).when(controller).getSessionIfItExists();

        controller.getUploadsForApp(TEST_APP_ID, startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test
    public void canDeactivateForSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteApp("not-protected", false);

        verify(mockAppService).deleteApp("not-protected", false);
        verifyNoMoreInteractions(mockAppService);
    }

    @Test
    public void canDeleteForSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteApp("not-protected", true);

        verify(mockAppService).deleteApp("not-protected", true);
        verifyNoMoreInteractions(mockAppService);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void cannotDeactivateForDeveloper() throws Exception {
        controller.deleteApp(TEST_APP_ID, false);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void cannotDeleteForDeveloper() throws Exception {
        controller.deleteApp(TEST_APP_ID, true);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deactivateAppThrowsGoodException() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        doThrow(new EntityNotFoundException(App.class)).when(mockAppService).deleteApp("not-protected",
                false);

        controller.deleteApp("not-protected", false);
    }

    @Test
    public void canCreateAppAndUser() throws Exception {
        // mock
        App app = getValidApp(AppControllerTest.class);
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);
        app.setVersion(1L);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(RESEARCHER, DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        List<String> adminIds = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);

        AppAndUsers mockAppAndUsers = new AppAndUsers(adminIds, app, mockUsers);
        TestUtils.mockRequestBody(mockRequest, mockAppAndUsers);

        // stub
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        ArgumentCaptor<AppAndUsers> argumentCaptor = ArgumentCaptor.forClass(AppAndUsers.class);
        when(mockAppService.createAppAndUsers(argumentCaptor.capture())).thenReturn(app);

        // execute
        VersionHolder result = controller.createAppAndUsers();
        
        // verify
        verify(mockAppService, times(1)).createAppAndUsers(any());
        AppAndUsers capObj = argumentCaptor.getValue();
        assertEquals(capObj.getApp(), app);
        assertEquals(capObj.getUsers(), mockUsers);
        assertEquals(capObj.getAdminIds(), adminIds);
        assertEquals(result.getVersion(), app.getVersion());
    }


    @Test
    public void canCreateSynapse() throws Exception {
        // mock
        List<String> mockUserIds = ImmutableList.of(TEST_USER_ID);
        mockRequestBody(mockRequest, mockUserIds);

        // stub
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        SynapseProjectIdTeamIdHolder result = controller.createSynapse();

        // verify
        verify(mockAppService).getApp(TEST_APP_ID);
        verify(mockAppService).createSynapseProjectTeam(mockUserIds, app);

        assertEquals(result.getProjectId(), TEST_PROJECT_ID);
        assertEquals(result.getTeamId(), TEST_TEAM_ID);
    }

    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        CmsPublicKey result = controller.getAppPublicKeyAsPem();

        assertTrue(result.getPublicKey().contains("-----BEGIN CERTIFICATE-----"));
    }
    
    @Test
    public void getEmailStatus() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        EmailVerificationStatusHolder result = controller.getEmailStatus();
        
        verify(mockVerificationService).getEmailStatus(EMAIL_ADDRESS);
        assertEquals(result.getStatus(), VERIFIED);
    }
    
    @Test
    public void verifySenderEmail() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockVerificationService.verifyEmailAddress(EMAIL_ADDRESS)).thenReturn(VERIFIED);
        
        EmailVerificationStatusHolder result = controller.verifySenderEmail();
        
        verify(mockVerificationService).verifyEmailAddress(EMAIL_ADDRESS);
        assertEquals(result.getStatus(), VERIFIED);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void resendVerifyEmailNullType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail(null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void resendVerifyEmailEmptyType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail("");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void resendVerifyEmailBlankType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail("   ");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void resendVerifyEmailInvalidType() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.resendVerifyEmail("bad-type");
    }

    @Test
    public void resendVerifyEmailSuccess() throws Exception {
        // Mock session
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        // Execute
        StatusMessage result = controller.resendVerifyEmail(CONSENT_NOTIFICATION.toString().toLowerCase());
        assertEquals(result, RESEND_EMAIL_MSG);

        // Verify call to AppService
        verify(mockAppService).sendVerifyEmail(TEST_APP_ID, CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullType() throws Exception {
        controller.verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailEmptyType() throws Exception {
        controller.verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN, "");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailBlankType() throws Exception {
        controller.verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN, "   ");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailInvalidType() throws Exception {
        controller.verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN, "bad-type");
    }

    @Test
    public void verifyEmailSuccess() throws Exception {
        // Execute
        StatusMessage result = controller.verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN,
                CONSENT_NOTIFICATION.toString().toLowerCase());
        assertEquals(result, CONSENT_EMAIL_VERIFIED_MSG);

        // Verify call to AppService
        verify(mockAppService).verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN, CONSENT_NOTIFICATION);
    }

    @Test
    public void developerCanAccessCurrentApp() throws Exception {
        testRoleAccessToCurrentApp(DEVELOPER);
    }
    
    @Test
    public void researcherCanAccessCurrentApp() throws Exception {
        testRoleAccessToCurrentApp(RESEARCHER);
    }
    
    @Test
    public void adminCanAccessCurrentApp() throws Exception {
        testRoleAccessToCurrentApp(ADMIN);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void userCannotAccessCurrentApp() throws Exception {
        testRoleAccessToCurrentApp(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void canGetUploadsForApp() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");
        List<Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockUploadService).getAppUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);
        
        ForwardCursorPagedResourceList<UploadView> result = controller.getUploads(startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
        
        verify(mockUploadService).getAppUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);
        verify(mockAppService, never()).getApp(TEST_APP_ID);
        // in other words, it's the object we mocked out from the service, we were returned the value.
        assertNull(result.getRequestParams().get("offsetBy"));
        assertNull(result.getTotal());
        assertEquals(result.getRequestParams().get("pageSize"), API_MAXIMUM_PAGE_SIZE);
        assertEquals(result.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(result.getRequestParams().get("endTime"), endTime.toString());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsForAppWithNullAppId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForApp(null, startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsForAppWithEmptyAppId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForApp("", startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsForAppWithBlankAppId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForApp(" ", startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void canGetUploadsForSpecifiedApp() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        List<Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockUploadService).getAppUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE,
                null);

        ForwardCursorPagedResourceList<UploadView> result = controller.getUploadsForApp(TEST_APP_ID, startTime.toString(), endTime.toString(),
                API_MAXIMUM_PAGE_SIZE, null);

        verify(mockUploadService).getAppUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);

        // in other words, it's the object we mocked out from the service, we were returned the value.
        assertNull(result.getRequestParams().get("offsetBy"));
        assertNull(result.getTotal());
        assertEquals(result.getRequestParams().get("pageSize"), API_MAXIMUM_PAGE_SIZE);
        assertEquals(result.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(result.getRequestParams().get("endTime"), endTime.toString());
    }
    
    @Test
    public void updateApp() throws Exception {
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        App created = App.create();
        created.setVersion(3L);
        when(mockAppService.updateApp(any(), anyBoolean())).thenReturn(created);
        
        App app = App.create();
        app.setName("value to seek");
        mockRequestBody(mockRequest, app);
        
        VersionHolder holder = controller.updateApp(TEST_APP_ID);
        assertEquals(holder.getVersion(), Long.valueOf(3L));
        
        verify(mockAppService).updateApp(appCaptor.capture(), eq(true));
        assertEquals(appCaptor.getValue().getName(), "value to seek");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateAppRejectsAppAdmin() throws Exception {
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().
                withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getSessionIfItExists();
        
        controller.updateApp("some-app");
    }
    
    @Test
    public void getApp() throws Exception {
        App retrieved = App.create();
        when(mockAppService.getApp("some-app", true)).thenReturn(retrieved);
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN, WORKER);
        
        App result = controller.getApp("some-app");
        assertSame(result, retrieved);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAppRejectsNonSuperAdmin() throws Exception { 
        when(mockSession.isInRole(DEVELOPER)).thenReturn(false);
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        doReturn(mockSession).when(controller).getSessionIfItExists();
        
        controller.getApp("some-app");
    }
    
    private List<App> mockApps(Boolean... activeStates) {
        List<App> apps = Lists.newArrayListWithCapacity(activeStates.length);
        for (int i=0; i < activeStates.length; i++) {
            App app = App.create();
            app.setName("app"+(i+1));
            app.setIdentifier("app"+i);
            app.setSponsorName("sponsor name"); // this shouldn't be in summary
            app.setActive(activeStates[i]);
            apps.add(app);
        }
        return apps;
    }
    
    @Test
    public void getAllApps_defaultsToFullWithoutDeleted() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps(null, null, null);
        
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 2);
        assertNotNull(deser.getItems().get(0).getSponsorName());
        assertNotNull(deser.getItems().get(1).getSponsorName());
    }
    
    @Test
    public void getAllApps_summaryIncludeDeleted() throws Exception {
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps(null, "true", "true");
        
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 3);
        assertNull(deser.getItems().get(0).getSponsorName());
        assertNull(deser.getItems().get(1).getSponsorName());
        assertNull(deser.getItems().get(2).getSponsorName());
    }
    
    @Test
    public void getAllApps_summaryExcludeDeleted() throws Exception {
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps(null, "true", "false");
        
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 2);
        assertNull(deser.getItems().get(0).getSponsorName());
        assertNull(deser.getItems().get(1).getSponsorName());
    }

    @Test
    public void getAllApps_formatSummaryIncludeDeleted() throws Exception {
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps("summary", null, "true");
        
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 3);
        assertNull(deser.getItems().get(0).getSponsorName());
        assertNull(deser.getItems().get(1).getSponsorName());
        assertNull(deser.getItems().get(2).getSponsorName());
    }
    
    @Test
    public void getAllApps_formatSummaryExcludeDeleted() throws Exception {
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps("summary", null, "false");
        
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 2);
        assertNull(deser.getItems().get(0).getSponsorName());
        assertNull(deser.getItems().get(1).getSponsorName());
    }
    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void getAllApps_detailNotAuthorized() throws Exception {
        controller.getAllApps(null, null, null);
    }
    
    @Test
    public void getAllApps_detailIncludeDeleted() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps(null, null, "true");
 
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 3);
        assertNotNull(deser.getItems().get(0).getSponsorName());
        assertNotNull(deser.getItems().get(1).getSponsorName());
        assertNotNull(deser.getItems().get(2).getSponsorName());
    }
    
    @Test
    public void getAllApps_detailExcludeDeleted() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        List<App> apps = mockApps(TRUE, TRUE, FALSE);
        when(mockAppService.getApps()).thenReturn(apps);
        
        String json = controller.getAllApps(null, null, "false");
 
        ResourceList<App> deser = BridgeObjectMapper.get().readValue(json, APP_RESOURCE_LIST_TYPE);
        assertEquals(deser.getItems().size(), 2);
        assertNotNull(deser.getItems().get(0).getSponsorName());
        assertNotNull(deser.getItems().get(1).getSponsorName());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAppRejectsNonSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.createApp();
    }
    
    @Test
    public void createApp() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);

        App created = App.create();
        created.setVersion(3L);
        when(mockAppService.createApp(any())).thenReturn(created);
        
        App newApp = App.create();
        newApp.setName("some app");
        mockRequestBody(mockRequest, newApp);
        
        VersionHolder keys = controller.createApp();
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockAppService).createApp(newApp);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createAppAndUsersRejectsNoneSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.createAppAndUsers();
    }
    
    @Test
    public void createAppAndUsers() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        App created = App.create();
        created.setVersion(3L);
        when(mockAppService.createAppAndUsers(any())).thenReturn(created);
        
        App newApp = App.create();
        newApp.setName("some app");
        
        AppAndUsers appAndUsers = new AppAndUsers(
                ImmutableList.of("admin1", "admin2"), newApp,
                ImmutableList.of(new StudyParticipant.Builder().build()));
        mockRequestBody(mockRequest, appAndUsers);
        
        VersionHolder keys = controller.createAppAndUsers();
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockAppService).createAppAndUsers(appAndUsersCaptor.capture());
        
        AppAndUsers captured =  appAndUsersCaptor.getValue();
        assertEquals(captured.getAdminIds(), ImmutableList.of("admin1", "admin2"));
        assertEquals(captured.getUsers().size(), 1);
        assertEquals(captured.getApp(), newApp);
    }
        
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteAppRejectsNonSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.deleteApp("other-app", true);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = ".*Admin cannot delete the app they are associated with.*")
    public void deleteAppRejectsCallerInApp() throws Exception {
        // API is protected by the whitelist so this test must target some other app
        when(mockSession.getAppId()).thenReturn("other-app");
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        controller.deleteApp("other-app", true);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = ".*other-app is protected by whitelist.*")
    public void deleteAppRejectsWhitelistedApp() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        when(controller.getAppWhitelist()).thenReturn(ImmutableSet.of("other-app"));
        controller.deleteApp("other-app", true);
    }
    
    @Test
    public void deleteApp() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        controller.deleteApp("delete-app", true);
        
        verify(mockAppService).deleteApp("delete-app", Boolean.TRUE);
    }
    
    @Test
    public void getAppMemberships() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL)
                .withEmailVerified(true)
                .withRoles(ImmutableSet.of(DEVELOPER))
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        doReturn(mockSession).when(controller).getAuthenticatedSession();

        mockApp("App D", "appD", true);
        mockApp("App C", "appC", true);
        mockApp("App B", "appB", true);
        mockApp("App A", "appA", false);
        
        List<String> list = ImmutableList.of("appA", "appB", "appC");
        when(mockAccountService.getAppIdsForUser(SYNAPSE_USER_ID)).thenReturn(list);
        
        String jsonString = controller.getAppMemberships();
        JsonNode node = BridgeObjectMapper.get().readTree(jsonString).get("items");
        
        assertEquals(node.size(), 2);
        assertEquals(node.get(0).get("name").textValue(), "App B");
        assertEquals(node.get(0).get("identifier").textValue(), "appB");
        assertEquals(node.get(1).get("name").textValue(), "App C");
        assertEquals(node.get(1).get("identifier").textValue(), "appC");
    }
    
    @Test
    public void getAppMembershipsForCrossAppAdmin() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL)
                .withRoles(ImmutableSet.of(ADMIN)).withEmailVerified(true)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(true);
        
        doReturn(mockSession).when(controller).getAuthenticatedSession();

        App appD = mockApp("App D", "appD", true);
        App appC = mockApp("App C", "appC", true);
        App appB = mockApp("App B", "appB", true);
        App appA = mockApp("App A", "appA", false);
        when(mockAppService.getApps()).thenReturn(ImmutableList.of(appA, appB, appC, appD));
        
        // This user is only associated to the API app, but they are an admin
        List<String> list = ImmutableList.of(TEST_APP_ID);
        when(mockAccountService.getAppIdsForUser(SYNAPSE_USER_ID)).thenReturn(list);
        
        String jsonString = controller.getAppMemberships();
        JsonNode node = BridgeObjectMapper.get().readTree(jsonString).get("items");

        assertEquals(node.size(), 3);
        assertEquals(node.get(0).get("name").textValue(), "App B");
        assertEquals(node.get(0).get("identifier").textValue(), "appB");
        assertEquals(node.get(1).get("name").textValue(), "App C");
        assertEquals(node.get(1).get("identifier").textValue(), "appC");
        assertEquals(node.get(2).get("name").textValue(), "App D");
        assertEquals(node.get(2).get("identifier").textValue(), "appD");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = ".*" + APP_ACCESS_EXCEPTION_MSG + ".*")
    public void getAppMembershipsForNonAdminUsers() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL)
                .withEmailVerified(true)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        doReturn(mockSession).when(controller).getAuthenticatedSession();

        controller.getAppMemberships();
    }
    
    @Test
    public void getAppMembershipsForSuperadminNoSynapseId() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN)).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(true);
        doReturn(mockSession).when(controller).getAuthenticatedSession();
        
        App app1 = App.create();
        app1.setName("Name1");
        app1.setActive(true);
        App app2 = App.create();
        app2.setActive(true);
        app2.setName("Name2");
        
        when(mockAppService.getApps()).thenReturn(ImmutableList.of(app1, app2));
        
        String returnValue = controller.getAppMemberships();
        ResourceList<App> list = BridgeObjectMapper.get().readValue(returnValue, APP_RESOURCE_LIST_TYPE);
        
        assertEquals(list.getItems().size(), 2);
        assertEquals(list.getItems().get(0).getName(), "Name1");
        assertEquals(list.getItems().get(1).getName(), "Name2");
        
        verify(mockAppService, never()).getApp(any());
    }
    
    @Test
    public void updateAppForDeveloperOrAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        App app = App.create();
        app.setName("My new app");
        app.setVersion(10L);
        
        TestUtils.mockRequestBody(mockRequest, app);
        when(mockAppService.updateApp(any(), anyBoolean())).thenReturn(app);
        
        VersionHolder retValue = controller.updateAppForDeveloperOrAdmin();
        assertEquals(retValue.getVersion(), Long.valueOf(10L));
        
        verify(mockAppService).updateApp(appCaptor.capture(), eq(false));
        assertEquals(appCaptor.getValue().getName(), "My new app");
    }
    
    private App mockApp(String name, String appId, boolean active) {
        App app = App.create();
        app.setName(name);
        app.setIdentifier(appId);
        app.setActive(active);
        when(mockAppService.getApp(appId)).thenReturn(app);
        return app;
    }
    
    private void testRoleAccessToCurrentApp(Roles role) throws Exception {
        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        if (role != null) {
            builder.withRoles(ImmutableSet.of(role));
        }
        UserSession session = new UserSession(builder.build());
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getSessionIfItExists();
        
        App result = controller.getCurrentApp();
        assertEquals(result.getSupportEmail(), EMAIL_ADDRESS);        
    }
}
