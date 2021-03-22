package org.sagebionetworks.bridge.services;

import static org.mockito.AdditionalMatchers.not;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.apps.PasswordPolicy.DEFAULT_PASSWORD_POLICY;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.upload.UploadValidationStrictness.REPORT;
import static org.sagebionetworks.bridge.models.upload.UploadValidationStrictness.WARNING;
import static org.sagebionetworks.bridge.services.AppService.EXPORTER_SYNAPSE_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.MembershipInvitation;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.util.ModelConstants;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AppDao;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.apps.AppAndUsers;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.validators.AppAndUsersValidator;
import org.sagebionetworks.bridge.validators.AppValidator;

public class AppServiceTest extends Mockito {
    private static final long BRIDGE_ADMIN_TEAM_ID = 1357L;
    private static final long BRIDGE_STAFF_TEAM_ID = 2468L;
    private static final Long TEST_USER_ID = Long.parseLong("3348228"); // test user exists in synapse
    private static final String TEST_NAME_SCOPING_TOKEN = "qwerty";
    private static final String TEST_PROJECT_NAME = "Test App AppServiceTest Project " + TEST_NAME_SCOPING_TOKEN;
    private static final String TEST_TEAM_NAME = "Test App AppServiceTest Access Team " + TEST_NAME_SCOPING_TOKEN;
    private static final String TEST_TEAM_ID = "1234";
    private static final String TEST_PROJECT_ID = "synapseProjectId";

    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_SYNAPSE_ID = "synapse-id-1";
    private static final String TEST_USER_SYNAPSE_ID_2 = "synapse-id-2";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password12AB";
    private static final String TEST_IDENTIFIER = "test_identifier";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";
    private static final List<String> TEST_ADMIN_IDS = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);
    private static final Set<String> EMPTY_SET = ImmutableSet.of();
    private static final String SUPPORT_EMAIL = "bridgeit@sagebase.org";
    private static final String SYNAPSE_TRACKING_VIEW_ID = "synTrackingViewId";
    private static final String VERIFICATION_TOKEN = "dummy-token";
    private static final CacheKey VER_CACHE_KEY = CacheKey.verificationToken("dummy-token");
    private static final ByteArrayResource TEMPLATE_RESOURCE = new ByteArrayResource("<p>${url}</p>".getBytes());
    
    @Mock
    BridgeConfig mockBridgeConfig;
    @Mock
    CompoundActivityDefinitionService mockCompoundActivityDefinitionService;
    @Mock
    NotificationTopicService mockTopicService;
    @Mock
    SendMailService mockSendMailService;
    @Mock
    UploadCertificateService mockUploadCertService;
    @Mock
    AppDao mockAppDao;
    @Mock
    CacheProvider mockCacheProvider;
    @Mock
    SubpopulationService mockSubpopService;
    @Mock
    EmailVerificationService mockEmailVerificationService;
    @Mock
    ParticipantService mockParticipantService;
    @Mock
    AccessControlList mockAccessControlList;
    @Mock
    SynapseClient mockSynapseClient;
    @Mock
    TemplateService mockTemplateService;
    @Mock
    FileService mockFileService;
    @Mock
    OrganizationService mockOrgService;
    @Mock
    StudyService mockStudyService;

    @Captor
    ArgumentCaptor<Project> projectCaptor;
    @Captor
    ArgumentCaptor<Team> teamCaptor;
    @Captor
    ArgumentCaptor<App> appCaptor;
    @Captor
    ArgumentCaptor<Template> templateCaptor;
    @Captor
    ArgumentCaptor<Study> studyCaptor;
    @Captor
    ArgumentCaptor<Organization> orgCaptor;

    @Spy
    @InjectMocks
    AppService service;
    
    App app;
    Team team;
    Project project;
    MembershipInvitation teamMemberInvitation;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Mock config.
        when(mockBridgeConfig.get(AppService.CONFIG_KEY_SUPPORT_EMAIL_PLAIN)).thenReturn(SUPPORT_EMAIL);
        when(mockBridgeConfig.get(AppService.CONFIG_KEY_TEAM_BRIDGE_ADMIN))
                .thenReturn(String.valueOf(BRIDGE_ADMIN_TEAM_ID));
        when(mockBridgeConfig.get(AppService.CONFIG_KEY_TEAM_BRIDGE_STAFF))
                .thenReturn(String.valueOf(BRIDGE_STAFF_TEAM_ID));
        when(mockBridgeConfig.getPropertyAsList(AppService.CONFIG_APP_WHITELIST)).thenReturn(ImmutableList.of(API_APP_ID));
        when(mockBridgeConfig.get(AppService.CONFIG_KEY_SYNAPSE_TRACKING_VIEW)).thenReturn(SYNAPSE_TRACKING_VIEW_ID);
        service.setBridgeConfig(mockBridgeConfig); // this has to be set again after being mocked

        // Mock templates
        service.setAppEmailVerificationTemplateSubject(mockTemplateAsSpringResource(
                "Verify your app email"));
        service.setAppEmailVerificationTemplate(mockTemplateAsSpringResource(
                "Click here ${appEmailVerificationUrl} ${appEmailVerificationExpirationPeriod}" + 
                " ${studyEmailVerificationUrl} ${studyEmailVerificationExpirationPeriod}"));
        service.setValidator(new AppValidator());
        
        AppAndUsersValidator appAndUsersValidator = new AppAndUsersValidator();
        appAndUsersValidator.setSynapseClient(mockSynapseClient);
        service.setAppAndUsersValidator(appAndUsersValidator);

        when(service.getNameScopingToken()).thenReturn(TEST_NAME_SCOPING_TOKEN);
        
        app = getTestApp();
        app.setIdentifier(TEST_APP_ID);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        GuidVersionHolder keys = new GuidVersionHolder("guid", 1L);
        when(mockTemplateService.createTemplate(any(), any())).thenReturn(keys);
        
        when(mockAppDao.createApp(any())).thenAnswer(invocation -> {
            // Return the same app, except set version to 1.
            App app = invocation.getArgument(0);
            app.setVersion(1L);
            return app;
        });

        when(mockAppDao.updateApp(any())).thenAnswer(invocation -> {
            // Return the same app, except we increment the version.
            App app = invocation.getArgument(0);
            Long oldVersion = app.getVersion();
            app.setVersion(oldVersion != null ? oldVersion + 1 : 1);
            return app;
        });
        
        // Spy AppService.createTimeLimitedToken() to create a known token instead of a random one. This makes our
        // tests easier.
        doReturn(VERIFICATION_TOKEN).when(service).createTimeLimitedToken();

        // setup project and team
        team = new Team();
        project = new Project();
        project.setId(TEST_PROJECT_ID);
        team.setId(TEST_TEAM_ID);

        teamMemberInvitation = new MembershipInvitation();
        teamMemberInvitation.setInviteeId(TEST_USER_ID.toString());
        teamMemberInvitation.setTeamId(TEST_TEAM_ID);
    }

    private App getTestApp() {
        App app = TestUtils.getValidApp(AppServiceTest.class);
        app.setIdentifier(TEST_APP_ID);
        return app;
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAppExcludeDeleted() {
        app.setActive(false);
        service.getApp(TEST_APP_ID, false);
    }
    
    @Test
    public void getApps() {
        when(mockAppDao.getApps()).thenReturn(ImmutableList.of(app));
        
        List<App> results = service.getApps();
        assertSame(results.get(0), app);
        
        verify(mockAppDao).getApps();
    }

    @Test
    public void createAppSendsVerificationEmail() throws Exception {
        // Create app.
        App app = getTestApp();
        String consentNotificationEmail = app.getConsentNotificationEmail();

        // Execute. Verify app is created with ConsentNotificationEmailVerified=false.
        service.createApp(app);

        ArgumentCaptor<App> savedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppDao).createApp(savedAppCaptor.capture());

        App savedApp = savedAppCaptor.getValue();
        assertFalse(savedApp.isConsentNotificationEmailVerified());

        // Verify email verification email.
        verifyEmailVerificationEmail(consentNotificationEmail);
    }

    @Test
    public void updateAppConsentNotificationEmailSendsVerificationEmail() throws Exception {
        // Original app. ConsentNotificationEmailVerified is true.
        App originalApp = getTestApp();
        originalApp.setConsentNotificationEmailVerified(true);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(originalApp);

        // New app is the same as original app. Change consent notification email and app name.
        App newApp = getTestApp();
        newApp.setConsentNotificationEmail("different-email@example.com");
        newApp.setName("different-name");

        // Execute. Verify the consent email change and app name change. The verified flag should now be false.
        service.updateApp(newApp, false);

        ArgumentCaptor<App> savedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppDao).updateApp(savedAppCaptor.capture());

        App savedApp = savedAppCaptor.getValue();
        assertEquals(savedApp.getConsentNotificationEmail(), "different-email@example.com");
        assertFalse(savedApp.isConsentNotificationEmailVerified());
        assertEquals(savedApp.getName(), "different-name");

        // Verify email verification email.
        verifyEmailVerificationEmail("different-email@example.com");
    }

    private void verifyEmailVerificationEmail(String consentNotificationEmail) throws Exception {
        // Verify token in CacheProvider.
        ArgumentCaptor<String> verificationDataCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCacheProvider).setObject(eq(VER_CACHE_KEY), verificationDataCaptor.capture(),
                eq(AppService.VERIFY_APP_EMAIL_EXPIRE_IN_SECONDS));
        JsonNode verificationData = BridgeObjectMapper.get().readTree(verificationDataCaptor.getValue());
        assertEquals(verificationData.get("appId").textValue(), TEST_APP_ID);
        assertEquals(verificationData.get("email").textValue(), consentNotificationEmail);

        // Verify sent email.
        ArgumentCaptor<BasicEmailProvider> emailProviderCaptor = ArgumentCaptor.forClass(
                BasicEmailProvider.class);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());

        MimeTypeEmail email = emailProviderCaptor.getValue().getMimeTypeEmail();
        assertEquals(email.getType(), EmailType.VERIFY_CONSENT_EMAIL);
        String body = (String) email.getMessageParts().get(0).getContent();

        assertTrue(body.contains("/vse?appId="+ TEST_APP_ID + "&token=" +
                VERIFICATION_TOKEN + "&type=consent_notification"));
        assertTrue(email.getSenderAddress().contains(SUPPORT_EMAIL));
        assertEquals(emailProviderCaptor.getValue().getTokenMap().get("studyEmailVerificationExpirationPeriod"), "1 day");
        assertEquals(emailProviderCaptor.getValue().getTokenMap().get("appEmailVerificationExpirationPeriod"), "1 day");
        
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(recipientList.size(), 1);
        assertEquals(recipientList.get(0), consentNotificationEmail);
    }

    @Test
    public void updateAppWithSameConsentNotificationEmailDoesntSendVerification() {
        // Original app. ConsentNotificationEmailVerified is true.
        App originalApp = getTestApp();
        originalApp.setConsentNotificationEmailVerified(true);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(originalApp);

        // New app is the same as original app. Make some inconsequential change to the app name.
        App newApp = getTestApp();
        newApp.setName("different-name");
        newApp.setConsentNotificationEmailVerified(true);

        // Execute. Verify the app name change. Verified is still true.
        service.updateApp(newApp, false);

        ArgumentCaptor<App> savedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppDao).updateApp(savedAppCaptor.capture());

        App savedApp = savedAppCaptor.getValue();
        assertTrue(savedApp.isConsentNotificationEmailVerified());
        assertEquals(savedApp.getName(), "different-name");

        // Verify we don't send email.
        verify(mockSendMailService, never()).sendEmail(any());
    }

    @Test
    public void updateAppChangesNullConsentNotificationEmailVerifiedToTrue() {
        // For backwards-compatibility, we flip the verified=null flag to true. This only happens for older apps
        // that predate verification, most of which are confirmed working.
        updateAppConsentNotificationEmailVerified(null, null, true);
    }

    @Test
    public void updateAppCantFlipVerifiedFromFalseToTrue() {
        updateAppConsentNotificationEmailVerified(false, true, false);
    }

    @Test
    public void updateAppCanFlipVerifiedFromTrueToFalse() {
        updateAppConsentNotificationEmailVerified(true, false, false);
    }

    private void updateAppConsentNotificationEmailVerified(Boolean oldValue, Boolean newValue,
            Boolean expectedValue) {
        // Original app
        App oldApp = getTestApp();
        oldApp.setConsentNotificationEmailVerified(oldValue);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(oldApp);

        // New app
        App newApp = getTestApp();
        newApp.setConsentNotificationEmailVerified(newValue);

        // Update
        service.updateApp(newApp, false);

        // Verify result
        ArgumentCaptor<App> savedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppDao).updateApp(savedAppCaptor.capture());

        App savedApp = savedAppCaptor.getValue();
        assertEquals(savedApp.isConsentNotificationEmailVerified(), expectedValue);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void sendVerifyEmailNullType() throws Exception {
        service.sendVerifyEmail(TEST_APP_ID, null);
    }

    // This can be manually triggered through the API even though there's no consent
    // email to confirm... so return a 400 in this case.
    @Test(expectedExceptions = BadRequestException.class)
    public void sendVerifyEmailNoConsentEmail() throws Exception {
        App app = getTestApp();
        app.setConsentNotificationEmail(null);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        service.sendVerifyEmail(TEST_APP_ID, AppEmailType.CONSENT_NOTIFICATION);
    }
    
    @Test
    public void sendVerifyEmailSuccess() throws Exception {
        // Mock getApp().
        App app = getTestApp();
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute.
        service.sendVerifyEmail(TEST_APP_ID, AppEmailType.CONSENT_NOTIFICATION);

        // Verify email verification email.
        verifyEmailVerificationEmail(app.getConsentNotificationEmail());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullToken() {
        service.verifyEmail(TEST_APP_ID, null, AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailEmptyToken() {
        service.verifyEmail(TEST_APP_ID, "", AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailBlankToken() {
        service.verifyEmail(TEST_APP_ID, "   ", AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullType() {
        service.verifyEmail(TEST_APP_ID, VERIFICATION_TOKEN, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullVerificationData() {
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(null);
        service.verifyEmail(TEST_APP_ID, VERIFICATION_TOKEN, AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailMismatchedApp() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"appId\":\"wrong-app\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getApp().
        App app = getTestApp();
        app.setConsentNotificationEmail("correct-email@example.com");
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute. Will throw.
        service.verifyEmail(TEST_APP_ID, VERIFICATION_TOKEN, AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNoEmail() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"appId\":\"" + TEST_APP_ID + "\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getApp().
        App app = getTestApp();
        app.setConsentNotificationEmail(null);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute. Will throw.
        service.verifyEmail(TEST_APP_ID, VERIFICATION_TOKEN, AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailMismatchedEmail() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"appId\":\"" + TEST_APP_ID + "\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getApp().
        App app = getTestApp();
        app.setConsentNotificationEmail("wrong-email@example.com");
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute. Will throw.
        service.verifyEmail(TEST_APP_ID, VERIFICATION_TOKEN, AppEmailType.CONSENT_NOTIFICATION);
    }

    @Test
    public void verifyEmailSuccess() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"appId\":\"" + TEST_APP_ID + "\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getting the app from the cache.
        App app = getTestApp();
        app.setConsentNotificationEmail("correct-email@example.com");
        when(mockCacheProvider.getApp(TEST_APP_ID)).thenReturn(app);

        // Execute. Verify consentNotificationEmailVerified is now true.
        service.verifyEmail(TEST_APP_ID, VERIFICATION_TOKEN, AppEmailType.CONSENT_NOTIFICATION);

        ArgumentCaptor<App> savedAppCaptor = ArgumentCaptor.forClass(App.class);
        verify(mockAppDao).updateApp(savedAppCaptor.capture());

        App savedApp = savedAppCaptor.getValue();
        assertTrue(savedApp.isConsentNotificationEmailVerified());

        // Verify that we cached the app.
        verify(mockCacheProvider).setApp(savedApp);

        // Verify that we removed the used token.
        verify(mockCacheProvider).removeObject(VER_CACHE_KEY);
    }

    @Test
    public void cannotRemoveTaskIdentifiers() {
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(TEST_APP_ID);
        updatedApp.setTaskIdentifiers(Sets.newHashSet("task2", "different-tag"));
        
        try {
            service.updateApp(updatedApp, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals(e.getMessage(), "Task identifiers cannot be deleted.");
            assertEquals(e.getEntityKeys().get("identifier"), TEST_APP_ID);
            assertEquals(e.getEntityKeys().get("type"), "App");
        }
    }
    
    @Test
    public void cannotRemoveDataGroups() {
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);

        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(TEST_APP_ID);
        updatedApp.setDataGroups(Sets.newHashSet("beta_users", "different-tag"));
        
        try {
            service.updateApp(updatedApp, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals(e.getMessage(), "Data groups cannot be deleted.");
            assertEquals(e.getEntityKeys().get("identifier"), TEST_APP_ID);
            assertEquals(e.getEntityKeys().get("type"), "App");
        }
    }
    
    @Test
    public void cannotRemoveTaskIdentifiersEmptyLists() {
        app.setTaskIdentifiers(EMPTY_SET);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(TEST_APP_ID);
        updatedApp.setTaskIdentifiers(EMPTY_SET);
        
        service.updateApp(updatedApp, true);
    }
    
    @Test
    public void cannotRemoveDataGroupsEmptyLists() {
        app.setDataGroups(EMPTY_SET);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(TEST_APP_ID);
        updatedApp.setDataGroups(EMPTY_SET);
        
        service.updateApp(updatedApp, true);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class, expectedExceptionsMessageRegExp = "Activity event keys cannot be deleted.")
    public void cannotRemoveActivityEventKeys() {
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setIdentifier(TEST_APP_ID);
        app.getCustomEvents().put("test", FUTURE_ONLY);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(app.getIdentifier());
        updatedApp.setCustomEvents(null);
        
        service.updateApp(updatedApp, true);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class, expectedExceptionsMessageRegExp = "Default templates cannot be deleted.")
    public void cannotRemoveDefaultAppTemplates() {
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setIdentifier(TEST_APP_ID);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(app.getIdentifier());
        updatedApp.getDefaultTemplates().remove(EMAIL_ACCOUNT_EXISTS.name().toLowerCase());
        
        service.updateApp(updatedApp, true);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class, expectedExceptionsMessageRegExp = "Default templates cannot be deleted.")
    public void cannotNullDefaultAppTemplates() {
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setIdentifier(TEST_APP_ID);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(app.getIdentifier());
        updatedApp.setDefaultTemplates(null);
        
        service.updateApp(updatedApp, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getAppWithNullArgumentThrows() {
        service.getApp((String)null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getAppWithEmptyStringArgumentThrows() {
        service.getApp("");
    }
    
    @Test
    public void createAppWithoutConsentNotificationEmailDoesNotSendNotification() {
        App app = TestUtils.getValidApp(AppServiceTest.class);
        app.setConsentNotificationEmail(null);
        
        service.createApp(app);
        
        verify(mockSendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void createAppCreatesDefaultTemplates() {
        // Mock this to verify that defaults are set in app
        GuidVersionHolder keys = new GuidVersionHolder("oneGuid", 1L);
        when(mockTemplateService.createTemplate(any(), any())).thenReturn(keys);
        
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setIdentifier(TEST_APP_ID);
        app.setDefaultTemplates(ImmutableMap.of());
        
        service.createApp(app);
        
        int templateTypeNum = TemplateType.values().length;
        
        assertEquals(app.getDefaultTemplates().size(), templateTypeNum);
        
        verify(mockTemplateService, times(templateTypeNum)).createTemplate(eq(app), templateCaptor.capture());
        for (int i=0; i < templateTypeNum; i++) {
            TemplateType type = TemplateType.values()[i];
            Template template = templateCaptor.getAllValues().get(i);
            
            assertEquals(template.getTemplateType(), type);
            assertEquals(template.getName(), BridgeUtils.templateTypeToLabel(type));
            
            assertEquals(app.getDefaultTemplates().get(type.name().toLowerCase()), "oneGuid");
        }
    }
    
    @Test
    public void updateAppCallsTemplateMigrationService() {
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setIdentifier(TEST_APP_ID);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(app);
        
        App updatedApp = TestUtils.getValidApp(AppServiceTest.class);
        updatedApp.setIdentifier(TEST_APP_ID);
        
        service.updateApp(updatedApp, true);
    }
    
    @Test
    public void physicallyDeleteApp() {
        PagedResourceList<? extends Template> page1 = new PagedResourceList<>(
                ImmutableList.of(createTemplate("guid1"), createTemplate("guid2"), createTemplate("guid3")), 3);
        PagedResourceList<? extends Template> page2 = new PagedResourceList<>(ImmutableList.of(), 3);

        doReturn(page1, page2).when(mockTemplateService).getTemplatesForType(
                TEST_APP_ID, EMAIL_ACCOUNT_EXISTS, 0, 50, true);
        doReturn(page2).when(mockTemplateService).getTemplatesForType(eq(TEST_APP_ID), 
                not(eq(EMAIL_ACCOUNT_EXISTS)), eq(0), eq(50), eq(true));
        
        // execute
        service.deleteApp(TEST_APP_ID, true);

        // verify we called the correct dependent services
        verify(mockAppDao).deleteApp(app);
        verify(mockStudyService).deleteAllStudies(app.getIdentifier());
        verify(mockOrgService).deleteAllOrganizations(app.getIdentifier());
        verify(mockCompoundActivityDefinitionService).deleteAllCompoundActivityDefinitionsInApp(
                app.getIdentifier());
        verify(mockSubpopService).deleteAllSubpopulations(app.getIdentifier());
        verify(mockTopicService).deleteAllTopics(app.getIdentifier());
        verify(mockCacheProvider).removeApp(TEST_APP_ID);
        verify(mockTemplateService).deleteTemplatesForApp(TEST_APP_ID);
        verify(mockFileService).deleteAllAppFiles(TEST_APP_ID);
    }

    private Template createTemplate(String guid) {
        Template template = Template.create();
        template.setGuid(guid);
        return template;
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void deactivateAppAlreadyDeactivatedBefore() {
        App app = getTestApp();
        app.setActive(false);
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(app);

        service.deleteApp(app.getIdentifier(), false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deactivateAppNotFound() {
        // Basically, this test doesn't do much because getApp() will throw ENFE, not return null
        when(mockAppDao.getApp(app.getIdentifier())).thenThrow(new EntityNotFoundException(App.class));
        service.deleteApp(app.getIdentifier(), false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void nonAdminsCannotUpdateDeactivatedApp() {
        App app = getTestApp();
        app.setActive(false);
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(app);

        service.updateApp(app, false);

        verify(mockAppDao, never()).updateApp(any());
    }

    @Test
    public void updateUploadMetadataOldAppHasNoFields() {
        // old app
        App oldApp = getTestApp();
        oldApp.setUploadMetadataFieldDefinitions(null);
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(oldApp);

        // new app
        App newApp = getTestApp();
        newApp.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));

        // execute - no exception
        service.updateApp(newApp, false);
    }

    @Test
    public void updateUploadMetadataNewAppHasNoFields() {
        // old app
        App oldApp = getTestApp();
        oldApp.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(oldApp);

        // new app
        App newApp = getTestApp();
        newApp.setUploadMetadataFieldDefinitions(null);

        // execute - expect exception
        try {
            service.updateApp(newApp, false);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            assertEquals(ex.getMessage(),
                    "Non-admins cannot delete or modify upload metadata fields; affected fields: test-field");
        }
    }

    @Test
    public void updateUploadMetadataCanAddAndReorderFields() {
        // make fields for test
        UploadFieldDefinition reorderedField1 = new UploadFieldDefinition.Builder().withName("reoredered-field-1")
                .withType(UploadFieldType.INT).build();
        UploadFieldDefinition reorderedField2 = new UploadFieldDefinition.Builder().withName("reoredered-field-2")
                .withType(UploadFieldType.BOOLEAN).build();
        UploadFieldDefinition addedField = new UploadFieldDefinition.Builder().withName("added-field")
                .withType(UploadFieldType.TIMESTAMP).build();

        // old app
        App oldApp = getTestApp();
        oldApp.setUploadMetadataFieldDefinitions(ImmutableList.of(reorderedField1, reorderedField2));
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(oldApp);

        // new app
        App newApp = getTestApp();
        newApp.setUploadMetadataFieldDefinitions(ImmutableList.of(reorderedField2, reorderedField1, addedField));

        // execute - no exception
        service.updateApp(newApp, false);
    }

    @Test
    public void nonAdminCantDeleteOrModifyFields() {
        // make fields for test
        UploadFieldDefinition goodField = new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build();
        UploadFieldDefinition deletedField = new UploadFieldDefinition.Builder().withName("deleted-field")
                .withType(UploadFieldType.INLINE_JSON_BLOB).withMaxLength(10).build();
        UploadFieldDefinition modifiedFieldOld = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withMaxLength(10).build();
        UploadFieldDefinition modifiedlFieldNew = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withMaxLength(20).build();

        // old app
        App oldApp = getTestApp();
        oldApp.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, deletedField, modifiedFieldOld));
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(oldApp);

        // new app
        App newApp = getTestApp();
        newApp.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, modifiedlFieldNew));

        // execute - expect exception
        try {
            service.updateApp(newApp, false);
            fail("expected exception");
        } catch (UnauthorizedException ex) {
            assertEquals(ex.getMessage(), "Non-admins cannot delete or modify upload metadata fields; " +
                    "affected fields: deleted-field, modified-field");
        }
    }

    @Test
    public void adminCanDeleteOrModifyFields() {
        // make fields for test
        UploadFieldDefinition goodField = new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build();
        UploadFieldDefinition deletedField = new UploadFieldDefinition.Builder().withName("deleted-field")
                .withType(UploadFieldType.INLINE_JSON_BLOB).withMaxLength(10).build();
        UploadFieldDefinition modifiedFieldOld = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withMaxLength(10).build();
        UploadFieldDefinition modifiedlFieldNew = new UploadFieldDefinition.Builder().withName("modified-field")
                .withType(UploadFieldType.STRING).withMaxLength(20).build();

        // old app
        App oldApp = getTestApp();
        oldApp.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, deletedField, modifiedFieldOld));
        when(mockAppDao.getApp(TEST_APP_ID)).thenReturn(oldApp);

        // new app
        App newApp = getTestApp();
        newApp.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, modifiedlFieldNew));

        // execute - no exception
        service.updateApp(newApp, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void nonAdminsCannotSetActiveToFalse() {
        App originalApp = getTestApp();
        originalApp.setActive(true);
        when(mockAppDao.getApp(originalApp.getIdentifier())).thenReturn(originalApp);

        App app = getTestApp();
        app.setIdentifier(originalApp.getIdentifier());
        app.setActive(false);

        service.updateApp(app, false);

        verify(mockAppDao, never()).updateApp(any());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void adminCannotSetActiveToFalse() {
        App originalApp = getTestApp();
        originalApp.setActive(true);
        when(mockAppDao.getApp(originalApp.getIdentifier())).thenReturn(originalApp);

        App app = getTestApp();
        app.setIdentifier(originalApp.getIdentifier());
        app.setActive(false);

        service.updateApp(app, true);

        verify(mockAppDao, never()).updateApp(any());
    }

    @Test
    public void createAppAndUsers() throws SynapseException {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);
        app.setExternalIdRequiredOnSignup(false);
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();
        
        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();
        
        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        AppAndUsers mockAppAndUsers = new AppAndUsers(TEST_ADMIN_IDS, app, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);
        
        // stub out use of synapse client so we can validate it, not just ignore it.
        when(mockAccessControlList.getResourceAccess()).thenReturn(new HashSet<>());
        when(mockSynapseClient.createEntity(projectCaptor.capture())).thenReturn(project);
        when(mockSynapseClient.getACL(TEST_PROJECT_ID)).thenReturn(mockAccessControlList);
        when(mockSynapseClient.createTeam(teamCaptor.capture())).thenReturn(team);

        EntityView view = new EntityView();
        view.setScopeIds(new ArrayList<>());
        when(mockSynapseClient.getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class)).thenReturn(view);

        // stub
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);

        // execute
        service.createAppAndUsers(mockAppAndUsers);

        // verify
        verify(mockParticipantService).createParticipant(app, mockUser1, false);
        verify(mockParticipantService).createParticipant(app, mockUser2, false);
        verify(mockParticipantService, times(2)).requestResetPassword(app, mockIdentifierHolder.getIdentifier());
        
        verify(mockStudyService).createStudy(eq(TEST_APP_ID), studyCaptor.capture(), eq(false));
        
        Study capturedStudy = studyCaptor.getValue();
        assertEquals(capturedStudy.getAppId(), TEST_APP_ID);
        assertEquals(capturedStudy.getIdentifier(), TEST_APP_ID + "-study");
        assertEquals(capturedStudy.getName(), app.getName() + " Study");
        
        verify(service).createApp(app);
        verify(service).createSynapseProjectTeam(TEST_ADMIN_IDS,
                ImmutableList.of(TEST_USER_SYNAPSE_ID, TEST_USER_SYNAPSE_ID_2), app);
        
        assertEquals(projectCaptor.getValue().getName(), TEST_PROJECT_NAME);
        assertEquals(teamCaptor.getValue().getName(), TEST_TEAM_NAME);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*adminIds\\[0\\] is invalid.*")
    public void createAppAndUsersSynapseUserNotFound() throws SynapseException {
        when(mockSynapseClient.getUserProfile(any())).thenThrow(new SynapseNotFoundException());
        
        AppAndUsers mockAppAndUsers = new AppAndUsers(ImmutableList.of("bad-admin-id"), app, null);

        service.createAppAndUsers(mockAppAndUsers);
    }
    
    @Test
    public void createAppAndUsersDefaultsPasswordPolicy() throws SynapseException {
        app.setPasswordPolicy(null);
        app.setExternalIdRequiredOnSignup(false);
        app.setSynapseDataAccessTeamId(null);
        app.setSynapseProjectId(null);
        List<StudyParticipant> participants = ImmutableList.of(new StudyParticipant.Builder().withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID).withRoles(ImmutableSet.of(DEVELOPER)).build());

        IdentifierHolder holder = new IdentifierHolder("user-id");
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(holder);
        
        AccessControlList acl = new AccessControlList();
        acl.setResourceAccess(new HashSet<>());
        
        when(mockSynapseClient.createTeam(any())).thenReturn(team);
        when(mockSynapseClient.createEntity(any())).thenReturn(project);
        when(mockSynapseClient.getACL(any())).thenReturn(acl);

        EntityView view = new EntityView();
        view.setScopeIds(new ArrayList<>());
        when(mockSynapseClient.getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class)).thenReturn(view);

        AppAndUsers mockAppAndUsers = new AppAndUsers(ImmutableList.of("12345678"), app, participants);

        service.createAppAndUsers(mockAppAndUsers);
        
        verify(mockAppDao).createApp(appCaptor.capture());
        assertNotNull(appCaptor.getValue().getPasswordPolicy());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*users\\[0\\].roles can only have roles developer and/or researcher.*")
    public void createAppAndUsersUserWithWorkerRole() throws SynapseException {
        createAppAndUserInWrongRole(ImmutableSet.of(WORKER));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*users\\[0\\].roles can only have roles developer and/or researcher.*")
    public void createAppAndUsersUserWithSuperadminRole() throws SynapseException {
        createAppAndUserInWrongRole(ImmutableSet.of(SUPERADMIN));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*users\\[0\\].roles can only have roles developer and/or researcher.*")
    public void createAppAndUsersUserWithAdminRole() throws SynapseException {
        createAppAndUserInWrongRole(ImmutableSet.of(ADMIN));
    }
    
    private void createAppAndUserInWrongRole(Set<Roles> roles) throws SynapseException {
        app.setExternalIdRequiredOnSignup(false);
        app.setSynapseDataAccessTeamId(null);
        app.setSynapseProjectId(null);
        List<StudyParticipant> participants = ImmutableList.of(new StudyParticipant.Builder()
                .withSynapseUserId(TEST_USER_SYNAPSE_ID).withEmail(TEST_USER_EMAIL).withRoles(roles).build());
        
        AppAndUsers mockAppAndUsers = new AppAndUsers(ImmutableList.of("12345678"), app, participants);

        service.createAppAndUsers(mockAppAndUsers);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*users\\[0\\].roles should have at least one role.*")
    public void createAppAndUsersUserHasNoRole() throws SynapseException {
        app.setExternalIdRequiredOnSignup(false);
        app.setSynapseDataAccessTeamId(null);
        app.setSynapseProjectId(null);
        List<StudyParticipant> participants = ImmutableList.of(new StudyParticipant.Builder().withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID).build());
        
        AppAndUsers mockAppAndUsers = new AppAndUsers(ImmutableList.of("12345678"), app, participants);

        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test(expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*adminIds are required.*")
    public void createAppAndUsersWithNullAdmins() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        AppAndUsers mockAppAndUsers = new AppAndUsers(null, app, mockUsers);

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test (expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*adminIds are required.*")
    public void createAppAndUsersWithEmptyRoles() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of())
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1);
        AppAndUsers mockAppAndUsers = new AppAndUsers(null, app, mockUsers);

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test (expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*adminIds are required.*")
    public void createAppAndUsersWithEmptyAdmins() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        AppAndUsers mockAppAndUsers = new AppAndUsers(ImmutableList.of(), app, mockUsers);

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test (expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*users are required.*")
    public void createAppAndUsersWithEmptyUser() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        List<StudyParticipant> mockUsers = new ArrayList<>();
        AppAndUsers mockAppAndUsers = new AppAndUsers(TEST_ADMIN_IDS, app, mockUsers);

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test (expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*users are required.*")
    public void createAppAndUsersWithNullUser() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);
        
        AppAndUsers mockAppAndUsers = new AppAndUsers(TEST_ADMIN_IDS, app, null);

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test (expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*app cannot be null.*")
    public void createAppAndUsersWithNullApp() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withSynapseUserId(TEST_USER_SYNAPSE_ID_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        AppAndUsers mockAppAndUsers = new AppAndUsers(TEST_ADMIN_IDS, null, mockUsers);

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class, expectedExceptionsMessageRegExp = "App already has a project ID.")
    public void createAppAndUsersProjectIdExists() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseDataAccessTeamId(null);
        app.setExternalIdRequiredOnSignup(false);
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withEmail(TEST_USER_EMAIL)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .build();

        when(mockParticipantService.createParticipant(any(), any(), anyBoolean()))
                .thenReturn(new IdentifierHolder("userId"));
        
        AppAndUsers mockAppAndUsers = new AppAndUsers(TEST_ADMIN_IDS, app, ImmutableList.of(mockUser1));

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }    
    
    @Test
    public void createSynapseProjectTeam() throws SynapseException {
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        AccessControlList mockAcl = new AccessControlList();
        AccessControlList mockTeamAcl = new AccessControlList();
        mockAcl.setResourceAccess(new HashSet<>());
        mockTeamAcl.setResourceAccess(new HashSet<>());

        // pre-setup
        when(mockSynapseClient.createTeam(any())).thenReturn(team);
        when(mockSynapseClient.createEntity(any())).thenReturn(project);
        when(mockSynapseClient.getACL(any())).thenReturn(mockAcl);

        EntityView view = new EntityView();
        view.setScopeIds(new ArrayList<>());
        when(mockSynapseClient.getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class)).thenReturn(view);

        // execute
        App retApp = service.createSynapseProjectTeam(ImmutableList.of(TEST_USER_ID.toString()), 
                ImmutableList.of(TEST_USER_SYNAPSE_ID, TEST_USER_SYNAPSE_ID_2), app);
        
        // verify
        // create project and team
        verify(mockSynapseClient).createTeam(any());
        verify(mockSynapseClient).createEntity(any());
        // get project acl
        verify(mockSynapseClient).getACL(eq(TEST_PROJECT_ID));

        // update project acl
        ArgumentCaptor<AccessControlList> argumentProjectAcl = ArgumentCaptor.forClass(AccessControlList.class);
        verify(mockSynapseClient).updateACL(argumentProjectAcl.capture());
        AccessControlList capturedProjectAcl = argumentProjectAcl.getValue();
        Set<ResourceAccess> capturedProjectAclSet = capturedProjectAcl.getResourceAccess();
        assertEquals(capturedProjectAclSet.size(), 5);
        Map<Long, ResourceAccess> principalIdToAcl = Maps.uniqueIndex(capturedProjectAclSet,
                ResourceAccess::getPrincipalId);
        assertEquals(principalIdToAcl.size(), 5);

        // 1. Exporter (admin)
        ResourceAccess capturedExporterRa = principalIdToAcl.get(Long.valueOf(EXPORTER_SYNAPSE_USER_ID));
        assertEquals(capturedExporterRa.getAccessType(), ModelConstants.ENTITY_ADMIN_ACCESS_PERMISSIONS);

        // 2. Bridge Admin
        ResourceAccess bridgeAdminAcl = principalIdToAcl.get(BRIDGE_ADMIN_TEAM_ID);
        assertEquals(bridgeAdminAcl.getAccessType(), ModelConstants.ENTITY_ADMIN_ACCESS_PERMISSIONS);

        // 3. Specified admin user
        ResourceAccess capturedUserRa = principalIdToAcl.get(TEST_USER_ID);
        assertEquals(capturedUserRa.getAccessType(), ModelConstants.ENTITY_ADMIN_ACCESS_PERMISSIONS);

        // 4. Bridge Staff
        ResourceAccess bridgeStaffAcl = principalIdToAcl.get(BRIDGE_STAFF_TEAM_ID);
        assertEquals(bridgeStaffAcl.getAccessType(), AppService.READ_DOWNLOAD_ACCESS);

        // 5. Created data access team.
        ResourceAccess capturedTeamRa = principalIdToAcl.get(Long.valueOf(TEST_TEAM_ID));
        assertEquals(capturedTeamRa.getAccessType(), AppService.READ_DOWNLOAD_ACCESS);

        // Add project to tracking view. We truncate the "syn" from the project ID.
        verify(mockSynapseClient).putEntity(view);
        assertEquals(view.getScopeIds().size(), 1);
        assertEquals(view.getScopeIds().get(0), "apseProjectId");

        // invite users to team
        verify(mockSynapseClient, times(3)).createMembershipInvitation(any(), eq(null), eq(null));
        verify(mockSynapseClient).setTeamMemberPermissions(TEST_TEAM_ID, Long.toString(TEST_USER_ID), true);
        verify(mockSynapseClient).setTeamMemberPermissions(TEST_TEAM_ID, TEST_USER_SYNAPSE_ID, false);
        verify(mockSynapseClient).setTeamMemberPermissions(TEST_TEAM_ID, TEST_USER_SYNAPSE_ID_2, false);
        
        // update app
        assertNotNull(retApp);
        assertEquals(retApp.getIdentifier(), app.getIdentifier());
        assertEquals(retApp.getName(), app.getName());
        assertEquals(retApp.getSynapseProjectId(), TEST_PROJECT_ID);
        assertEquals(retApp.getSynapseDataAccessTeamId().toString(), TEST_TEAM_ID);
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class, expectedExceptionsMessageRegExp = "App already has a team ID.")
    public void createSynapseProjectTeamAccessTeamIdExists() throws SynapseException {
        // mock
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setExternalIdRequiredOnSignup(false);
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withSynapseUserId(TEST_USER_SYNAPSE_ID)
                .withEmail(TEST_USER_EMAIL)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .build();

        when(mockParticipantService.createParticipant(any(), any(), anyBoolean()))
                .thenReturn(new IdentifierHolder("userId"));
        
        AppAndUsers mockAppAndUsers = new AppAndUsers(TEST_ADMIN_IDS, app, ImmutableList.of(mockUser1));

        // execute
        service.createAppAndUsers(mockAppAndUsers);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamNullAppName() throws Exception {
        // mock
        App app = getTestApp();
        app.setExternalIdRequiredOnSignup(false);
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);
        app.setName(null); // This is not a good name...

        service.createSynapseProjectTeam(ImmutableList.of(TEST_IDENTIFIER), app);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamBadAppName() throws Exception {
        // mock
        App app = getTestApp();
        app.setExternalIdRequiredOnSignup(false);
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);
        app.setName("# # "); // This is not a good name...

        service.createSynapseProjectTeam(ImmutableList.of(TEST_IDENTIFIER), app);
    }


    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamNonExistUserID() throws SynapseException {
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        // pre-setup
        when(mockSynapseClient.getUserProfile(any())).thenThrow(SynapseNotFoundException.class);

        // execute
        service.createSynapseProjectTeam(ImmutableList.of(TEST_USER_ID.toString()), app);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamNullUserID() throws SynapseException {
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        // execute
        service.createSynapseProjectTeam(null, app);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamEmptyUserID() throws SynapseException {
        App app = getTestApp();
        app.setSynapseProjectId(null);
        app.setSynapseDataAccessTeamId(null);

        // execute
        service.createSynapseProjectTeam(ImmutableList.of(), app);
    }

    @Test
    public void addProjectToTrackingView_ViewIdNotSpecified() throws Exception {
        // Set up.
        when(mockBridgeConfig.get(AppService.CONFIG_KEY_SYNAPSE_TRACKING_VIEW)).thenReturn(null);
        service.setBridgeConfig(mockBridgeConfig);

        // Execute and verify.
        service.addProjectToTrackingView(TEST_PROJECT_ID);
        verify(mockSynapseClient, never()).getEntity(any(), any());
        verify(mockSynapseClient, never()).putEntity(any());
    }

    @Test
    public void addProjectToTrackingView_GetThrows() throws Exception {
        // Set up.
        when(mockSynapseClient.getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class))
                .thenThrow(SynapseNotFoundException.class);

        // Execute. The exception is swallowed, and the Put is never called.
        service.addProjectToTrackingView(TEST_PROJECT_ID);
        verify(mockSynapseClient).getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class);
        verify(mockSynapseClient, never()).putEntity(any());
    }

    @Test
    public void addProjectToTrackingView_PutThrows() throws Exception {
        // Set up.
        EntityView view = new EntityView();
        view.setScopeIds(new ArrayList<>());
        when(mockSynapseClient.getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class)).thenReturn(view);

        when(mockSynapseClient.putEntity(view)).thenThrow(SynapseNotFoundException.class);

        // Execute. The exception is swallowed.
        service.addProjectToTrackingView(TEST_PROJECT_ID);
        verify(mockSynapseClient).getEntity(SYNAPSE_TRACKING_VIEW_ID, EntityView.class);
        verify(mockSynapseClient).putEntity(view);
    }

    @Test
    public void newAppVerifiesSupportEmail() {
        App app = getTestApp();
        when(mockEmailVerificationService.verifyEmailAddress(app.getSupportEmail()))
                .thenReturn(EmailVerificationStatus.PENDING);

        service.createApp(app);

        verify(mockEmailVerificationService).verifyEmailAddress(app.getSupportEmail());
        assertTrue(app.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP));
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createAppChecksForExistingIdentifier() {
        App app = getTestApp();
        
        // already exists under the same ID.
        when(mockAppDao.doesIdentifierExist(app.getIdentifier())).thenReturn(true);
        
        service.createApp(app);
    }
    
    // This would be destructive
    @Test
    public void createAppDoesNotCreateCertsForWhitelistedStudies() {
        App app = getTestApp();
        app.setIdentifier(API_APP_ID); // the only Id in the mock whitelist
        
        service.createApp(app);
        
        verify(mockUploadCertService, never()).createCmsKeyPair(any());
    }
    
    @Test
    public void updatingAppVerifiesSupportEmail() throws Exception {
        App app = getTestApp();
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(app);

        // We need to copy app in order to set support email and have it be different than
        // the mock version returned from the database
        App newApp = BridgeObjectMapper.get().readValue(
                BridgeObjectMapper.get().writeValueAsString(app), App.class);
        newApp.setSupportEmail("foo@foo.com"); // it's new and must be verified.
        
        service.updateApp(newApp, false);
        verify(mockEmailVerificationService).verifyEmailAddress("foo@foo.com");
    }

    @Test
    public void updatingAppNoChangeInSupportEmailDoesNotVerifyEmail() {
        App app = getTestApp();
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(app);
        
        service.updateApp(app, false);
        verify(mockEmailVerificationService, never()).verifyEmailAddress(any());
    }
    
    @Test
    public void updateAppCorrectlyDetectsEmailChangesInvolvingNulls() {
        // consent email still correctly detected
        String originalEmail = TestUtils.getValidApp(AppServiceTest.class).getConsentNotificationEmail();
        String newEmail = "changed@changed.com";
        
        setupConsentEmailChangeTest(null, null, false, false);
        setupConsentEmailChangeTest(originalEmail, originalEmail, false, false);
        setupConsentEmailChangeTest(null, newEmail, true, true);
        setupConsentEmailChangeTest(originalEmail, null, true, false);
        setupConsentEmailChangeTest(originalEmail, newEmail, true, true);
    }
    
    private void setupConsentEmailChangeTest(String originalEmail, String newEmail, boolean shouldBeChanged,
            boolean expectedSendEmail) {
        reset(mockSendMailService);
        App original = TestUtils.getValidApp(AppServiceTest.class);
        original.setConsentNotificationEmail(originalEmail);
        when(mockAppDao.getApp(any())).thenReturn(original);
        
        App update = TestUtils.getValidApp(AppServiceTest.class);
        update.setConsentNotificationEmail(newEmail);
        // just assume this is true for the test so defaults aren't set
        update.setConsentNotificationEmailVerified(true);
        
        service.updateApp(update, true);
        
        if (expectedSendEmail) {
            verify(mockSendMailService).sendEmail(any());
        } else {
            verify(mockSendMailService, never()).sendEmail(any());
        }
        if (shouldBeChanged) {
            assertFalse(update.isConsentNotificationEmailVerified());
        } else {
            assertTrue(update.isConsentNotificationEmailVerified());
        }
    }

    private static Resource mockTemplateAsSpringResource(String content) throws Exception {
        byte[] contentBytes = content.getBytes(Charsets.UTF_8);
        Resource mockResource = mock(Resource.class);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream(contentBytes));
        return mockResource;
    }
    
    // Tests from the Play-based AppServiceTest.java in BridgePF
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void appIsValidated() {
        App testApp = new DynamoApp();
        testApp.setName("Belgian Waffles [Test]");
        service.createApp(testApp);
    }

    @Test
    public void cannotCreateAnExistingAppWithAVersion() {
        app = TestUtils.getValidApp(AppServiceTest.class);
        app = service.createApp(app);
        try {
            app = service.createApp(app);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            // expected exception
        }
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void cannotCreateAnAppWithAVersion() {
        App testApp = TestUtils.getValidApp(AppServiceTest.class);
        testApp.setVersion(1L);
        service.createApp(testApp);
    }

    /**
     * From the non-mock tests, this test is probably redundant with other test, but is kept 
     * here.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void crudApp() {
        when(mockTemplateService.getTemplatesForType(any(), any(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        // developer
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        app = TestUtils.getValidApp(AppServiceTest.class);
        // verify this can be null, that's okay, and the flags are reset correctly on create
        app.setConsentNotificationEmailVerified(true);
        app.setReauthenticationEnabled(null);
        app.setAppIdExcludedInExport(false);
        app.setTaskIdentifiers(null);
        app.setUploadValidationStrictness(null);
        app.setActivityEventKeys(null);
        app.setCustomEvents(null);
        app.setHealthCodeExportEnabled(true);
        app.setActive(false);
        app.setStrictUploadValidationEnabled(false);
        app.setEmailVerificationEnabled(false);
        app.setEmailSignInEnabled(true);
        app.setPhoneSignInEnabled(true);
        
        app = service.createApp(app);

        // Verify that the flags are set correctly on create.
        assertFalse(app.isConsentNotificationEmailVerified());
        assertNotNull(app.getVersion(), "Version has been set");
        assertTrue(app.isActive());
        assertTrue(app.isReauthenticationEnabled());
        assertFalse(app.isStrictUploadValidationEnabled());
        assertTrue(app.isAppIdExcludedInExport());
        assertEquals(app.getUploadValidationStrictness(), REPORT);

        verify(mockCacheProvider).setApp(app);
        
        ArgumentCaptor<Study> studyCaptor = ArgumentCaptor.forClass(Study.class);
        
        // A default, active consent should be created for the app.
        verify(mockSubpopService).createDefaultSubpopulation(eq(app), studyCaptor.capture());
        
        verify(mockStudyService).createStudy(eq(app.getIdentifier()), studyCaptor.capture(), eq(false));
        Study defaultStudy = studyCaptor.getValue();
        assertEquals(defaultStudy.getAppId(), app.getIdentifier());
        assertEquals(defaultStudy.getIdentifier(), app.getIdentifier() + "-study");
        assertEquals(defaultStudy.getName(), "Test App [AppServiceTest] Study");

        verify(mockSubpopService).createDefaultSubpopulation(app, defaultStudy);
        
        verify(mockAppDao).createApp(appCaptor.capture());

        App newApp = appCaptor.getValue();
        assertTrue(newApp.isActive());
        assertFalse(newApp.isStrictUploadValidationEnabled());
        assertTrue(newApp.isAppIdExcludedInExport());
        assertEquals(UploadValidationStrictness.REPORT, newApp.getUploadValidationStrictness());

        assertEquals(newApp.getIdentifier(), app.getIdentifier());
        assertEquals(newApp.getName(), "Test App [AppServiceTest]");
        assertEquals(newApp.getMinAgeOfConsent(), 18);
        assertEquals(newApp.getDataGroups(), ImmutableSet.of("beta_users", "production_users", TEST_USER_GROUP));
        assertTrue(newApp.getTaskIdentifiers().isEmpty());
        assertTrue(newApp.getActivityEventKeys().isEmpty());
        assertTrue(newApp.getCustomEvents().isEmpty());

        verify(mockCacheProvider).setApp(newApp);

        // make some (non-admin) updates, these should change
        newApp.setConsentNotificationEmailVerified(true);
        newApp.setStrictUploadValidationEnabled(true);
        newApp.setUploadValidationStrictness(WARNING);
        
        assertEquals(studyCaptor.getValue().getIdentifier(), app.getIdentifier() + "-study");
        
        when(mockAppDao.getApp(newApp.getIdentifier())).thenReturn(newApp);
        App updatedApp = service.updateApp(newApp, false);
        
        assertTrue(updatedApp.isConsentNotificationEmailVerified());
        assertTrue(updatedApp.isStrictUploadValidationEnabled());
        assertEquals(updatedApp.getUploadValidationStrictness(), WARNING);

        verify(mockCacheProvider).removeApp(updatedApp.getIdentifier());
        verify(mockCacheProvider, times(2)).setApp(updatedApp);

        // delete app
        reset(mockCacheProvider);
        service.deleteApp(app.getIdentifier(), true);
        
        verify(mockCacheProvider).getApp(app.getIdentifier());
        verify(mockCacheProvider).setApp(updatedApp);
        verify(mockCacheProvider).removeApp(app.getIdentifier());

        verify(mockAppDao).deleteApp(updatedApp);
        verify(mockCompoundActivityDefinitionService)
                .deleteAllCompoundActivityDefinitionsInApp(updatedApp.getIdentifier());
        verify(mockSubpopService).deleteAllSubpopulations(updatedApp.getIdentifier());
        verify(mockTopicService).deleteAllTopics(updatedApp.getIdentifier());
    }

    @Test
    public void canUpdatePasswordPolicyAndTemplates() throws Exception {
        // service need the defaults injected for this test...
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setPasswordPolicy(null);

        app = service.createApp(app);

        // First, verify that defaults are set...
        PasswordPolicy policy = app.getPasswordPolicy();
        assertNotNull(policy);
        assertEquals(policy.getMinLength(), 8);
        assertTrue(policy.isNumericRequired());
        assertTrue(policy.isSymbolRequired());
        assertTrue(policy.isUpperCaseRequired());

        // You have to mock this for the update
        App existingApp = TestUtils.getValidApp(AppServiceTest.class);
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(existingApp);
        app.setPasswordPolicy(new PasswordPolicy(6, true, false, false, true));
        
        app = service.updateApp(app, true);
        
        policy = app.getPasswordPolicy();
        assertTrue(app.isEmailVerificationEnabled());
        assertTrue(app.isAutoVerificationPhoneSuppressed());

        assertEquals(policy.getMinLength(), 6);
        assertTrue(policy.isNumericRequired());
        assertFalse(policy.isSymbolRequired());
        assertFalse(policy.isLowerCaseRequired());
        assertTrue(policy.isUpperCaseRequired());
    }

    @Test
    public void defaultsAreUsedWhenNotProvided() throws Exception {
        service.setAppEmailVerificationTemplate(TEMPLATE_RESOURCE);
        service.setAppEmailVerificationTemplateSubject(TEMPLATE_RESOURCE);
        
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setPasswordPolicy(null);
        app = service.createApp(app);
        
        assertEquals(DEFAULT_PASSWORD_POLICY, app.getPasswordPolicy());
        assertNotNull(app.getPasswordPolicy());
        
        // Remove them and update... we are set back to defaults
        app.setPasswordPolicy(null);
        
        // You have to mock this for the update
        App existingApp = TestUtils.getValidApp(AppServiceTest.class);
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(existingApp);
        
        app = service.updateApp(app, false);
        assertNotNull(app.getPasswordPolicy());
    }

    @Test
    public void adminsCanChangeSomeValuesResearchersCannot() {
        app = TestUtils.getValidApp(AppServiceTest.class);
        app.setAppIdExcludedInExport(true);
        app.setEmailVerificationEnabled(true);
        app.setExternalIdRequiredOnSignup(false);
        app.setEmailSignInEnabled(false);
        app.setPhoneSignInEnabled(false);
        app.setReauthenticationEnabled(false);
        app.setAccountLimit(0);
        app.setVerifyChannelOnSignInEnabled(false);

        App existing = TestUtils.getValidApp(AppServiceTest.class);
        existing.setExternalIdRequiredOnSignup(false);
        existing.setEmailSignInEnabled(false);
        existing.setPhoneSignInEnabled(false);
        existing.setReauthenticationEnabled(false);
        assertAppDefaults(existing);
        when(mockAppDao.getApp(app.getIdentifier())).thenReturn(existing);
        
        // Cannot be changed on create
        app = service.createApp(app);
        assertAppDefaults(app); // still set to defaults
        
        // Researchers cannot change these through update
        changeAppDefaults(app);
        app = service.updateApp(app, false);
        assertAppDefaults(app); // nope
        
        // But administrators can change these
        changeAppDefaults(app);
        app = service.updateApp(app, true);
        // These values have all successfully been changed from the defaults
        assertFalse(app.isAppIdExcludedInExport());
        assertFalse(app.isEmailVerificationEnabled());
        assertFalse(app.isVerifyChannelOnSignInEnabled());
        assertTrue(app.isAutoVerificationPhoneSuppressed());
        assertTrue(app.isExternalIdRequiredOnSignup());
        assertTrue(app.isEmailSignInEnabled());
        assertTrue(app.isPhoneSignInEnabled());
        assertTrue(app.isReauthenticationEnabled());
        assertEquals(app.getAccountLimit(), 10);
    }

    private void assertAppDefaults(App app) {
        assertTrue(app.isAppIdExcludedInExport());
        assertTrue(app.isEmailVerificationEnabled());
        assertTrue(app.isVerifyChannelOnSignInEnabled());
        assertFalse(app.isExternalIdRequiredOnSignup());
        assertFalse(app.isEmailSignInEnabled());
        assertFalse(app.isPhoneSignInEnabled());
        assertFalse(app.isReauthenticationEnabled());
        assertEquals(app.getAccountLimit(), 0);
    }
    
    private void changeAppDefaults(App app) {
        app.setAppIdExcludedInExport(false);
        app.setEmailVerificationEnabled(false);
        app.setVerifyChannelOnSignInEnabled(false);
        app.setExternalIdRequiredOnSignup(true);
        app.setEmailSignInEnabled(true);
        app.setPhoneSignInEnabled(true);
        app.setReauthenticationEnabled(true);
        app.setAccountLimit(10);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void cantDeleteApiApp() {
        service.deleteApp(API_APP_ID, true);
    }

}
