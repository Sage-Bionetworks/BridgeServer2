package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.models.studies.PasswordPolicy.DEFAULT_PASSWORD_POLICY;
import static org.sagebionetworks.bridge.models.upload.UploadValidationStrictness.REPORT;
import static org.sagebionetworks.bridge.models.upload.UploadValidationStrictness.WARNING;
import static org.sagebionetworks.bridge.services.StudyService.EXPORTER_SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.services.StudyService.SYNAPSE_REGISTER_END_POINT;
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
import org.sagebionetworks.client.exceptions.SynapseClientException;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.client.exceptions.SynapseNotFoundException;
import org.sagebionetworks.client.exceptions.UnknownSynapseServerException;
import org.sagebionetworks.repo.model.AccessControlList;
import org.sagebionetworks.repo.model.MembershipInvitation;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.ResourceAccess;
import org.sagebionetworks.repo.model.Team;
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
import org.sagebionetworks.bridge.dao.StudyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyAndUsers;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.validators.StudyValidator;

public class StudyServiceMockTest extends Mockito {
    private static final long BRIDGE_ADMIN_TEAM_ID = 1357L;
    private static final long BRIDGE_STAFF_TEAM_ID = 2468L;
    private static final Long TEST_USER_ID = Long.parseLong("3348228"); // test user exists in synapse
    private static final String TEST_NAME_SCOPING_TOKEN = "qwerty";
    private static final String TEST_PROJECT_NAME = "Test Study StudyServiceMockTest Project " + TEST_NAME_SCOPING_TOKEN;
    private static final String TEST_TEAM_NAME = "Test Study StudyServiceMockTest Access Team " + TEST_NAME_SCOPING_TOKEN;
    private static final String TEST_TEAM_ID = "1234";
    private static final String TEST_PROJECT_ID = "synapseProjectId";

    // Don't use TestConstants.TEST_STUDY since this conflicts with the whitelist.
    private static final String TEST_STUDY_ID = "test-study";
    private static final StudyIdentifier TEST_STUDY_IDENTIFIER = new StudyIdentifierImpl(TEST_STUDY_ID);

    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password12AB";
    private static final String TEST_IDENTIFIER = "test_identifier";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";
    private static final List<String> TEST_ADMIN_IDS = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);
    private static final Set<String> EMPTY_SET = ImmutableSet.of();
    private static final String SUPPORT_EMAIL = "bridgeit@sagebase.org";
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
    StudyDao mockStudyDao;
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
    
    @Captor
    ArgumentCaptor<Project> projectCaptor;
    @Captor
    ArgumentCaptor<Team> teamCaptor;
    @Captor
    ArgumentCaptor<Study> studyCaptor;

    @Spy
    @InjectMocks
    StudyService service;
    
    Study study;
    Team team;
    Project project;
    MembershipInvitation teamMemberInvitation;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Mock config.
        when(mockBridgeConfig.get(StudyService.CONFIG_KEY_SUPPORT_EMAIL_PLAIN)).thenReturn(SUPPORT_EMAIL);
        when(mockBridgeConfig.get(StudyService.CONFIG_KEY_TEAM_BRIDGE_ADMIN))
                .thenReturn(String.valueOf(BRIDGE_ADMIN_TEAM_ID));
        when(mockBridgeConfig.get(StudyService.CONFIG_KEY_TEAM_BRIDGE_STAFF))
                .thenReturn(String.valueOf(BRIDGE_STAFF_TEAM_ID));
        when(mockBridgeConfig.getPropertyAsList(StudyService.CONFIG_STUDY_WHITELIST)).thenReturn(ImmutableList.of("api"));
        service.setBridgeConfig(mockBridgeConfig); // this has to be set again after being mocked

        // Mock templates
        service.setStudyEmailVerificationTemplateSubject(mockTemplateAsSpringResource(
                "Verify your study email"));
        service.setStudyEmailVerificationTemplate(mockTemplateAsSpringResource(
                "Click here ${studyEmailVerificationUrl} ${studyEmailVerificationExpirationPeriod}"));
        service.setSignedConsentTemplateSubject(mockTemplateAsSpringResource("subject"));
        service.setSignedConsentTemplate(mockTemplateAsSpringResource("Test this"));
        service.setValidator(new StudyValidator());

        when(service.getNameScopingToken()).thenReturn(TEST_NAME_SCOPING_TOKEN);
        
        study = getTestStudy();
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        when(mockStudyDao.createStudy(any())).thenAnswer(invocation -> {
            // Return the same study, except set version to 1.
            Study study = invocation.getArgument(0);
            study.setVersion(1L);
            return study;
        });

        when(mockStudyDao.updateStudy(any())).thenAnswer(invocation -> {
            // Return the same study, except we increment the version.
            Study study = invocation.getArgument(0);
            Long oldVersion = study.getVersion();
            study.setVersion(oldVersion != null ? oldVersion + 1 : 1);
            return study;
        });
        
        // Also set the service default message strings from this study, for tests where we clear these
        service.setResetPasswordSmsTemplate(study.getResetPasswordSmsTemplate().getMessage());
        service.setPhoneSignInSmsTemplate(study.getPhoneSignInSmsTemplate().getMessage());
        service.setAppInstallLinkSmsTemplate(study.getAppInstallLinkSmsTemplate().getMessage());
        service.setVerifyPhoneSmsTemplate(study.getVerifyPhoneSmsTemplate().getMessage());
        service.setAccountExistsSmsTemplate(study.getAccountExistsSmsTemplate().getMessage());
        service.setSignedConsentSmsTemplate(study.getSignedConsentSmsTemplate().getMessage());

        // Spy StudyService.createTimeLimitedToken() to create a known token instead of a random one. This makes our
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

    private Study getTestStudy() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setIdentifier(TEST_STUDY_ID);
        return study;
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getStudyExcludeDeleted() {
        study.setActive(false);
        service.getStudy(TEST_STUDY_ID, false);
    }
    
    @Test
    public void getStudies() {
        when(mockStudyDao.getStudies()).thenReturn(ImmutableList.of(study));
        
        List<Study> results = service.getStudies();
        assertSame(results.get(0), study);
        
        verify(mockStudyDao).getStudies();
    }

    @Test
    public void createStudySendsVerificationEmail() throws Exception {
        // Create study.
        Study study = getTestStudy();
        String consentNotificationEmail = study.getConsentNotificationEmail();

        // Execute. Verify study is created with ConsentNotificationEmailVerified=false.
        service.createStudy(study);

        ArgumentCaptor<Study> savedStudyCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyDao).createStudy(savedStudyCaptor.capture());

        Study savedStudy = savedStudyCaptor.getValue();
        assertFalse(savedStudy.isConsentNotificationEmailVerified());

        // Verify email verification email.
        verifyEmailVerificationEmail(consentNotificationEmail);
    }

    @Test
    public void updateStudyConsentNotificationEmailSendsVerificationEmail() throws Exception {
        // Original study. ConsentNotificationEmailVerified is true.
        Study originalStudy = getTestStudy();
        originalStudy.setConsentNotificationEmailVerified(true);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(originalStudy);

        // New study is the same as original study. Change consent notification email and study name.
        Study newStudy = getTestStudy();
        newStudy.setConsentNotificationEmail("different-email@example.com");
        newStudy.setName("different-name");

        // Execute. Verify the consent email change and study name change. The verified flag should now be false.
        service.updateStudy(newStudy, false);

        ArgumentCaptor<Study> savedStudyCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyDao).updateStudy(savedStudyCaptor.capture());

        Study savedStudy = savedStudyCaptor.getValue();
        assertEquals(savedStudy.getConsentNotificationEmail(), "different-email@example.com");
        assertFalse(savedStudy.isConsentNotificationEmailVerified());
        assertEquals(savedStudy.getName(), "different-name");

        // Verify email verification email.
        verifyEmailVerificationEmail("different-email@example.com");
    }

    private void verifyEmailVerificationEmail(String consentNotificationEmail) throws Exception {
        // Verify token in CacheProvider.
        ArgumentCaptor<String> verificationDataCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCacheProvider).setObject(eq(VER_CACHE_KEY), verificationDataCaptor.capture(),
                eq(StudyService.VERIFY_STUDY_EMAIL_EXPIRE_IN_SECONDS));
        JsonNode verificationData = BridgeObjectMapper.get().readTree(verificationDataCaptor.getValue());
        assertEquals(verificationData.get("studyId").textValue(), TEST_STUDY_ID);
        assertEquals(verificationData.get("email").textValue(), consentNotificationEmail);

        // Verify sent email.
        ArgumentCaptor<BasicEmailProvider> emailProviderCaptor = ArgumentCaptor.forClass(
                BasicEmailProvider.class);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());

        MimeTypeEmail email = emailProviderCaptor.getValue().getMimeTypeEmail();
        assertEquals(email.getType(), EmailType.VERIFY_CONSENT_EMAIL);
        String body = (String) email.getMessageParts().get(0).getContent();

        assertTrue(body.contains("/vse?study="+ TEST_STUDY_ID + "&token=" +
                VERIFICATION_TOKEN + "&type=consent_notification"));
        assertTrue(email.getSenderAddress().contains(SUPPORT_EMAIL));
        assertEquals(emailProviderCaptor.getValue().getTokenMap().get("studyEmailVerificationExpirationPeriod"), "1 day");
        
        List<String> recipientList = email.getRecipientAddresses();
        assertEquals(recipientList.size(), 1);
        assertEquals(recipientList.get(0), consentNotificationEmail);
    }

    @Test
    public void updateStudyWithSameConsentNotificationEmailDoesntSendVerification() {
        // Original study. ConsentNotificationEmailVerified is true.
        Study originalStudy = getTestStudy();
        originalStudy.setConsentNotificationEmailVerified(true);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(originalStudy);

        // New study is the same as original study. Make some inconsequential change to the study name.
        Study newStudy = getTestStudy();
        newStudy.setName("different-name");
        newStudy.setConsentNotificationEmailVerified(true);

        // Execute. Verify the study name change. Verified is still true.
        service.updateStudy(newStudy, false);

        ArgumentCaptor<Study> savedStudyCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyDao).updateStudy(savedStudyCaptor.capture());

        Study savedStudy = savedStudyCaptor.getValue();
        assertTrue(savedStudy.isConsentNotificationEmailVerified());
        assertEquals(savedStudy.getName(), "different-name");

        // Verify we don't send email.
        verify(mockSendMailService, never()).sendEmail(any());
    }

    @Test
    public void updateStudyChangesNullConsentNotificationEmailVerifiedToTrue() {
        // For backwards-compatibility, we flip the verified=null flag to true. This only happens for older studies
        // that predate verification, most of which are confirmed working.
        updateStudyConsentNotificationEmailVerified(null, null, true);
    }

    @Test
    public void updateStudyCantFlipVerifiedFromFalseToTrue() {
        updateStudyConsentNotificationEmailVerified(false, true, false);
    }

    @Test
    public void updateStudyCanFlipVerifiedFromTrueToFalse() {
        updateStudyConsentNotificationEmailVerified(true, false, false);
    }

    private void updateStudyConsentNotificationEmailVerified(Boolean oldValue, Boolean newValue,
            Boolean expectedValue) {
        // Original study
        Study oldStudy = getTestStudy();
        oldStudy.setConsentNotificationEmailVerified(oldValue);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // New study
        Study newStudy = getTestStudy();
        newStudy.setConsentNotificationEmailVerified(newValue);

        // Update
        service.updateStudy(newStudy, false);

        // Verify result
        ArgumentCaptor<Study> savedStudyCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyDao).updateStudy(savedStudyCaptor.capture());

        Study savedStudy = savedStudyCaptor.getValue();
        assertEquals(savedStudy.isConsentNotificationEmailVerified(), expectedValue);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void sendVerifyEmailNullType() throws Exception {
        service.sendVerifyEmail(TEST_STUDY_IDENTIFIER, null);
    }

    // This can be manually triggered through the API even though there's no consent
    // email to confirm... so return a 400 in this case.
    @Test(expectedExceptions = BadRequestException.class)
    public void sendVerifyEmailNoConsentEmail() throws Exception {
        Study study = getTestStudy();
        study.setConsentNotificationEmail(null);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        service.sendVerifyEmail(TEST_STUDY_IDENTIFIER, StudyEmailType.CONSENT_NOTIFICATION);
    }
    
    @Test
    public void sendVerifyEmailSuccess() throws Exception {
        // Mock getStudy().
        Study study = getTestStudy();
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        // Execute.
        service.sendVerifyEmail(TEST_STUDY_IDENTIFIER, StudyEmailType.CONSENT_NOTIFICATION);

        // Verify email verification email.
        verifyEmailVerificationEmail(study.getConsentNotificationEmail());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullToken() {
        service.verifyEmail(TEST_STUDY_IDENTIFIER, null, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailEmptyToken() {
        service.verifyEmail(TEST_STUDY_IDENTIFIER, "", StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailBlankToken() {
        service.verifyEmail(TEST_STUDY_IDENTIFIER, "   ", StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullType() {
        service.verifyEmail(TEST_STUDY_IDENTIFIER, VERIFICATION_TOKEN, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNullVerificationData() {
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(null);
        service.verifyEmail(TEST_STUDY_IDENTIFIER, VERIFICATION_TOKEN, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailMismatchedStudy() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"studyId\":\"wrong-study\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getStudy().
        Study study = getTestStudy();
        study.setConsentNotificationEmail("correct-email@example.com");
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        // Execute. Will throw.
        service.verifyEmail(TEST_STUDY_IDENTIFIER, VERIFICATION_TOKEN, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailNoEmail() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"studyId\":\"" + TEST_STUDY_ID + "\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getStudy().
        Study study = getTestStudy();
        study.setConsentNotificationEmail(null);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        // Execute. Will throw.
        service.verifyEmail(TEST_STUDY_IDENTIFIER, VERIFICATION_TOKEN, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void verifyEmailMismatchedEmail() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"studyId\":\"" + TEST_STUDY_ID + "\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getStudy().
        Study study = getTestStudy();
        study.setConsentNotificationEmail("wrong-email@example.com");
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        // Execute. Will throw.
        service.verifyEmail(TEST_STUDY_IDENTIFIER, VERIFICATION_TOKEN, StudyEmailType.CONSENT_NOTIFICATION);
    }

    @Test
    public void verifyEmailSuccess() {
        // Mock Cache Provider.
        String verificationDataJson = "{\n" +
                "   \"studyId\":\"" + TEST_STUDY_ID + "\",\n" +
                "   \"email\":\"correct-email@example.com\"\n" +
                "}";
        when(mockCacheProvider.getObject(VER_CACHE_KEY, String.class)).thenReturn(verificationDataJson);

        // Mock getting the study from the cache.
        Study study = getTestStudy();
        study.setConsentNotificationEmail("correct-email@example.com");
        when(mockCacheProvider.getStudy(TEST_STUDY_ID)).thenReturn(study);

        // Execute. Verify consentNotificationEmailVerified is now true.
        service.verifyEmail(TEST_STUDY_IDENTIFIER, VERIFICATION_TOKEN, StudyEmailType.CONSENT_NOTIFICATION);

        ArgumentCaptor<Study> savedStudyCaptor = ArgumentCaptor.forClass(Study.class);
        verify(mockStudyDao).updateStudy(savedStudyCaptor.capture());

        Study savedStudy = savedStudyCaptor.getValue();
        assertTrue(savedStudy.isConsentNotificationEmailVerified());

        // Verify that we cached the study.
        verify(mockCacheProvider).setStudy(savedStudy);

        // Verify that we removed the used token.
        verify(mockCacheProvider).removeObject(VER_CACHE_KEY);
    }

    @Test
    public void cannotRemoveTaskIdentifiers() {
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setTaskIdentifiers(Sets.newHashSet("task2", "different-tag"));
        
        try {
            service.updateStudy(updatedStudy, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals(e.getMessage(), "Task identifiers cannot be deleted.");
            assertEquals(e.getEntityKeys().get("identifier"), TEST_STUDY_ID);
            assertEquals(e.getEntityKeys().get("type"), "Study");
        }
    }
    
    @Test
    public void cannotRemoveDataGroups() {
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);

        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setDataGroups(Sets.newHashSet("beta_users", "different-tag"));
        
        try {
            service.updateStudy(updatedStudy, true);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals(e.getMessage(), "Data groups cannot be deleted.");
            assertEquals(e.getEntityKeys().get("identifier"), TEST_STUDY_ID);
            assertEquals(e.getEntityKeys().get("type"), "Study");
        }
    }
    
    @Test
    public void cannotRemoveTaskIdentifiersEmptyLists() {
        study.setTaskIdentifiers(EMPTY_SET);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setTaskIdentifiers(EMPTY_SET);
        
        service.updateStudy(updatedStudy, true);
    }
    
    @Test
    public void cannotRemoveDataGroupsEmptyLists() {
        study.setDataGroups(EMPTY_SET);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(TEST_STUDY_ID);
        updatedStudy.setDataGroups(EMPTY_SET);
        
        service.updateStudy(updatedStudy, true);
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class, expectedExceptionsMessageRegExp = "Activity event keys cannot be deleted.")
    public void cannotRemoveActivityEventKeys() {
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setIdentifier(TEST_STUDY_ID);
        study.setActivityEventKeys(ImmutableSet.of("test"));
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        Study updatedStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        updatedStudy.setIdentifier(study.getIdentifier());
        updatedStudy.setActivityEventKeys(EMPTY_SET);
        
        service.updateStudy(updatedStudy, true);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyWithNullArgumentThrows() {
        service.getStudy((String)null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyWithEmptyStringArgumentThrows() {
        service.getStudy("");
    }
    
    @Test
    public void loadingStudyWithoutEmailSignInTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setEmailSignInTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getEmailSignInTemplate());
    }

    @Test
    public void loadingStudyWithoutAccountExistsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setEmailSignInTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getEmailSignInTemplate());
        assertNotNull(retStudy.getAccountExistsTemplate());
    }

    @Test
    public void loadingStudyWithoutSignedConsentTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setSignedConsentTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getSignedConsentTemplate());
    }

    @Test
    public void loadingStudyWithoutAppInstalLinkTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAppInstallLinkTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getAppInstallLinkTemplate());
    }
    
    @Test
    public void createStudyWithoutSignedConsentTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setSignedConsentTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getSignedConsentTemplate());
    }
    
    @Test
    public void updateStudyWithoutSignedConsentTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setSignedConsentTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getSignedConsentTemplate());
    }

    @Test
    public void loadingStudyWithoutResetPasswordSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setResetPasswordSmsTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getResetPasswordSmsTemplate());
    }
    
    @Test
    public void loadingStudyWithoutPhoneSignInSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setPhoneSignInSmsTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getPhoneSignInSmsTemplate());
    }
    
    @Test
    public void loadingStudyWithoutAppInstallLinkSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAppInstallLinkSmsTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getAppInstallLinkSmsTemplate());
    }
    
    @Test
    public void loadingStudyWithoutVerifyPhoneSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setVerifyPhoneSmsTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getVerifyPhoneSmsTemplate());
    }
    
    @Test
    public void loadingStudyWithoutAccountExistsSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAccountExistsSmsTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getAccountExistsSmsTemplate());
    }
    
    @Test
    public void loadingStudyWithoutSignedConsentSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setSignedConsentSmsTemplate(null);
        when(mockStudyDao.getStudy("foo")).thenReturn(study);
        
        Study retStudy = service.getStudy("foo");
        assertNotNull(retStudy.getSignedConsentSmsTemplate());
    }
    
    @Test
    public void updateStudyWithoutResetPasswordSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setResetPasswordSmsTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getResetPasswordSmsTemplate());
    }
    
    @Test
    public void updateStudyWithoutPhoneSignInSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setPhoneSignInSmsTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getPhoneSignInSmsTemplate());
    }
    
    @Test
    public void updateStudyWithoutAppInstallLinkSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAppInstallLinkSmsTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getAppInstallLinkSmsTemplate());
    }
    
    @Test
    public void updateStudyWithoutVerifyPhoneSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setVerifyPhoneSmsTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getVerifyPhoneSmsTemplate());
    }
    
    @Test
    public void updateStudyWithoutAccountExistsSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAccountExistsSmsTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getAccountExistsSmsTemplate());
    }
    
    @Test
    public void updateStudyWithoutSignedConsentSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setSignedConsentSmsTemplate(null);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        Study retStudy = service.updateStudy(study, false);
        assertNotNull(retStudy.getSignedConsentSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutResetPasswordSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setResetPasswordSmsTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getResetPasswordSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutPhoneSignInSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setPhoneSignInSmsTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getPhoneSignInSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutAppInstallLinkSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAppInstallLinkSmsTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getAppInstallLinkSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutVerifyPhoneSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setVerifyPhoneSmsTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getVerifyPhoneSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutAccountExistsSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setAccountExistsSmsTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getAccountExistsSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutSignedConsentSmsTemplateAddsADefault() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setSignedConsentSmsTemplate(null);
        
        Study retStudy = service.createStudy(study);
        assertNotNull(retStudy.getSignedConsentSmsTemplate());
    }
    
    @Test
    public void createStudyWithoutConsentNotificationEmailDoesNotSendNotification() {
        Study study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setConsentNotificationEmail(null);
        
        service.createStudy(study);
        
        verify(mockSendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void physicallyDeleteStudy() {
        // execute
        service.deleteStudy(TEST_STUDY_ID, true);

        // verify we called the correct dependent services
        verify(mockStudyDao).deleteStudy(study);
        verify(mockCompoundActivityDefinitionService).deleteAllCompoundActivityDefinitionsInStudy(
                study.getStudyIdentifier());
        verify(mockSubpopService).deleteAllSubpopulations(study.getStudyIdentifier());
        verify(mockTopicService).deleteAllTopics(study.getStudyIdentifier());
        verify(mockCacheProvider).removeStudy(TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void deactivateStudyAlreadyDeactivatedBefore() {
        Study study = getTestStudy();
        study.setActive(false);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);

        service.deleteStudy(study.getIdentifier(), false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deactivateStudyNotFound() {
        // Basically, this test doesn't do much because getStudy() will throw ENFE, not return null
        when(mockStudyDao.getStudy(study.getIdentifier())).thenThrow(new EntityNotFoundException(Study.class));
        service.deleteStudy(study.getIdentifier(), false);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void nonAdminsCannotUpdateDeactivatedStudy() {
        Study study = getTestStudy();
        study.setActive(false);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);

        service.updateStudy(study, false);

        verify(mockStudyDao, never()).updateStudy(any());
    }

    @Test
    public void updateUploadMetadataOldStudyHasNoFields() {
        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(null);
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));

        // execute - no exception
        service.updateStudy(newStudy, false);
    }

    @Test
    public void updateUploadMetadataNewStudyHasNoFields() {
        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(null);

        // execute - expect exception
        try {
            service.updateStudy(newStudy, false);
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

        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(reorderedField1, reorderedField2));
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(reorderedField2, reorderedField1, addedField));

        // execute - no exception
        service.updateStudy(newStudy, false);
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

        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, deletedField, modifiedFieldOld));
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, modifiedlFieldNew));

        // execute - expect exception
        try {
            service.updateStudy(newStudy, false);
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

        // old study
        Study oldStudy = getTestStudy();
        oldStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, deletedField, modifiedFieldOld));
        when(mockStudyDao.getStudy(TEST_STUDY_ID)).thenReturn(oldStudy);

        // new study
        Study newStudy = getTestStudy();
        newStudy.setUploadMetadataFieldDefinitions(ImmutableList.of(goodField, modifiedlFieldNew));

        // execute - no exception
        service.updateStudy(newStudy, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void nonAdminsCannotSetActiveToFalse() {
        Study originalStudy = getTestStudy();
        originalStudy.setActive(true);
        when(mockStudyDao.getStudy(originalStudy.getIdentifier())).thenReturn(originalStudy);

        Study study = getTestStudy();
        study.setIdentifier(originalStudy.getIdentifier());
        study.setActive(false);

        service.updateStudy(study, false);

        verify(mockStudyDao, never()).updateStudy(any());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void adminCannotSetActiveToFalse() {
        Study originalStudy = getTestStudy();
        originalStudy.setActive(true);
        when(mockStudyDao.getStudy(originalStudy.getIdentifier())).thenReturn(originalStudy);

        Study study = getTestStudy();
        study.setIdentifier(originalStudy.getIdentifier());
        study.setActive(false);

        service.updateStudy(study, true);

        verify(mockStudyDao, never()).updateStudy(any());
    }

    @Test
    public void createStudyAndUser() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);

        // spy
        doReturn(study).when(service).createStudy(any());
        
        // stub out use of synapse client so we can validate it, not just ignore it.
        when(mockAccessControlList.getResourceAccess()).thenReturn(new HashSet<>());
        when(mockSynapseClient.createEntity(projectCaptor.capture())).thenReturn(project);
        when(mockSynapseClient.getACL(TEST_PROJECT_ID)).thenReturn(mockAccessControlList);
        when(mockSynapseClient.createTeam(teamCaptor.capture())).thenReturn(team);

        // stub
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doNothing().when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);

        // verify
        verify(mockParticipantService, times(2)).createParticipant(any(), any(), anyBoolean());
        verify(mockParticipantService).createParticipant(study, mockUser1, false);
        verify(mockParticipantService).createParticipant(study, mockUser2, false);
        verify(mockParticipantService, times(2)).requestResetPassword(study, mockIdentifierHolder.getIdentifier());
        verify(mockSynapseClient, times(2)).newAccountEmailValidation(any(), eq(SYNAPSE_REGISTER_END_POINT));
        verify(service).createStudy(study);
        verify(service).createSynapseProjectTeam(TEST_ADMIN_IDS, study);
        
        assertEquals(projectCaptor.getValue().getName(), TEST_PROJECT_NAME);
        assertEquals(teamCaptor.getValue().getName(), TEST_TEAM_NAME);
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Admin ID is invalid.")
    public void createStudyAndUsersSynapseUserNotFound() throws SynapseException {
        when(mockSynapseClient.getUserProfile(any())).thenThrow(new SynapseNotFoundException());
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(ImmutableList.of("bad-admin-id"), study, null);

        service.createStudyAndUsers(mockStudyAndUsers);
    }
    
    @Test
    public void createStudyAndUsersDefaultsPasswordPolicy() throws SynapseException {
        study.setPasswordPolicy(null);
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseDataAccessTeamId(null);
        study.setSynapseProjectId(null);
        List<StudyParticipant> participants = ImmutableList.of(new StudyParticipant.Builder().withEmail(TEST_USER_EMAIL)
                .withRoles(ImmutableSet.of(DEVELOPER)).build());

        IdentifierHolder holder = new IdentifierHolder("user-id");
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(holder);
        
        AccessControlList acl = new AccessControlList();
        acl.setResourceAccess(new HashSet<>());
        
        when(mockSynapseClient.createTeam(any())).thenReturn(team);
        when(mockSynapseClient.createEntity(any())).thenReturn(project);
        when(mockSynapseClient.getACL(any())).thenReturn(acl);
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(ImmutableList.of("12345678"), study, participants);

        service.createStudyAndUsers(mockStudyAndUsers);
        
        verify(mockStudyDao).createStudy(studyCaptor.capture());
        assertNotNull(studyCaptor.getValue().getPasswordPolicy());
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "User can only have roles developer and/or researcher.")
    public void createStudyAndUsersUserInWrongRole() throws SynapseException {
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseDataAccessTeamId(null);
        study.setSynapseProjectId(null);
        List<StudyParticipant> participants = ImmutableList.of(
                new StudyParticipant.Builder().withEmail(TEST_USER_EMAIL).withRoles(ImmutableSet.of(WORKER)).build());
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(ImmutableList.of("12345678"), study, participants);

        service.createStudyAndUsers(mockStudyAndUsers);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "User should have at least one role.")
    public void createStudyAndUsersUserHasNoRole() throws SynapseException {
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseDataAccessTeamId(null);
        study.setSynapseProjectId(null);
        List<StudyParticipant> participants = ImmutableList
                .of(new StudyParticipant.Builder().withEmail(TEST_USER_EMAIL).build());
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(ImmutableList.of("12345678"), study, participants);

        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Admin IDs are required.")
    public void createStudyAndUserWithNullAdmins() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(null, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Admin IDs are required.")
    public void createStudyAndUserWithEmptyRoles() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of())
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(null, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Admin IDs are required.")
    public void createStudyAndUserWithEmptyAdmins() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(ImmutableList.of(), study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "User list is required.")
    public void createStudyAndUserWithEmptyUser() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        List<StudyParticipant> mockUsers = new ArrayList<>();
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "User list is required.")
    public void createStudyAndUserWithNullUser() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, null);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test (expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Study cannot be null.")
    public void createStudyAndUserWithNullStudy() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, null, mockUsers);

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class, expectedExceptionsMessageRegExp = "Study already has a project ID.")
    public void createStudyAndUserSynapseProjectIdExists() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .build();

        when(mockParticipantService.createParticipant(any(), any(), anyBoolean()))
                .thenReturn(new IdentifierHolder("userId"));
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, ImmutableList.of(mockUser1));

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }    
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class, expectedExceptionsMessageRegExp = "Study already has a team ID.")
    public void createStudyAndUserSynapseAccessTeamIdExists() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .build();

        when(mockParticipantService.createParticipant(any(), any(), anyBoolean()))
                .thenReturn(new IdentifierHolder("userId"));
        
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, ImmutableList.of(mockUser1));

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }
    
    @Test(expectedExceptions = SynapseClientException.class)
    public void createStudyAndUserThrowExceptionNotLogged() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);

        // spy
        doReturn(study).when(service).createStudy(any());

        // stub
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doThrow(SynapseClientException.class).when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createStudyAndUserNullStudyName() throws Exception {
        // mock
        Study study = getTestStudy();
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setName(null); // This is not a good name...

        service.createSynapseProjectTeam(ImmutableList.of(TEST_IDENTIFIER), study);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void createStudyAndUserBadStudyName() throws Exception {
        // mock
        Study study = getTestStudy();
        study.setExternalIdRequiredOnSignup(false);
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setName("# # "); // This is not a good name...

        service.createSynapseProjectTeam(ImmutableList.of(TEST_IDENTIFIER), study);
    }
    
    @Test
    public void createStudyAndUserThrowExceptionLogged() throws SynapseException {
        // mock
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);
        study.setExternalIdRequiredOnSignup(false);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);

        StudyParticipant mockUser1 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        StudyParticipant mockUser2 = new StudyParticipant.Builder()
                .withEmail(TEST_USER_EMAIL_2)
                .withFirstName(TEST_USER_FIRST_NAME)
                .withLastName(TEST_USER_LAST_NAME)
                .withRoles(ImmutableSet.of(Roles.RESEARCHER))
                .withPassword(TEST_USER_PASSWORD)
                .build();

        List<StudyParticipant> mockUsers = ImmutableList.of(mockUser1, mockUser2);
        StudyAndUsers mockStudyAndUsers = new StudyAndUsers(TEST_ADMIN_IDS, study, mockUsers);
        IdentifierHolder mockIdentifierHolder = new IdentifierHolder(TEST_IDENTIFIER);

        // spy
        doReturn(study).when(service).createStudy(any());
        doReturn(study).when(service).createSynapseProjectTeam(any(), any());

        // stub
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(mockIdentifierHolder);
        doThrow(new UnknownSynapseServerException(500, "The email address provided is already used.")).when(mockSynapseClient).newAccountEmailValidation(any(), any());

        // execute
        service.createStudyAndUsers(mockStudyAndUsers);

        // verify
        verify(mockParticipantService, times(2)).createParticipant(any(), any(), anyBoolean());
        verify(mockParticipantService).createParticipant(study, mockUser1, false);
        verify(mockParticipantService).createParticipant(study, mockUser2, false);
        verify(mockParticipantService, times(2)).requestResetPassword(study, mockIdentifierHolder.getIdentifier());
        verify(mockSynapseClient, times(2)).newAccountEmailValidation(any(), eq(SYNAPSE_REGISTER_END_POINT));
        verify(service).createStudy(study);
        verify(service).createSynapseProjectTeam(TEST_ADMIN_IDS, study);
    }

    @Test
    public void createSynapseProjectTeam() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        AccessControlList mockAcl = new AccessControlList();
        AccessControlList mockTeamAcl = new AccessControlList();
        mockAcl.setResourceAccess(new HashSet<>());
        mockTeamAcl.setResourceAccess(new HashSet<>());

        // pre-setup
        when(mockSynapseClient.createTeam(any())).thenReturn(team);
        when(mockSynapseClient.createEntity(any())).thenReturn(project);
        when(mockSynapseClient.getACL(any())).thenReturn(mockAcl);

        // execute
        Study retStudy = service.createSynapseProjectTeam(ImmutableList.of(TEST_USER_ID.toString()), study);

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
        assertEquals(bridgeStaffAcl.getAccessType(), StudyService.READ_DOWNLOAD_ACCESS);

        // 5. Created data access team.
        ResourceAccess capturedTeamRa = principalIdToAcl.get(Long.valueOf(TEST_TEAM_ID));
        assertEquals(capturedTeamRa.getAccessType(), StudyService.READ_DOWNLOAD_ACCESS);

        // invite user to team
        verify(mockSynapseClient).createMembershipInvitation(eq(teamMemberInvitation), any(), any());
        verify(mockSynapseClient).setTeamMemberPermissions(eq(TEST_TEAM_ID), eq(TEST_USER_ID.toString()), anyBoolean());

        // update study
        assertNotNull(retStudy);
        assertEquals(retStudy.getIdentifier(), study.getIdentifier());
        assertEquals(retStudy.getName(), study.getName());
        assertEquals(retStudy.getSynapseProjectId(), TEST_PROJECT_ID);
        assertEquals(retStudy.getSynapseDataAccessTeamId().toString(), TEST_TEAM_ID);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamNonExistUserID() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        // pre-setup
        when(mockSynapseClient.getUserProfile(any())).thenThrow(SynapseNotFoundException.class);

        // execute
        service.createSynapseProjectTeam(ImmutableList.of(TEST_USER_ID.toString()), study);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamNullUserID() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        // execute
        service.createSynapseProjectTeam(null, study);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createSynapseProjectTeamEmptyUserID() throws SynapseException {
        Study study = getTestStudy();
        study.setSynapseProjectId(null);
        study.setSynapseDataAccessTeamId(null);

        // execute
        service.createSynapseProjectTeam(ImmutableList.of(), study);
    }

    @Test
    public void newStudyVerifiesSupportEmail() {
        Study study = getTestStudy();
        when(mockEmailVerificationService.verifyEmailAddress(study.getSupportEmail()))
                .thenReturn(EmailVerificationStatus.PENDING);

        service.createStudy(study);

        verify(mockEmailVerificationService).verifyEmailAddress(study.getSupportEmail());
        assertTrue(study.getDataGroups().contains(BridgeConstants.TEST_USER_GROUP));
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createStudyChecksForExistingIdentifier() {
        Study study = getTestStudy();
        
        // already exists under the same ID.
        when(mockStudyDao.doesIdentifierExist(study.getIdentifier())).thenReturn(true);
        
        service.createStudy(study);
    }
    
    // This would be destructive
    @Test
    public void createStudyDoesNotCreateCertsForWhitelistedStudies() {
        Study study = getTestStudy();
        study.setIdentifier("api"); // the only Id in the mock whitelist
        
        service.createStudy(study);
        
        verify(mockUploadCertService, never()).createCmsKeyPair(TEST_STUDY);
    }
    
    @Test
    public void updatingStudyVerifiesSupportEmail() throws Exception {
        Study study = getTestStudy();
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);

        // We need to copy study in order to set support email and have it be different than
        // the mock version returned from the database
        Study newStudy = BridgeObjectMapper.get().readValue(
                BridgeObjectMapper.get().writeValueAsString(study), Study.class);
        newStudy.setSupportEmail("foo@foo.com"); // it's new and must be verified.
        
        service.updateStudy(newStudy, false);
        verify(mockEmailVerificationService).verifyEmailAddress("foo@foo.com");
    }

    @Test
    public void updatingStudyNoChangeInSupportEmailDoesNotVerifyEmail() {
        Study study = getTestStudy();
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(study);
        
        service.updateStudy(study, false);
        verify(mockEmailVerificationService, never()).verifyEmailAddress(any());
    }
    
    @Test
    public void textTemplateIsSanitized() {
        EmailTemplate source = new EmailTemplate("<p>Test</p>","<p>This should have no markup</p>", MimeType.TEXT);
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        assertEquals(result.getSubject(), "Test");
        assertEquals(result.getBody(), "This should have no markup");
        assertEquals(result.getMimeType(), MimeType.TEXT);
    }
    
    @Test
    public void htmlTemplateIsSanitized() {
        EmailTemplate source = new EmailTemplate("<p>${studyName} test</p>", "<p>This should remove: <iframe src=''></iframe></p>", MimeType.HTML); 
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        assertHtmlTemplateSanitized(result);
    }
    
    @Test
    public void htmlTemplatePreservesLinksWithTemplateVariables() {
        EmailTemplate source = new EmailTemplate("", "<p><a href=\"http://www.google.com/\"></a><a href=\"/foo.html\">Foo</a><a href=\"${url}\">${url}</a></p>", MimeType.HTML); 
        
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        // The absolute, relative, and template URLs are all preserved correctly. 
        assertEquals(result.getBody(),
                "<p><a href=\"http://www.google.com/\"></a><a href=\"/foo.html\">Foo</a><a href=\"${url}\">${url}</a></p>");
    }
    
    @Test
    public void emptyTemplateIsSanitized() {
        EmailTemplate source = new EmailTemplate("", "", MimeType.HTML); 
        EmailTemplate result = service.sanitizeEmailTemplate(source);
        
        assertEquals(result.getSubject(), "");
        assertEquals(result.getBody(), "");
        assertEquals(result.getMimeType(), MimeType.HTML);
    }
    
    @Test
    public void testAllSixTemplatesAreSanitized() {
        EmailTemplate source = new EmailTemplate("<p>${studyName} test</p>", "<p>This should remove: <iframe src=''></iframe></p>", MimeType.HTML);
        Study study = new DynamoStudy();
        study.setEmailSignInTemplate(source);
        study.setResetPasswordTemplate(source);
        study.setVerifyEmailTemplate(source);
        study.setAccountExistsTemplate(source);
        study.setSignedConsentTemplate(source);
        study.setAppInstallLinkTemplate(source);
        
        service.sanitizeHTML(study);
        assertHtmlTemplateSanitized( study.getEmailSignInTemplate() );
        assertHtmlTemplateSanitized( study.getResetPasswordTemplate() );
        assertHtmlTemplateSanitized( study.getVerifyEmailTemplate() );
        assertHtmlTemplateSanitized( study.getAccountExistsTemplate() );
        assertHtmlTemplateSanitized( study.getSignedConsentTemplate() );
        assertHtmlTemplateSanitized( study.getAppInstallLinkTemplate() );
    }
    
    @Test
    public void updateStudyCorrectlyDetectsEmailChangesInvolvingNulls() {
        // consent email still correctly detected
        String originalEmail = TestUtils.getValidStudy(StudyServiceMockTest.class).getConsentNotificationEmail();
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
        Study original = TestUtils.getValidStudy(StudyServiceMockTest.class);
        original.setConsentNotificationEmail(originalEmail);
        when(mockStudyDao.getStudy(any())).thenReturn(original);
        
        Study update = TestUtils.getValidStudy(StudyServiceMockTest.class);
        update.setConsentNotificationEmail(newEmail);
        // just assume this is true for the test so defaults aren't set
        update.setConsentNotificationEmailVerified(true);
        
        service.updateStudy(update, true);
        
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

    private void assertHtmlTemplateSanitized(EmailTemplate result) {
        assertEquals(result.getSubject(), "${studyName} test");
        assertEquals(result.getBody(), "<p>This should remove: </p>");
        assertEquals(result.getMimeType(), MimeType.HTML);
    }

    private static Resource mockTemplateAsSpringResource(String content) throws Exception {
        byte[] contentBytes = content.getBytes(Charsets.UTF_8);
        Resource mockResource = mock(Resource.class);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream(contentBytes));
        return mockResource;
    }
    
    // Tests from the Play-based StudyServiceTest.java in BridgePF
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void studyIsValidated() {
        Study testStudy = new DynamoStudy();
        testStudy.setName("Belgian Waffles [Test]");
        service.createStudy(testStudy);
    }

    @Test
    public void cannotCreateAnExistingStudyWithAVersion() {
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study = service.createStudy(study);
        try {
            study = service.createStudy(study);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            // expected exception
        }
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void cannotCreateAStudyWithAVersion() {
        Study testStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        testStudy.setVersion(1L);
        service.createStudy(testStudy);
    }

    /**
     * From the non-mock tests, this test is probably redundant with other test, but is kept 
     * here.
     */
    @Test
    public void crudStudy() {
        // developer
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        // verify this can be null, that's okay, and the flags are reset correctly on create
        study.setConsentNotificationEmailVerified(true);
        study.setStudyIdExcludedInExport(false);
        study.setTaskIdentifiers(null);
        study.setUploadValidationStrictness(null);
        study.setActivityEventKeys(null);
        study.setHealthCodeExportEnabled(true);
        study.setActive(false);
        study.setStrictUploadValidationEnabled(false);
        study.setEmailVerificationEnabled(false);
        study.setEmailSignInEnabled(true);
        study.setPhoneSignInEnabled(true);
        
        study = service.createStudy(study);

        // Verify that the flags are set correctly on create.
        assertFalse(study.isConsentNotificationEmailVerified());
        assertNotNull(study.getVersion(), "Version has been set");
        assertTrue(study.isActive());
        assertFalse(study.isStrictUploadValidationEnabled());
        assertTrue(study.isStudyIdExcludedInExport());
        assertEquals(study.getUploadValidationStrictness(), REPORT);

        verify(mockCacheProvider).setStudy(study);
        
        // A default, active consent should be created for the study.
        verify(mockSubpopService).createDefaultSubpopulation(study);

        verify(mockStudyDao).createStudy(studyCaptor.capture());

        Study newStudy = studyCaptor.getValue();
        assertTrue(newStudy.isActive());
        assertFalse(newStudy.isStrictUploadValidationEnabled());
        assertTrue(newStudy.isStudyIdExcludedInExport());
        assertEquals(UploadValidationStrictness.REPORT, newStudy.getUploadValidationStrictness());

        // Verify that the missing templates where created
        assertNotNull(newStudy.getEmailSignInTemplate());
        assertNotNull(newStudy.getAccountExistsTemplate());
        assertNotNull(newStudy.getResetPasswordSmsTemplate());
        assertNotNull(newStudy.getPhoneSignInSmsTemplate());
        assertNotNull(newStudy.getAppInstallLinkSmsTemplate());
        assertNotNull(newStudy.getVerifyPhoneSmsTemplate());
        assertNotNull(newStudy.getAccountExistsSmsTemplate());
        
        assertEquals(newStudy.getIdentifier(), study.getIdentifier());
        assertEquals(newStudy.getName(), "Test Study [StudyServiceMockTest]");
        assertEquals(newStudy.getMinAgeOfConsent(), 18);
        assertEquals(newStudy.getDataGroups(), ImmutableSet.of("beta_users", "production_users", TEST_USER_GROUP));
        assertTrue(newStudy.getTaskIdentifiers().isEmpty());
        assertTrue(newStudy.getActivityEventKeys().isEmpty());

        // these should have been changed
        assertEquals(newStudy.getEmailSignInTemplate().getSubject(), "${studyName} link");
        assertEquals(newStudy.getEmailSignInTemplate().getBody(), "Follow link ${url}");
        
        verify(mockCacheProvider).setStudy(newStudy);

        // make some (non-admin) updates, these should change
        newStudy.setConsentNotificationEmailVerified(true);
        newStudy.setStrictUploadValidationEnabled(true);
        newStudy.setUploadValidationStrictness(WARNING);
        
        when(mockStudyDao.getStudy(newStudy.getIdentifier())).thenReturn(newStudy);
        Study updatedStudy = service.updateStudy(newStudy, false);
        
        assertTrue(updatedStudy.isConsentNotificationEmailVerified());
        assertTrue(updatedStudy.isStrictUploadValidationEnabled());
        assertEquals(updatedStudy.getUploadValidationStrictness(), WARNING);

        verify(mockCacheProvider).removeStudy(updatedStudy.getIdentifier());
        verify(mockCacheProvider, times(2)).setStudy(updatedStudy);

        // delete study
        reset(mockCacheProvider);
        service.deleteStudy(study.getIdentifier(), true);
        
        verify(mockCacheProvider).getStudy(study.getIdentifier());
        verify(mockCacheProvider).setStudy(updatedStudy);
        verify(mockCacheProvider).removeStudy(study.getIdentifier());

        verify(mockStudyDao).deleteStudy(updatedStudy);
        verify(mockCompoundActivityDefinitionService)
                .deleteAllCompoundActivityDefinitionsInStudy(updatedStudy.getStudyIdentifier());
        verify(mockSubpopService).deleteAllSubpopulations(updatedStudy.getStudyIdentifier());
        verify(mockTopicService).deleteAllTopics(updatedStudy.getStudyIdentifier());
    }

    @Test
    public void canUpdatePasswordPolicyAndTemplates() throws Exception {
        // service need the defaults injected for this test...
        service.setDefaultEmailVerificationTemplate(TEMPLATE_RESOURCE);
        service.setDefaultEmailVerificationTemplateSubject(TEMPLATE_RESOURCE);
        service.setDefaultPasswordTemplate(TEMPLATE_RESOURCE);
        service.setDefaultPasswordTemplateSubject(TEMPLATE_RESOURCE);
        
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setPasswordPolicy(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);

        study = service.createStudy(study);

        // First, verify that defaults are set...
        PasswordPolicy policy = study.getPasswordPolicy();
        assertNotNull(policy);
        assertEquals(8, policy.getMinLength());
        assertTrue(policy.isNumericRequired());
        assertTrue(policy.isSymbolRequired());
        assertTrue(policy.isUpperCaseRequired());

        EmailTemplate veTemplate = study.getVerifyEmailTemplate();
        assertNotNull(veTemplate);
        assertNotNull(veTemplate.getSubject());
        assertNotNull(veTemplate.getBody());
        
        EmailTemplate rpTemplate = study.getResetPasswordTemplate();
        assertNotNull(rpTemplate);
        assertNotNull(rpTemplate.getSubject());
        assertNotNull(rpTemplate.getBody());
        
        SmsTemplate smsTemplate = new SmsTemplate("Test Template ${token} ${appInstallUrl} ${resetPasswordUrl}"); 
        study.setResetPasswordSmsTemplate(smsTemplate);
        study.setPhoneSignInSmsTemplate(smsTemplate);
        study.setAppInstallLinkSmsTemplate(smsTemplate);
        study.setVerifyPhoneSmsTemplate(smsTemplate);
        study.setAccountExistsSmsTemplate(smsTemplate);
        
        // Now change them and verify they are changed.
        study.setPasswordPolicy(new PasswordPolicy(6, true, false, false, true));
        study.setVerifyEmailTemplate(new EmailTemplate("subject *", "body ${url} *", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject **", "body ${url} **", MimeType.TEXT));
        
        // You have to mock this for the update
        Study existingStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(existingStudy);
        
        study = service.updateStudy(study, true);
        
        policy = study.getPasswordPolicy();
        assertTrue(study.isEmailVerificationEnabled());
        assertTrue(study.isAutoVerificationPhoneSuppressed());

        assertEquals(policy.getMinLength(), 6);
        assertTrue(policy.isNumericRequired());
        assertFalse(policy.isSymbolRequired());
        assertFalse(policy.isLowerCaseRequired());
        assertTrue(policy.isUpperCaseRequired());
        
        veTemplate = study.getVerifyEmailTemplate();
        assertEquals(veTemplate.getSubject(), "subject *");
        assertEquals(veTemplate.getBody(), "body ${url} *");
        assertEquals(veTemplate.getMimeType(), MimeType.TEXT);
        
        rpTemplate = study.getResetPasswordTemplate();
        assertEquals(rpTemplate.getSubject(), "subject **");
        assertEquals(rpTemplate.getBody(), "body ${url} **");
        assertEquals(rpTemplate.getMimeType(), MimeType.TEXT);
        
        assertEquals(study.getResetPasswordSmsTemplate().getMessage(), smsTemplate.getMessage());
        assertEquals(study.getPhoneSignInSmsTemplate().getMessage(), smsTemplate.getMessage());
        assertEquals(study.getAppInstallLinkSmsTemplate().getMessage(), smsTemplate.getMessage());
        assertEquals(study.getVerifyPhoneSmsTemplate().getMessage(), smsTemplate.getMessage());
        assertEquals(study.getAccountExistsSmsTemplate().getMessage(), smsTemplate.getMessage());
    }

    @Test
    public void defaultsAreUsedWhenNotProvided() throws Exception {
        service.setDefaultEmailVerificationTemplate(TEMPLATE_RESOURCE);
        service.setDefaultPasswordTemplate(TEMPLATE_RESOURCE);
        service.setDefaultPasswordTemplateSubject(TEMPLATE_RESOURCE);
        service.setDefaultEmailSignInTemplate(TEMPLATE_RESOURCE);
        service.setDefaultEmailSignInTemplateSubject(TEMPLATE_RESOURCE);
        service.setDefaultAccountExistsTemplate(TEMPLATE_RESOURCE);
        service.setDefaultAccountExistsTemplateSubject(TEMPLATE_RESOURCE);
        service.setSignedConsentTemplate(TEMPLATE_RESOURCE);
        service.setSignedConsentTemplateSubject(TEMPLATE_RESOURCE);
        service.setAppInstallLinkTemplate(TEMPLATE_RESOURCE);
        service.setAppInstallLinkTemplateSubject(TEMPLATE_RESOURCE);
        service.setStudyEmailVerificationTemplate(TEMPLATE_RESOURCE);
        service.setStudyEmailVerificationTemplateSubject(TEMPLATE_RESOURCE);
        service.setResetPasswordSmsTemplate("${url}");
        service.setPhoneSignInSmsTemplate("${token}");
        service.setAppInstallLinkSmsTemplate("${url}");
        service.setVerifyPhoneSmsTemplate("${token}");
        service.setAccountExistsSmsTemplate("${token}");
        service.setSignedConsentSmsTemplate("${consentUrl}");
        
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setPasswordPolicy(null);
        study.setEmailSignInTemplate(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);
        study.setEmailSignInTemplate(null);
        study.setAccountExistsTemplate(null);
        study.setSignedConsentTemplate(null);
        study.setAppInstallLinkTemplate(null);
        study.setResetPasswordSmsTemplate(null);
        study.setPhoneSignInSmsTemplate(null);
        study.setAppInstallLinkSmsTemplate(null);
        study.setVerifyPhoneSmsTemplate(null);
        study.setAccountExistsSmsTemplate(null);
        study.setSignedConsentSmsTemplate(null);
        study = service.createStudy(study);
        
        assertEquals(DEFAULT_PASSWORD_POLICY, study.getPasswordPolicy());
        assertNotNull(study.getPasswordPolicy());
        assertNotNull(study.getEmailSignInTemplate());
        assertNotNull(study.getVerifyEmailTemplate());
        assertNotNull(study.getResetPasswordTemplate());
        assertNotNull(study.getEmailSignInTemplate());
        assertNotNull(study.getAccountExistsTemplate());
        assertNotNull(study.getSignedConsentTemplate());
        assertNotNull(study.getAppInstallLinkTemplate());
        assertNotNull(study.getResetPasswordSmsTemplate());
        assertNotNull(study.getPhoneSignInSmsTemplate());
        assertNotNull(study.getAppInstallLinkSmsTemplate());
        assertNotNull(study.getVerifyPhoneSmsTemplate());
        assertNotNull(study.getAccountExistsSmsTemplate());
        assertNotNull(study.getSignedConsentSmsTemplate());
        
        // Remove them and update... we are set back to defaults
        study.setPasswordPolicy(null);
        study.setEmailSignInTemplate(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);
        study.setEmailSignInTemplate(null);
        study.setAccountExistsTemplate(null);
        study.setSignedConsentTemplate(null);
        study.setAppInstallLinkTemplate(null);
        study.setResetPasswordSmsTemplate(null);
        study.setPhoneSignInSmsTemplate(null);
        study.setAppInstallLinkSmsTemplate(null);
        study.setVerifyPhoneSmsTemplate(null);
        study.setAccountExistsSmsTemplate(null);
        study.setSignedConsentSmsTemplate(null);
        
        // You have to mock this for the update
        Study existingStudy = TestUtils.getValidStudy(StudyServiceMockTest.class);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(existingStudy);
        
        study = service.updateStudy(study, false);
        assertNotNull(study.getPasswordPolicy());
        assertNotNull(study.getEmailSignInTemplate());
        assertNotNull(study.getVerifyEmailTemplate());
        assertNotNull(study.getResetPasswordTemplate());
        assertNotNull(study.getEmailSignInTemplate());
        assertNotNull(study.getAccountExistsTemplate());
        assertNotNull(study.getSignedConsentTemplate());
        assertNotNull(study.getAppInstallLinkTemplate());
        assertNotNull(study.getResetPasswordSmsTemplate());
        assertNotNull(study.getPhoneSignInSmsTemplate());
        assertNotNull(study.getAppInstallLinkSmsTemplate());
        assertNotNull(study.getVerifyPhoneSmsTemplate());
        assertNotNull(study.getAccountExistsSmsTemplate());
        assertNotNull(study.getSignedConsentSmsTemplate());
    }

    @Test
    public void problematicHtmlIsRemovedFromTemplates() {
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setVerifyEmailTemplate(new EmailTemplate("<b>This is not allowed [ve]</b>", "<p>Test [ve] ${url}</p><script></script>", MimeType.HTML));
        study.setResetPasswordTemplate(new EmailTemplate("<b>This is not allowed [rp]</b>", "<p>Test [rp] ${url}</p>", MimeType.TEXT));
        study = service.createStudy(study);
        
        EmailTemplate template = study.getVerifyEmailTemplate();
        assertEquals("This is not allowed [ve]", template.getSubject());
        assertEquals("<p>Test [ve] ${url}</p>", template.getBody());
        assertEquals(MimeType.HTML, template.getMimeType());
        
        template = study.getResetPasswordTemplate();
        assertEquals("This is not allowed [rp]", template.getSubject());
        assertEquals("Test [rp] ${url}", template.getBody());
        assertEquals(MimeType.TEXT, template.getMimeType());
    }

    @Test
    public void adminsCanChangeSomeValuesResearchersCannot() {
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study.setStudyIdExcludedInExport(true);
        study.setEmailVerificationEnabled(true);
        study.setExternalIdRequiredOnSignup(false);
        study.setEmailSignInEnabled(false);
        study.setPhoneSignInEnabled(false);
        study.setReauthenticationEnabled(false);
        study.setAccountLimit(0);
        study.setVerifyChannelOnSignInEnabled(false);

        Study existing = TestUtils.getValidStudy(StudyServiceMockTest.class);
        existing.setExternalIdRequiredOnSignup(false);
        existing.setEmailSignInEnabled(false);
        existing.setPhoneSignInEnabled(false);
        existing.setReauthenticationEnabled(false);
        assertStudyDefaults(existing);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(existing);
        
        // Cannot be changed on create
        study = service.createStudy(study);
        assertStudyDefaults(study); // still set to defaults
        
        // Researchers cannot change these through update
        changeStudyDefaults(study);
        study = service.updateStudy(study, false);
        assertStudyDefaults(study); // nope
        
        // But administrators can change these
        changeStudyDefaults(study);
        study = service.updateStudy(study, true);
        // These values have all successfully been changed from the defaults
        assertFalse(study.isStudyIdExcludedInExport());
        assertFalse(study.isEmailVerificationEnabled());
        assertFalse(study.isVerifyChannelOnSignInEnabled());
        assertTrue(study.isAutoVerificationPhoneSuppressed());
        assertTrue(study.isExternalIdRequiredOnSignup());
        assertTrue(study.isEmailSignInEnabled());
        assertTrue(study.isPhoneSignInEnabled());
        assertTrue(study.isReauthenticationEnabled());
        assertEquals(study.getAccountLimit(), 10);
    }

    private void assertStudyDefaults(Study study) {
        assertTrue(study.isStudyIdExcludedInExport());
        assertTrue(study.isEmailVerificationEnabled());
        assertTrue(study.isVerifyChannelOnSignInEnabled());
        assertFalse(study.isExternalIdRequiredOnSignup());
        assertFalse(study.isEmailSignInEnabled());
        assertFalse(study.isPhoneSignInEnabled());
        assertFalse(study.isReauthenticationEnabled());
        assertEquals(study.getAccountLimit(), 0);
    }
    
    private void changeStudyDefaults(Study study) {
        study.setStudyIdExcludedInExport(false);
        study.setEmailVerificationEnabled(false);
        study.setVerifyChannelOnSignInEnabled(false);
        study.setExternalIdRequiredOnSignup(true);
        study.setEmailSignInEnabled(true);
        study.setPhoneSignInEnabled(true);
        study.setReauthenticationEnabled(true);
        study.setAccountLimit(10);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateWithInvalidTemplateIsInvalid() {
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        study = service.createStudy(study);
        
        Study existing = TestUtils.getValidStudy(StudyServiceMockTest.class);
        when(mockStudyDao.getStudy(study.getIdentifier())).thenReturn(existing);        
        
        study.setVerifyEmailTemplate(new EmailTemplate(null, null, MimeType.HTML));
        service.updateStudy(study, false);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void cantDeleteApiStudy() {
        service.deleteStudy("api", true);
    }

    @Test
    public void ckeditorHTMLIsPreserved() {
        study = TestUtils.getValidStudy(StudyServiceMockTest.class);
        
        String body = "<s>This is a test</s><p style=\"color:red\">of new attributes ${url}.</p><hr>";
        
        EmailTemplate template = new EmailTemplate("Subject", body, MimeType.HTML);
        
        study.setVerifyEmailTemplate(template);
        study.setResetPasswordTemplate(template);
        
        study = service.createStudy(study);
        
        // The templates are pretty-print formatted, so remove that. Otherwise, everything should be
        // preserved.
        
        template = study.getVerifyEmailTemplate();
        assertEquals(body, template.getBody().replaceAll("[\n\t\r]", ""));
        
        template = study.getResetPasswordTemplate();
        assertEquals(body, template.getBody().replaceAll("[\n\t\r]", ""));
    }
    
    // Additional tests based on code coverage.
    
    

}
