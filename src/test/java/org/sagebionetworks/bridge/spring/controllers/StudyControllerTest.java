package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.STUDY_ACCESS_EXCEPTION_MSG;
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
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.getValidStudy;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.services.EmailVerificationStatus.VERIFIED;
import static org.sagebionetworks.bridge.services.StudyEmailType.CONSENT_NOTIFICATION;
import static org.sagebionetworks.bridge.spring.controllers.StudyController.CONSENT_EMAIL_VERIFIED_MSG;
import static org.sagebionetworks.bridge.spring.controllers.StudyController.RESEND_EMAIL_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DefaultObjectMapper;
import org.sagebionetworks.bridge.models.CmsPublicKey;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.studies.SynapseProjectIdTeamIdHolder;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.EmailVerificationService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadCertificateService;
import org.sagebionetworks.bridge.services.UploadService;

public class StudyControllerTest extends Mockito {
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
    StudyService mockStudyService;
    
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
    StudyController controller;
    
    @Captor
    ArgumentCaptor<Study> studyCaptor;
    
    @Captor
    ArgumentCaptor<StudyAndUsers> studyAndUsersCaptor;
    
    private Study study;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        // mock session with study identifier
        when(mockSession.getStudyIdentifier()).thenReturn(TEST_APP_ID);
        when(mockSession.getId()).thenReturn(USER_ID);
        
        study = new DynamoStudy();
        study.setSupportEmail(EMAIL_ADDRESS);
        study.setIdentifier(TEST_APP_ID);
        study.setSynapseProjectId(TEST_PROJECT_ID);
        study.setSynapseDataAccessTeamId(TEST_TEAM_ID);
        study.setActive(true);
     
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Account.create());
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);
        when(mockStudyService.createSynapseProjectTeam(any(), any())).thenReturn(study);
        when(mockVerificationService.getEmailStatus(EMAIL_ADDRESS)).thenReturn(VERIFIED);
        when(mockUploadCertService.getPublicKeyAsPem(TEST_APP_ID)).thenReturn(PEM_TEXT);
        when(mockBridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(StudyController.class);
        assertGet(StudyController.class, "getCurrentStudy");
        assertPost(StudyController.class, "updateStudyForDeveloperOrAdmin");
        assertPost(StudyController.class, "updateStudy");
        assertGet(StudyController.class, "getStudy");
        assertGet(StudyController.class, "getAllStudies");
        assertCreate(StudyController.class, "createStudy");
        assertCreate(StudyController.class, "createStudyAndUsers");
        assertCreate(StudyController.class, "createSynapse");
        assertDelete(StudyController.class, "deleteStudy");
        assertGet(StudyController.class, "getStudyPublicKeyAsPem");
        assertGet(StudyController.class, "getEmailStatus");
        assertPost(StudyController.class, "resendVerifyEmail");
        assertPost(StudyController.class, "verifyEmail");
        assertPost(StudyController.class, "verifySenderEmail");
        assertGet(StudyController.class, "getUploads");
        assertGet(StudyController.class, "getUploadsForStudy");
        assertGet(StudyController.class, "getStudyMemberships");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotAccessCmsPublicKeyUnlessDeveloper() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).build();
        UserSession session = new UserSession(participant);
        session.setStudyIdentifier(TEST_APP_ID);
        session.setAuthenticated(true);
        
        doReturn(session).when(controller).getSessionIfItExists();

        controller.getStudyPublicKeyAsPem();
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void cannotAccessGetUploadsForSpecifiedStudyUnlessWorker () throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).build();
        UserSession session = new UserSession(participant);
        session.setStudyIdentifier(TEST_APP_ID);
        session.setAuthenticated(true);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        doReturn(session).when(controller).getSessionIfItExists();

        controller.getUploadsForStudy(TEST_APP_ID, startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test
    public void canDeactivateForSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteStudy("not-protected", false);

        verify(mockStudyService).deleteStudy("not-protected", false);
        verifyNoMoreInteractions(mockStudyService);
    }

    @Test
    public void canDeleteForSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteStudy("not-protected", true);

        verify(mockStudyService).deleteStudy("not-protected", true);
        verifyNoMoreInteractions(mockStudyService);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void cannotDeactivateForDeveloper() throws Exception {
        controller.deleteStudy(TEST_APP_ID, false);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void cannotDeleteForDeveloper() throws Exception {
        controller.deleteStudy(TEST_APP_ID, true);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deactivateStudyThrowsGoodException() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        doThrow(new EntityNotFoundException(Study.class)).when(mockStudyService).deleteStudy("not-protected",
                false);

        controller.deleteStudy("not-protected", false);
    }

    @Test
    public void canCreateStudyAndUser() throws Exception {
        // mock
        Study study = getValidStudy(StudyControllerTest.class);
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setVersion(1L);

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

        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(adminIds, study, mockUsers);
        TestUtils.mockRequestBody(mockRequest, mockStudyAndUsers);

        // stub
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        ArgumentCaptor<StudyAndUsers> argumentCaptor = ArgumentCaptor.forClass(StudyAndUsers.class);
        when(mockStudyService.createStudyAndUsers(argumentCaptor.capture())).thenReturn(study);

        // execute
        VersionHolder result = controller.createStudyAndUsers();
        
        // verify
        verify(mockStudyService, times(1)).createStudyAndUsers(any());
        StudyAndUsers capObj = argumentCaptor.getValue();
        assertEquals(capObj.getStudy(), study);
        assertEquals(capObj.getUsers(), mockUsers);
        assertEquals(capObj.getAdminIds(), adminIds);
        assertEquals(result.getVersion(), study.getVersion());
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
        verify(mockStudyService).getStudy(TEST_APP_ID);
        verify(mockStudyService).createSynapseProjectTeam(mockUserIds, study);

        assertEquals(result.getProjectId(), TEST_PROJECT_ID);
        assertEquals(result.getTeamId(), TEST_TEAM_ID);
    }

    @Test
    public void canGetCmsPublicKeyPemFile() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);
        
        CmsPublicKey result = controller.getStudyPublicKeyAsPem();

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

        // Verify call to StudyService
        verify(mockStudyService).sendVerifyEmail(TEST_APP_ID, CONSENT_NOTIFICATION);
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

        // Verify call to StudyService
        verify(mockStudyService).verifyEmail(TEST_APP_ID, DUMMY_VERIFICATION_TOKEN, CONSENT_NOTIFICATION);
    }

    @Test
    public void developerCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(DEVELOPER);
    }
    
    @Test
    public void researcherCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(RESEARCHER);
    }
    
    @Test
    public void adminCanAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(ADMIN);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void userCannotAccessCurrentStudy() throws Exception {
        testRoleAccessToCurrentStudy(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void canGetUploadsForStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(ADMIN);
        
        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");
        List<Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockUploadService).getStudyUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);
        
        ForwardCursorPagedResourceList<UploadView> result = controller.getUploads(startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
        
        verify(mockUploadService).getStudyUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);
        verify(mockStudyService, never()).getStudy(TEST_APP_ID);
        // in other words, it's the object we mocked out from the service, we were returned the value.
        assertNull(result.getRequestParams().get("offsetBy"));
        assertNull(result.getTotal());
        assertEquals(result.getRequestParams().get("pageSize"), API_MAXIMUM_PAGE_SIZE);
        assertEquals(result.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(result.getRequestParams().get("endTime"), endTime.toString());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsForStudyWithNullStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy(null, startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsForStudyWitEmptyStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy("", startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsForStudyWithBlankStudyId() {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        controller.getUploadsForStudy(" ", startTime.toString(), endTime.toString(), API_MAXIMUM_PAGE_SIZE, null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void canGetUploadsForSpecifiedStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(WORKER);

        DateTime startTime = DateTime.parse("2010-01-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2010-01-02T00:00:00.000Z");

        List<Upload> list = ImmutableList.of();

        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(list, null)
                .withRequestParam("pageSize", API_MAXIMUM_PAGE_SIZE)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime);
        doReturn(uploads).when(mockUploadService).getStudyUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE,
                null);

        ForwardCursorPagedResourceList<UploadView> result = controller.getUploadsForStudy(TEST_APP_ID, startTime.toString(), endTime.toString(),
                API_MAXIMUM_PAGE_SIZE, null);

        verify(mockUploadService).getStudyUploads(TEST_APP_ID, startTime, endTime, API_MAXIMUM_PAGE_SIZE, null);

        // in other words, it's the object we mocked out from the service, we were returned the value.
        assertNull(result.getRequestParams().get("offsetBy"));
        assertNull(result.getTotal());
        assertEquals(result.getRequestParams().get("pageSize"), API_MAXIMUM_PAGE_SIZE);
        assertEquals(result.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(result.getRequestParams().get("endTime"), endTime.toString());
    }
    
    @Test
    public void getSummaryStudiesWithFormatWorks() throws Exception {
        List<Study> studies = ImmutableList.of(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        String result = controller.getAllStudies("summary", null);
        ResourceList<Study> list = BridgeObjectMapper.get().readValue(result, new TypeReference<ResourceList<Study>>() {});
        assertTrue((Boolean)list.getRequestParams().get("summary"));

        assertFalse(result.contains("healthCodeExportEnabled"));
    }

    @Test
    public void getSummaryStudiesWithSummaryWorks() throws Exception {
        List<Study> studies = ImmutableList.of(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        String result = controller.getAllStudies(null, "true");

        assertFalse(result.contains("healthCodeExportEnabled"));
    }

    @Test
    public void getSummaryStudiesWithInactiveOnes() throws Exception {
        DynamoStudy testStudy1 = new DynamoStudy();
        testStudy1.setName("test_study_1");
        testStudy1.setActive(true);

        DynamoStudy testStudy2 = new DynamoStudy();
        testStudy2.setName("test_study_2");

        List<Study> studies = ImmutableList.of(testStudy1, testStudy2);
        doReturn(studies).when(mockStudyService).getStudies();

        String result = controller.getAllStudies("summary", null);

        // only active studies will be returned
        JsonNode recordJsonNode = DefaultObjectMapper.INSTANCE.readTree(result);
        JsonNode items = recordJsonNode.get("items");
        assertTrue(items.size() == 1);
        JsonNode study = items.get(0);
        assertEquals("test_study_1", study.get("name").asText());
        assertFalse(result.contains("healthCodeExportEnabled"));

        verify(controller, never()).getAuthenticatedSession(ADMIN);
    }
    
    @Test
    public void getFullStudiesWorks() throws Exception {
        List<Study> studies = ImmutableList.of(new DynamoStudy());
        doReturn(studies).when(mockStudyService).getStudies();
        
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        String result = controller.getAllStudies(null, "false");
        ResourceList<Study> list = BridgeObjectMapper.get().readValue(result, new TypeReference<ResourceList<Study>>() {});
        assertFalse((Boolean)list.getRequestParams().get("summary"));

        assertTrue(result.contains("healthCodeExportEnabled"));
    }

    @Test
    public void updateStudy() throws Exception {
        when(mockSession.getStudyIdentifier()).thenReturn(TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        Study created = Study.create();
        created.setVersion(3L);
        when(mockStudyService.updateStudy(any(), anyBoolean())).thenReturn(created);
        
        Study study = Study.create();
        study.setName("value to seek");
        mockRequestBody(mockRequest, study);
        
        VersionHolder holder = controller.updateStudy(TEST_APP_ID);
        assertEquals(holder.getVersion(), Long.valueOf(3L));
        
        verify(mockStudyService).updateStudy(studyCaptor.capture(), eq(true));
        assertEquals(studyCaptor.getValue().getName(), "value to seek");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateStudyRejectsStudyAdmin() throws Exception {
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().
                withRoles(ImmutableSet.of(ADMIN)).build());
        doReturn(mockSession).when(controller).getSessionIfItExists();
        
        controller.updateStudy("some-study");
    }
    
    @Test
    public void getStudy() throws Exception {
        Study retrieved = Study.create();
        when(mockStudyService.getStudy("some-study", true)).thenReturn(retrieved);
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN, WORKER);
        
        Study result = controller.getStudy("some-study");
        assertSame(result, retrieved);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getStudyRejectsNonSuperAdmin() throws Exception { 
        when(mockSession.isInRole(DEVELOPER)).thenReturn(false);
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        doReturn(mockSession).when(controller).getSessionIfItExists();
        
        controller.getStudy("some-study");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAllStudiesFullStudyRejectsStudyAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.getAllStudies(null, null);
    }
    
    @Test
    public void getAllStudiesSummary() throws Exception {
        // Two active and one deleted study
        Study study1 = Study.create();
        study1.setName("study1");
        study1.setSponsorName("sponsor name"); // this typeof field shouldn't be in summary
        study1.setActive(true);
        Study study2 = Study.create();
        study2.setName("study2");
        study2.setActive(true);
        Study study3 = Study.create();
        study3.setName("study3");
        study3.setActive(false);
        List<Study> studies = ImmutableList.of(study1, study2, study3);
        when(mockStudyService.getStudies()).thenReturn(studies);
        
        String json = controller.getAllStudies("summary", null);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals(node.get("items").size(), 2);
        assertEquals(node.get("items").get(0).get("name").textValue(), "study1");
        assertEquals(node.get("items").get(1).get("name").textValue(), "study2");
        assertNull(node.get("items").get(0).get("sponsorName"));
        assertTrue(node.get("requestParams").get("summary").booleanValue());
    }
    
    @Test
    public void getAllStudies() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        // Two active and one deleted study
        Study study1 = Study.create();
        study1.setName("study1");
        study1.setSponsorName("sponsor name"); // this typeof field shouldn't be in summary
        study1.setActive(true);
        Study study2 = Study.create();
        study2.setName("study2");
        study2.setActive(true);
        Study study3 = Study.create();
        study3.setName("study3");
        study3.setActive(false);
        List<Study> studies = ImmutableList.of(study1, study2, study3);
        when(mockStudyService.getStudies()).thenReturn(studies);
        
        String json = controller.getAllStudies(null, null);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("items").size(), 3);
        assertEquals(node.get("items").get(0).get("name").textValue(), "study1");
        assertEquals(node.get("items").get(0).get("sponsorName").textValue(), "sponsor name");
        assertEquals(node.get("items").get(1).get("name").textValue(), "study2");
        assertEquals(node.get("items").get(2).get("name").textValue(), "study3");
        assertFalse(node.get("requestParams").get("summary").booleanValue());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createStudyRejectsNonSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.createStudy();
    }
    
    @Test
    public void createStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);

        Study created = Study.create();
        created.setVersion(3L);
        when(mockStudyService.createStudy(any())).thenReturn(created);
        
        Study newStudy = Study.create();
        newStudy.setName("some study");
        mockRequestBody(mockRequest, newStudy);
        
        VersionHolder keys = controller.createStudy();
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockStudyService).createStudy(newStudy);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void createStudyAndUsersRejectsNoneSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.createStudyAndUsers();
    }
    
    @Test
    public void createStudyAndUsers() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        Study created = Study.create();
        created.setVersion(3L);
        when(mockStudyService.createStudyAndUsers(any())).thenReturn(created);
        
        Study newStudy = Study.create();
        newStudy.setName("some study");
        
        StudyAndUsers studyAndUsers = new StudyAndUsers(
                ImmutableList.of("admin1", "admin2"), newStudy,
                ImmutableList.of(new StudyParticipant.Builder().build()));
        mockRequestBody(mockRequest, studyAndUsers);
        
        VersionHolder keys = controller.createStudyAndUsers();
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockStudyService).createStudyAndUsers(studyAndUsersCaptor.capture());
        
        StudyAndUsers captured =  studyAndUsersCaptor.getValue();
        assertEquals(captured.getAdminIds(), ImmutableList.of("admin1", "admin2"));
        assertEquals(captured.getUsers().size(), 1);
        assertEquals(captured.getStudy(), newStudy);
    }
        
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteStudyRejectsNonSuperAdmin() throws Exception {
        doReturn(mockSession).when(controller).getSessionIfItExists();
        when(mockSession.isAuthenticated()).thenReturn(true);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(false);
        when(mockSession.getParticipant()).thenReturn(new StudyParticipant.Builder().build());
        
        controller.deleteStudy("other-study", true);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = ".*Admin cannot delete the study they are associated with.*")
    public void deleteStudyRejectsCallerInStudy() throws Exception {
        // API is protected by the whitelist so this test must target some other study
        when(mockSession.getStudyIdentifier()).thenReturn("other-study");
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        controller.deleteStudy("other-study", true);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = ".*other-study is protected by whitelist.*")
    public void deleteStudyRejectsWhitelistedStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        when(controller.getStudyWhitelist()).thenReturn(ImmutableSet.of("other-study"));
        controller.deleteStudy("other-study", true);
    }
    
    @Test
    public void deleteStudy() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedSession(SUPERADMIN);
        
        controller.deleteStudy("delete-study", true);
        
        verify(mockStudyService).deleteStudy("delete-study", Boolean.TRUE);
    }
    
    @Test
    public void getStudyMemberships() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL)
                .withEmailVerified(true)
                .withRoles(ImmutableSet.of(DEVELOPER))
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        doReturn(mockSession).when(controller).getAuthenticatedSession();

        mockStudy("Study D", "studyD", true);
        mockStudy("Study C", "studyC", true);
        mockStudy("Study B", "studyB", true);
        mockStudy("Study A", "studyA", false);
        
        List<String> list = ImmutableList.of("studyA", "studyB", "studyC");
        when(mockAccountService.getStudyIdsForUser(SYNAPSE_USER_ID)).thenReturn(list);
        
        String jsonString = controller.getStudyMemberships();
        JsonNode node = BridgeObjectMapper.get().readTree(jsonString).get("items");
        
        assertEquals(node.size(), 2);
        assertEquals(node.get(0).get("name").textValue(), "Study B");
        assertEquals(node.get(0).get("identifier").textValue(), "studyB");
        assertEquals(node.get(1).get("name").textValue(), "Study C");
        assertEquals(node.get(1).get("identifier").textValue(), "studyC");
    }
    
    @Test
    public void getStudyMembershipsForCrossStudyAdmin() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL)
                .withRoles(ImmutableSet.of(ADMIN)).withEmailVerified(true)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        when(mockSession.isInRole(SUPERADMIN)).thenReturn(true);
        
        doReturn(mockSession).when(controller).getAuthenticatedSession();

        Study studyD = mockStudy("Study D", "studyD", true);
        Study studyC = mockStudy("Study C", "studyC", true);
        Study studyB = mockStudy("Study B", "studyB", true);
        Study studyA = mockStudy("Study A", "studyA", false);
        when(mockStudyService.getStudies()).thenReturn(ImmutableList.of(studyA, studyB, studyC, studyD));
        
        // This user is only associated to the API study, but they are an admin
        List<String> list = ImmutableList.of(TEST_APP_ID);
        when(mockAccountService.getStudyIdsForUser(SYNAPSE_USER_ID)).thenReturn(list);
        
        String jsonString = controller.getStudyMemberships();
        JsonNode node = BridgeObjectMapper.get().readTree(jsonString).get("items");

        assertEquals(node.size(), 3);
        assertEquals(node.get(0).get("name").textValue(), "Study B");
        assertEquals(node.get(0).get("identifier").textValue(), "studyB");
        assertEquals(node.get(1).get("name").textValue(), "Study C");
        assertEquals(node.get(1).get("identifier").textValue(), "studyC");
        assertEquals(node.get(2).get("name").textValue(), "Study D");
        assertEquals(node.get(2).get("identifier").textValue(), "studyD");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = ".*" + STUDY_ACCESS_EXCEPTION_MSG + ".*")
    public void getStudyMembershipsForNonAdminUsers() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL)
                .withEmailVerified(true)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        when(mockSession.getParticipant()).thenReturn(participant);
        doReturn(mockSession).when(controller).getAuthenticatedSession();

        controller.getStudyMemberships();
    }
    
    private Study mockStudy(String name, String identifier, boolean active) {
        Study study = Study.create();
        study.setName(name);
        study.setIdentifier(identifier);
        study.setActive(active);
        when(mockStudyService.getStudy(identifier)).thenReturn(study);
        return study;
    }
    
    private void testRoleAccessToCurrentStudy(Roles role) throws Exception {
        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        if (role != null) {
            builder.withRoles(ImmutableSet.of(role));
        }
        UserSession session = new UserSession(builder.build());
        session.setAuthenticated(true);
        session.setStudyIdentifier(TEST_APP_ID);
        doReturn(session).when(controller).getSessionIfItExists();
        
        Study result = controller.getCurrentStudy();
        assertEquals(result.getSupportEmail(), EMAIL_ADDRESS);        
    }
}
