package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.TRUE;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.collectExternalIds;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.ENROLLMENT;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.models.AccountSummarySearch.EMPTY_SEARCH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.sagebionetworks.bridge.models.schedules.ActivityType.SURVEY;
import static org.sagebionetworks.bridge.models.sms.SmsType.PROMOTIONAL;
import static org.sagebionetworks.bridge.models.sms.SmsType.TRANSACTIONAL;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.services.ParticipantService.ACCOUNT_UNABLE_TO_BE_CONTACTED_ERROR;
import static org.sagebionetworks.bridge.services.ParticipantService.APP_INSTALL_URL_KEY;
import static org.sagebionetworks.bridge.services.ParticipantService.NO_INSTALL_LINKS_ERROR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.ParticipantRosterRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserConsentHistory;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.apps.SmsTemplate;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ParticipantServiceTest extends Mockito {
    private static final DateTime ACTIVITIES_RETRIEVED_DATETIME = DateTime.parse("2019-08-01T18:32:36.487-0700");
    private static final ClientInfo CLIENT_INFO = new ClientInfo.Builder().withAppName("unit test")
            .withAppVersion(4).build();
    private static final DateTime CREATED_ON_DATETIME = DateTime.parse("2019-07-30T12:09:28.184-0700");
    private static final DateTime ENROLLMENT_DATETIME = DateTime.parse("2019-07-31T21:42:44.019-0700");
    private static final RequestInfo REQUEST_INFO = new RequestInfo.Builder().withClientInfo(CLIENT_INFO)
            .withLanguages(TestConstants.LANGUAGES).withUserDataGroups(TestConstants.USER_DATA_GROUPS).build();
    private static final Set<String> APP_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> APP_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2");
    private static final long CONSENT_PUBLICATION_DATE = DateTime.now().getMillis();
    private static final Phone PHONE = TestConstants.PHONE;
    private static final App APP = App.create();
    static {
        APP.setIdentifier(TEST_APP_ID);
        APP.setHealthCodeExportEnabled(true);
        APP.setUserProfileAttributes(APP_PROFILE_ATTRS);
        APP.setDataGroups(APP_DATA_GROUPS);
        APP.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        APP.getUserProfileAttributes().add("can_be_recontacted");
        APP.setInstallLinks(ImmutableMap.of("Android", "some.link"));
    }
    private static final String EXTERNAL_ID = "externalId";
    private static final String HEALTH_CODE = "healthCode";
    private static final String LAST_NAME = "lastName";
    private static final String FIRST_NAME = "firstName";
    private static final String PASSWORD = "P@ssword1";
    private static final String ACTIVITY_GUID = "activityGuid";
    private static final String PAGED_BY = "100";
    private static final int PAGE_SIZE = 50;
    private static final Set<Roles> RESEARCH_CALLER_ROLES = ImmutableSet.of(RESEARCHER);
    private static final Set<Roles> DEV_CALLER_ROLES = ImmutableSet.of(DEVELOPER);
    private static final Set<String> CALLER_SUBS = ImmutableSet.of();
    private static final List<String> USER_LANGUAGES = ImmutableList.copyOf(BridgeUtils.commaListToOrderedSet("de,fr"));
    private static final String EMAIL = "email@email.com";
    private static final String ID = "ASDF";
    private static final String STUDY_ID = "studyId";
    private static final ImmutableMap<String, String> ENROLLMENT_MAP = ImmutableMap.of(STUDY_ID, EXTERNAL_ID);
    private static final DateTimeZone USER_TIME_ZONE = DateTimeZone.forOffsetHours(-3);
    private static final Map<String,String> ATTRS = ImmutableMap.of("can_be_recontacted","true");
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(APP.getIdentifier());
    private static final SubpopulationGuid SUBPOP_GUID_1 = SubpopulationGuid.create("guid1");
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, ID);
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder()
            .withFirstName(FIRST_NAME)
            .withLastName(LAST_NAME)
            .withEmail(EMAIL)
            .withPhone(PHONE)
            .withId(ID)
            .withPassword(PASSWORD)
            .withSharingScope(ALL_QUALIFIED_RESEARCHERS)
            .withNotifyByEmail(true)
            .withRoles(DEV_CALLER_ROLES)
            .withDataGroups(APP_DATA_GROUPS)
            .withAttributes(ATTRS)
            .withLanguages(USER_LANGUAGES)
            .withStatus(DISABLED)
            .withTimeZone(USER_TIME_ZONE)
            .withClientData(TestUtils.getClientData())
            .withNote(TEST_NOTE)
            .withClientTimeZone(TEST_CLIENT_TIME_ZONE).build();
    
    private static final DateTime START_DATE = DateTime.now();
    private static final DateTime END_DATE = START_DATE.plusDays(1);
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder()
            .withUserId(ID).withAppId(TEST_APP_ID).build();
    private static final SignIn EMAIL_PASSWORD_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withPhone(TestConstants.PHONE).withPassword(PASSWORD).build();
    private static final SignIn REAUTH_REQUEST = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withReauthToken("ASDF").build();
    
    @Spy
    @InjectMocks
    private ParticipantService participantService;
    
    @Mock
    private AccountService accountService;
    
    @Mock
    private ScheduledActivityDao activityDao;

    @Mock
    private SmsService smsService;

    @Mock
    private SubpopulationService subpopService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private RequestInfoService requestInfoService;
    
    @Mock
    private Subpopulation subpopulation;
    
    @Mock
    private NotificationsService notificationsService;
    
    @Mock
    private ScheduledActivityService scheduledActivityService;
    
    @Mock
    private PagedResourceList<AccountSummary> accountSummaries;
    
    @Mock
    private EnrollmentService enrollmentService;
    
    @Mock
    private AccountWorkflowService accountWorkflowService;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private OrganizationService organizationService;
    
    @Mock
    private TemplateService templateService;

    @Mock
    private AmazonSQSClient sqsClient;

    @Mock
    private BridgeConfig bridgeConfig;
    
    @Mock
    private SendMailService sendMailService;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;

    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<App> appCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;

    @Captor
    ArgumentCaptor<SmsMessageProvider> smsProviderCaptor;
    
    @Captor
    ArgumentCaptor<BasicEmailProvider> emailProviderCaptor;
    
    @Captor
    ArgumentCaptor<Enrollment> enrollmentCaptor;
    
    private Account account;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        APP.setEmailVerificationEnabled(false);
        APP.setAccountLimit(0);

        account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id").withCallerAppId(TEST_APP_ID)
                .withCallerRoles(RESEARCH_CALLER_ROLES).withOrgSponsoredStudies(CALLER_SUBS).build());
    }
    
    @AfterMethod
    public void after() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    private void mockHealthCodeAndAccountRetrieval() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
    }
    
    private void mockHealthCodeAndAccountRetrieval(String email, Phone phone, String externalId) {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        account.setEmail(email);
        account.setEmailVerified(TRUE);
        account.setPhone(phone);
        Set<Enrollment> enrollments = new HashSet<>();
        if (externalId != null) {
            Enrollment enrollment = Enrollment.create(TEST_APP_ID, STUDY_ID, ID, externalId);
            enrollments.add(enrollment);
        }
        account.setEnrollments(enrollments);
        account.setAppId(TEST_APP_ID);
        when(participantService.getAccount()).thenReturn(account);
        when(participantService.generateGUID()).thenReturn(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
    }
    
    private void mockAccountNoEmail() {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
    }
    
    @Test
    public void createParticipant() {
        APP.setEmailVerificationEnabled(true);
        when(participantService.generateGUID()).thenReturn(ID);
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());

        StudyParticipant participant = withParticipant()
                .withExternalIds(ENROLLMENT_MAP)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        IdentifierHolder idHolder = participantService.createParticipant(APP, participant, true);
        assertEquals(idHolder.getIdentifier(), ID);
        
        // suppress email (true) == sendEmail (false)
        verify(accountService).createAccount(eq(APP), accountCaptor.capture());
        verify(accountWorkflowService).sendEmailVerificationToken(APP, ID, EMAIL);
        verify(enrollmentService).addEnrollment(eq(accountCaptor.getValue()), enrollmentCaptor.capture());
        
        Account account = accountCaptor.getValue();
        assertEquals(account.getId(), ID);
        assertEquals(account.getAppId(), APP.getIdentifier());
        // Not healthCode because the mock always returns the ID value, but this is
        // set by calling the generateGUID() method, which is correct.
        assertEquals(account.getHealthCode(), ID);
        assertEquals(account.getEmail(), EMAIL);
        assertFalse(account.getEmailVerified());
        assertEquals(account.getPhone(), PHONE);
        assertFalse(account.getPhoneVerified());
        assertNotNull(account.getPasswordHash());
        assertEquals(account.getPasswordAlgorithm(), PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        assertNotEquals(account.getPasswordHash(), PASSWORD);
        assertEquals(account.getFirstName(), FIRST_NAME);
        assertEquals(account.getLastName(), LAST_NAME);
        assertEquals(account.getAttributes().get("can_be_recontacted"), "true");
        assertEquals(account.getRoles(), DEV_CALLER_ROLES);
        assertEquals(account.getClientData(), TestUtils.getClientData());
        assertEquals(account.getStatus(), AccountStatus.ENABLED); // has external ID and password
        assertEquals(account.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(account.getNotifyByEmail(), Boolean.TRUE);
        assertNull(account.getTimeZone());
        assertEquals(account.getDataGroups(), ImmutableSet.of("group1","group2"));
        assertEquals(account.getLanguages(), ImmutableList.of("de","fr"));
        assertEquals(enrollmentCaptor.getValue().getExternalId(), EXTERNAL_ID);
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
        assertEquals(account.getNote(), TEST_NOTE);
        assertEquals(account.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        
        // don't update cache
        Mockito.verifyNoMoreInteractions(cacheProvider);
    }
    
    @Test
    public void createParticipantSetsOrgMembershipWhenCallerOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        when(participantService.generateGUID()).thenReturn(ID);
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        when(organizationService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));

        StudyParticipant participant = withParticipant()
                .withOrgMembership(TEST_ORG_ID)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        participantService.createParticipant(APP, participant, true);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture());
        
        Account account = accountCaptor.getValue();
        assertEquals(account.getOrgMembership(), TEST_ORG_ID);
    }
    
    @Test
    public void createParticipantSettingOrgMembershipFailsOnWrongRole() {
        when(organizationService.getOrganizationOpt(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(Organization.create()));

        StudyParticipant participant = withParticipant().withOrgMembership(TEST_ORG_ID).build();
        
        try {
            participantService.createParticipant(APP, participant, true);    
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("orgMembership").get(0), "orgMembership cannot be set by caller");
        }
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createParticipantDoesNotAlreadyExistThrowsInvalidEntity() {
        mockHealthCodeAndAccountRetrieval();
        
        AccountId accountId = AccountId.forExternalId(TEST_APP_ID, EXTERNAL_ID);
        when(accountService.getAccount(accountId)).thenReturn(null);
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        
        participantService.createParticipant(APP, participant, true);
    }
    
    @Test
    public void createParticipantWithEnrollment_anonymous() {
        // This works because the request context is mocked in the @Before method, not in
        // mockHealthCodeAndAccountRetrieval (below it)
        RequestContext.set(RequestContext.get().toBuilder().withCallerUserId(null).build());
        mockHealthCodeAndAccountRetrieval();
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());

        StudyParticipant participant = withParticipant().withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(APP, account);
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }

    @Test
    public void createParticipantWithEnrollment_notAnonymous() {
        mockHealthCodeAndAccountRetrieval();
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());

        StudyParticipant participant = withParticipant().withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(APP, account);
        verify(enrollmentService).addEnrollment(eq(account), enrollmentCaptor.capture());
        
        Enrollment en = enrollmentCaptor.getValue();
        assertEquals(en.getAppId(), TEST_APP_ID);
        assertEquals(en.getStudyId(), STUDY_ID);
        assertEquals(en.getAccountId(), ID);
        assertEquals(en.getExternalId(), EXTERNAL_ID);
    }
    
    @Test
    public void createParticipantWithInvalidParticipant() {
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        
        // It doesn't get more invalid than this...
        StudyParticipant participant = new StudyParticipant.Builder().build();
        
        try {
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
        }
        verifyNoMoreInteractions(accountService);
    }
    
    @Test
    public void createParticipantWithExternalIdAndStudyCallerValidates() {
        when(studyService.getStudy(TEST_APP_ID, "study1", false)).thenReturn(Study.create());
        
        // This is a study caller, so the study relationship needs to be enforced
        // when creating a participant. In this case, the relationship is implied by the 
        // external ID but not provided in the externalIds set. It works anyway.
        RequestContext.set(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES)
                .withCallerUserId("id").withCallerEnrolledStudies(ImmutableSet.of("study1")).build());

        StudyParticipant participant = new StudyParticipant.Builder()
                .withExternalIds(ImmutableMap.of("study1", EXTERNAL_ID)).build();
        
        participantService.createParticipant(APP, participant, false);
    }
    
    // Researchers are now global, so tests that researchers align with a study are removed.
    // Use “study coordinator” role to limit access to specific studies.
    
    @Test
    public void createParticipantEmailDisabledNoVerificationWanted() {
        APP.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(APP, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertEquals(account.getEmailVerified(), Boolean.TRUE);
    }
    
    @Test
    public void createParticipantEmailDisabledVerificationWanted() {
        APP.setEmailVerificationEnabled(false);
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withPhone(null).build();
        
        participantService.createParticipant(APP, participant, true);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertEquals(account.getEmailVerified(), Boolean.TRUE);
    }
    
    @Test
    public void createParticipantEmailEnabledNoVerificationWanted() {
        APP.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(APP, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertEquals(account.getEmailVerified(), Boolean.TRUE);
    }
    
    @Test
    public void createParticipantEmailEnabledVerificationWanted() {
        APP.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();

        participantService.createParticipant(APP, PARTICIPANT, true);

        verify(accountWorkflowService).sendEmailVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertFalse(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantAutoVerificationEmailSuppressed() {
        App app = makeStudy();
        app.setEmailVerificationEnabled(true);
        app.setAutoVerificationEmailSuppressed(true);
        mockHealthCodeAndAccountRetrieval();

        participantService.createParticipant(app, PARTICIPANT, true);

        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertFalse(account.getEmailVerified());
    }

    @Test
    public void createParticipantPhoneNoEmailVerificationWanted() {
        APP.setEmailVerificationEnabled(true);
        mockHealthCodeAndAccountRetrieval();
        account.setEmail(null);
        
        // Make minimal phone participant.
        StudyParticipant phoneParticipant = withParticipant().withEmail(null).withPhone(PHONE).build();
        participantService.createParticipant(APP, phoneParticipant, false);

        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertFalse(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantPhoneDisabledNoVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);
        
        participantService.createParticipant(APP, PARTICIPANT, false);
        
        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertEquals(account.getPhoneVerified(), Boolean.TRUE);
    }
    
    @Test
    public void createParticipantPhoneEnabledVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);

        APP.setEmailVerificationEnabled(true);
        participantService.createParticipant(APP, PARTICIPANT, true);

        verify(accountWorkflowService).sendPhoneVerificationToken(APP, ID, PHONE);
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertFalse(account.getPhoneVerified());
    }

    @Test
    public void createParticipantAutoVerificationPhoneSuppressed() {
        App app = makeStudy();
        app.setAutoVerificationPhoneSuppressed(true);
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);

        app.setEmailVerificationEnabled(true);
        participantService.createParticipant(app, PARTICIPANT, true);

        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertFalse(account.getPhoneVerified());
    }

    @Test
    public void createParticipantEmailNoPhoneVerificationWanted() {
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);

        // Make minimal email participant.
        StudyParticipant emailParticipant = withParticipant().withPhone(null).withEmail(EMAIL).build();
        participantService.createParticipant(APP, emailParticipant, false);

        verify(accountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertFalse(account.getPhoneVerified());
    }

    @Test
    public void createPhoneParticipant_OptInPhoneNumber() {
        // Set up and execute test.
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);
        participantService.createParticipant(APP, PARTICIPANT, false);

        // Verify calls to SmsService.
        verify(smsService).optInPhoneNumber(ID, PHONE);
    }

    @Test
    public void createParticipantExternalIdNoPasswordIsUnverified() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());

        StudyParticipant idParticipant = withParticipant().withPhone(null).withEmail(null).withPassword(null)
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, idParticipant, false);
        
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertFalse(account.getPhoneVerified());
        assertFalse(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantExternalIdAndPasswordIsEnabled() {
        mockHealthCodeAndAccountRetrieval(null, null, null);
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        
        when(enrollmentService.addEnrollment(any(), any())).thenAnswer(args -> {
            account.getEnrollments().add(args.getArgument(1));
            return args.getArgument(1);
        });

        StudyParticipant participant = withParticipant().withEmail(null).withPhone(null).withPassword(PASSWORD)
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertFalse(account.getPhoneVerified());
        assertFalse(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantSynapseUserIdIsEnabled() {
        mockHealthCodeAndAccountRetrieval(null, null, null);

        StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
        assertFalse(account.getPhoneVerified());
        assertFalse(account.getEmailVerified());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp=".*is not a study of the caller.*")
    public void createParticipantMustIncludeCallerStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of(STUDY_ID)).build());
        
        when(studyService.getStudy(TEST_APP_ID, "inaccessible-study-to-caller", false)).thenReturn(Study.create());
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalIds(ImmutableMap.of("inaccessible-study-to-caller", EXTERNAL_ID))
                .withRoles(null).build();
        
        participantService.createParticipant(APP, participant, false);
    }
    
    @Test
    public void createSmsNotificationRegistration_PhoneNotVerified() {
        // Mock account w/ email but no phone.
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        account.setPhoneVerified(null);

        // Execute.
        try {
            participantService.createSmsRegistration(APP, ID);
            fail("expected exception");
        } catch (BadRequestException ex) {
            // Verify error message.
            assertTrue(ex.getMessage().contains("user has no verified phone number"));
        }
    }

    @Test
    public void createSmsNotificationRegistration_NoRequestInfo() {
        // Mock account w/ phone.
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);
        account.setPhoneVerified(true);

        // Mock request info to return null.
        when(requestInfoService.getRequestInfo(ID)).thenReturn(null);

        // Execute.
        try {
            participantService.createSmsRegistration(APP, ID);
            fail("expected exception");
        } catch (BadRequestException ex) {
            // Verify error message.
            assertTrue(ex.getMessage().contains("user has no request info"));
        }
    }

    @Test
    public void createSmsNotificationRegistration_NotConsented() {
        // Mock account w/ phone.
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);
        account.setPhoneVerified(true);

        // Mock request info.
        when(requestInfoService.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        // Mock subpop service.
        when(subpopulation.getGuid()).thenReturn(SUBPOP_GUID);
        when(subpopulation.getGuidString()).thenReturn(SUBPOP_GUID.getGuid());
        when(subpopService.getSubpopulations(TEST_APP_ID, false)).thenReturn(
                ImmutableList.of(subpopulation));

        // Mock consent service
        ConsentStatus consentStatus = new ConsentStatus.Builder().withName("My Consent").withGuid(SUBPOP_GUID)
                .withRequired(true).withConsented(false).withSignedMostRecentConsent(false).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(ImmutableMap.of(SUBPOP_GUID, consentStatus));

        // Execute.
        try {
            participantService.createSmsRegistration(APP, ID);
            fail("expected exception");
        } catch (BadRequestException ex) {
            // Verify error message.
            assertTrue(ex.getMessage().contains("user is not consented"));
        }
    }

    @Test
    public void createSmsNotificationRegistration_Success() {
        // Mock account w/ phone.
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);
        account.setPhoneVerified(true);
        account.setDataGroups(TestConstants.USER_DATA_GROUPS);
        Enrollment as1 = Enrollment.create(TEST_APP_ID, "studyA", ID);
        Enrollment as2 = Enrollment.create(TEST_APP_ID, "studyB", ID);
        account.setEnrollments(ImmutableSet.of(as1, as2));

        // Mock request info.
        when(requestInfoService.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        // Mock subpop service.
        when(subpopulation.getGuid()).thenReturn(SUBPOP_GUID);
        when(subpopulation.getGuidString()).thenReturn(SUBPOP_GUID.getGuid());
        when(subpopService.getSubpopulations(TEST_APP_ID, false)).thenReturn(
                ImmutableList.of(subpopulation));

        // Mock consent service
        ConsentStatus consentStatus = new ConsentStatus.Builder().withName("My Consent").withGuid(SUBPOP_GUID)
                .withRequired(true).withConsented(true).withSignedMostRecentConsent(false).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(ImmutableMap.of(SUBPOP_GUID, consentStatus));

        // Execute.
        participantService.createSmsRegistration(APP, ID);

        // Verify.
        ArgumentCaptor<CriteriaContext> criteriaContextCaptor = ArgumentCaptor.forClass(CriteriaContext.class);
        ArgumentCaptor<NotificationRegistration> registrationCaptor = ArgumentCaptor.forClass(
                NotificationRegistration.class);
        verify(notificationsService).createRegistration(eq(TEST_APP_ID), criteriaContextCaptor.capture(),
                registrationCaptor.capture());

        CriteriaContext criteriaContext = criteriaContextCaptor.getValue();
        assertEquals(criteriaContext.getAppId(), TEST_APP_ID);
        assertEquals(criteriaContext.getUserId(), ID);
        assertEquals(criteriaContext.getHealthCode(), HEALTH_CODE);
        assertEquals(criteriaContext.getClientInfo(), CLIENT_INFO);
        assertEquals(criteriaContext.getLanguages(), TestConstants.LANGUAGES);
        assertEquals(criteriaContext.getUserDataGroups(), TestConstants.USER_DATA_GROUPS);
        assertEquals(criteriaContext.getUserStudyIds(), TestConstants.USER_STUDY_IDS);

        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals(registration.getHealthCode(), HEALTH_CODE);
        assertEquals(registration.getProtocol(), NotificationProtocol.SMS);
        assertEquals(registration.getEndpoint(), PHONE.getNumber());
    }

    @Test
    public void getPagedAccountSummaries() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(1100)
                .withPageSize(50)
                .withEmailFilter("foo")
                .withPhoneFilter("bar")
                .withStartTime(START_DATE)
                .withEndTime(END_DATE).build();
        
        participantService.getPagedAccountSummaries(APP, search);
        
        verify(accountService).getPagedAccountSummaries(TEST_APP_ID, search); 
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void getPagedAccountSummariesWithBadStudy() {
        participantService.getPagedAccountSummaries(null, AccountSummarySearch.EMPTY_SEARCH);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithNegativeOffsetBy() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(-1).build();
        participantService.getPagedAccountSummaries(APP, search);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithNegativePageSize() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withPageSize(-100).build();
        participantService.getPagedAccountSummaries(APP, search);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithBadDateRange() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withStartTime(END_DATE).withEndTime(START_DATE).build();
        participantService.getPagedAccountSummaries(APP, search);
    }
    
    @Test
    public void getPagedAccountSummariesWithoutEmailOrPhoneFilterOK() {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(1100).withPageSize(50).build();
        
        participantService.getPagedAccountSummaries(APP, search);
        
        verify(accountService).getPagedAccountSummaries(TEST_APP_ID, search); 
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithTooLargePageSize() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withPageSize(251).build();
        participantService.getPagedAccountSummaries(APP, search);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithInvalidAllOfGroup() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withAllOfGroups(ImmutableSet.of("not_real_group")).build();

        participantService.getPagedAccountSummaries(APP, search);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithInvalidNoneOfGroup() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(ImmutableSet.of("not_real_group")).build();

        participantService.getPagedAccountSummaries(APP, search);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void getPagedAccountSummariesWithConflictingGroups() {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withNoneOfGroups(ImmutableSet.of("group1"))
                .withAllOfGroups(ImmutableSet.of("group1")).build();
        participantService.getPagedAccountSummaries(APP, search);
    }
    
    @Test
    public void getPagedAccountSummariesAddsTestFlagForDevelopers() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("some-id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        
        participantService.getPagedAccountSummaries(APP, search);
        
        verify(accountService).getPagedAccountSummaries(
                eq(TEST_APP_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getAllOfGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }
    
    @Test
    public void getPagedAccountSummariesAddsTestFlagForStudyDesigners() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("some-id")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        
        participantService.getPagedAccountSummaries(APP, search);
        
        verify(accountService).getPagedAccountSummaries(
                eq(TEST_APP_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getAllOfGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }
    
    @Test
    public void getPagedAccountSummariesAddsTestFlagForOrgAdmins() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("some-id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        
        participantService.getPagedAccountSummaries(APP, search);
        
        verify(accountService).getPagedAccountSummaries(
                eq(TEST_APP_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getAllOfGroups(), ImmutableSet.of(TEST_USER_GROUP));
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantEmailDoesNotExist() {
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        
        participantService.getParticipant(APP, ID, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantAccountIdDoesNotExist() {
        participantService.getParticipant(APP, "externalId:some-junk", false);
    }
    
    // getParticiantAccountFilteredOutByStudyAssocation removed because all accounts are
    // now filtered in AccountService, and only in AccountService.
    
    @Test
    public void getSelfParticipantWithHistory() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(ID)
                .withOrgSponsoredStudies(TestConstants.USER_STUDY_IDS).build());
        
        // Some data to verify
        account.setId(ID);
        account.setLastName("lastName");
        Set<Enrollment> enrollments = new HashSet<>();
        for (String studyId : ImmutableList.of("studyA", "studyB", "studyC")) {
            Enrollment enrollment = Enrollment.create(APP.getIdentifier(), studyId, ID);
            enrollments.add(enrollment);
        }
        account.setEnrollments(enrollments);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create("foo1");
        account.setConsentSignatureHistory(subpopGuid, ImmutableList.of(new ConsentSignature.Builder()
                .withConsentCreatedOn(START_DATE.getMillis()).build()));
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        when(consentService.getConsentStatuses(CONTEXT, account)).thenReturn(TestConstants.CONSENTED_STATUS_MAP);
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SubpopulationGuid.create("foo1"));
        List<Subpopulation> subpops = ImmutableList.of(subpop);
        when(subpopService.getSubpopulations(TEST_APP_ID, false)).thenReturn(subpops);
        when(subpopService.getSubpopulation(TEST_APP_ID, subpopGuid)).thenReturn(subpop);
        
        StudyParticipant retrieved = participantService.getSelfParticipant(APP, CONTEXT, true);
        
        assertEquals(retrieved.getId(), CONTEXT.getUserId());
        assertEquals(retrieved.getLastName(), "lastName");
        // These have been filtered
        assertEquals(retrieved.getStudyIds(), ImmutableSet.of("studyA", "studyB", "studyC"));
        // Consent was calculated
        assertTrue(retrieved.isConsented());
        // There is history
        UserConsentHistory history = retrieved.getConsentHistories().get(subpopGuid.getGuid()).get(0);
        assertEquals(history.getConsentCreatedOn(), START_DATE.getMillis());
    }
    
    @Test
    public void getSelfParticipantNoHistory() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(USER_STUDY_IDS).build());
        
        // Some data to verify
        account.setId(ID);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        Set<Enrollment> enrollments = new HashSet<>();
        DateTime ts = ENROLLMENT;
        for (String studyId : ImmutableList.of("studyA", "studyB", "studyC")) {
            Enrollment enrollment = Enrollment.create(APP.getIdentifier(), studyId, ID);
            enrollment.setEnrolledOn(ts);
            enrollments.add(enrollment);
            ts = ts.plusDays(1);
        }
        account.setEnrollments(enrollments);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create("foo1");
        account.setConsentSignatureHistory(subpopGuid, ImmutableList.of(new ConsentSignature.Builder()
                .withConsentCreatedOn(START_DATE.getMillis()).build()));
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        when(consentService.getConsentStatuses(CONTEXT, account)).thenReturn(TestConstants.CONSENTED_STATUS_MAP);
        
        StudyParticipant retrieved = participantService.getSelfParticipant(APP, CONTEXT, false);
        
        assertEquals(retrieved.getId(), CONTEXT.getUserId());
        assertEquals(retrieved.getSynapseUserId(), SYNAPSE_USER_ID);
        // These have been filtered
        assertEquals(retrieved.getStudyIds(), ImmutableSet.of("studyA", "studyB", "studyC"));
        // Consent was calculated
        assertTrue(retrieved.isConsented());
        // There is no history
        assertTrue(retrieved.getConsentHistories().isEmpty());
        assertEquals(retrieved.getEnrollments().get("studyA").getEnrolledOn(), ENROLLMENT);
        assertEquals(retrieved.getEnrollments().get("studyB").getEnrolledOn(), ENROLLMENT.plusDays(1));
        assertEquals(retrieved.getEnrollments().get("studyC").getEnrolledOn(), ENROLLMENT.plusDays(2));
    }
    
    @Test
    public void getParticipant() {
        when(participantService.getAccount()).thenReturn(account);
        when(participantService.generateGUID()).thenReturn(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // A lot of mocks have to be set up first, this call aggregates almost everything we know about the user
        DateTime createdOn = DateTime.now();
        account.setHealthCode(HEALTH_CODE);
        account.setAppId(APP.getIdentifier());
        account.setId(ID);
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setCreatedOn(createdOn);
        account.setFirstName(FIRST_NAME);
        account.setLastName(LAST_NAME);
        account.setEmailVerified(Boolean.TRUE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setStatus(AccountStatus.DISABLED);
        account.getAttributes().put("attr2", "anAttribute2");
        List<ConsentSignature> sigs1 = Lists.newArrayList(new ConsentSignature.Builder()
                .withName("Name 1").withBirthdate("1980-01-01").build());
        account.setConsentSignatureHistory(SUBPOP_GUID_1, sigs1);
        account.setClientData(TestUtils.getClientData());
        account.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        account.setNotifyByEmail(Boolean.TRUE);
        account.setDataGroups(TestUtils.newLinkedHashSet("group1","group2"));
        account.setLanguages(USER_LANGUAGES);
        account.setTimeZone(USER_TIME_ZONE);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", ID, "externalIdA");
        en1.setEnrolledOn(ENROLLMENT);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", ID, "externalIdB");
        en2.setEnrolledOn(ENROLLMENT.plusDays(1));
        Enrollment en3 = Enrollment.create(TEST_APP_ID, "studyC", ID);
        en3.setEnrolledOn(ENROLLMENT.plusDays(2));

        // no third external ID, this one is just not in the external IDs map
        account.setEnrollments(ImmutableSet.of(en1, en2, en3));
        account.setOrgMembership(TEST_ORG_ID);
        account.setNote(TEST_NOTE);
        account.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        
        List<Subpopulation> subpopulations = Lists.newArrayList();
        // Two subpopulations for mocking.
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setGuidString("guid1");
        subpop1.setPublishedConsentCreatedOn(CONSENT_PUBLICATION_DATE);
        subpopulations.add(subpop1);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setGuidString("guid2");
        subpop2.setPublishedConsentCreatedOn(CONSENT_PUBLICATION_DATE);
        
        subpopulations.add(subpop2);
        when(subpopService.getSubpopulations(TEST_APP_ID, false)).thenReturn(subpopulations);

        when(subpopService.getSubpopulation(TEST_APP_ID, SUBPOP_GUID_1)).thenReturn(subpop1);

        // Mock CacheProvider to return request info.
        when(requestInfoService.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        // Mock ConsentService to return consent statuses for criteria.
        ConsentStatus consentStatus1 = new ConsentStatus.Builder().withName("consent1").withGuid(SUBPOP_GUID_1)
                .withRequired(true).withConsented(true).withSignedMostRecentConsent(true).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(
                ImmutableMap.of(SUBPOP_GUID_1, consentStatus1));
        
        // Get the fully initialized participant object (including histories)
        StudyParticipant participant = participantService.getParticipant(APP, ID, true);

        assertTrue(participant.isConsented());
        assertEquals(participant.getFirstName(), FIRST_NAME);
        assertEquals(participant.getLastName(), LAST_NAME);
        assertTrue(participant.isNotifyByEmail());
        assertEquals(participant.getDataGroups(), ImmutableSet.of("group1","group2"));
        assertTrue(collectExternalIds(account).contains("externalIdA"));
        assertTrue(collectExternalIds(account).contains("externalIdB"));
        assertEquals(participant.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(participant.getHealthCode(), HEALTH_CODE);
        assertEquals(participant.getEmail(), EMAIL);
        assertEquals(participant.getPhone().getNationalFormat(), PHONE.getNationalFormat());
        assertEquals(participant.getEmailVerified(), Boolean.TRUE);
        assertEquals(participant.getPhoneVerified(), Boolean.FALSE);
        assertEquals(participant.getId(), ID);
        assertEquals(participant.getStatus(), AccountStatus.DISABLED);
        assertEquals(participant.getCreatedOn(), createdOn);
        assertEquals(participant.getTimeZone(), USER_TIME_ZONE);
        assertEquals(participant.getLanguages(), USER_LANGUAGES);
        assertEquals(participant.getClientData(), TestUtils.getClientData());
        assertEquals(participant.getSynapseUserId(), SYNAPSE_USER_ID);
        assertEquals(participant.getExternalIds().size(), 2);
        assertEquals(participant.getExternalIds().get("studyA"), "externalIdA");
        assertEquals(participant.getExternalIds().get("studyB"), "externalIdB");
        assertEquals(participant.getOrgMembership(), TEST_ORG_ID);
        assertEquals(participant.getNote(), TEST_NOTE);
        assertEquals(participant.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        EnrollmentInfo detailA = participant.getEnrollments().get("studyA");
        assertEquals(detailA.getExternalId(), "externalIdA");
        assertEquals(detailA.getEnrolledOn(), ENROLLMENT);
        EnrollmentInfo detailB = participant.getEnrollments().get("studyB");
        assertEquals(detailB.getExternalId(), "externalIdB");
        assertEquals(detailB.getEnrolledOn(), ENROLLMENT.plusDays(1));
        EnrollmentInfo detailC = participant.getEnrollments().get("studyC");
        assertNull(detailC.getExternalId());
        assertEquals(detailC.getEnrolledOn(), ENROLLMENT.plusDays(2));
        
        assertNull(participant.getAttributes().get("attr1"));
        assertEquals(participant.getAttributes().get("attr2"), "anAttribute2");
        
        List<UserConsentHistory> retrievedHistory1 = participant.getConsentHistories().get(subpop1.getGuidString());
        assertEquals(retrievedHistory1.size(), 1);
        
        List<UserConsentHistory> retrievedHistory2 = participant.getConsentHistories().get(subpop2.getGuidString());
        assertTrue(retrievedHistory2.isEmpty());

        // Verify context passed to consent service.
        ArgumentCaptor<CriteriaContext> criteriaContextCaptor = ArgumentCaptor.forClass(CriteriaContext.class);
        verify(consentService).getConsentStatuses(criteriaContextCaptor.capture(), same(account));

        CriteriaContext criteriaContext = criteriaContextCaptor.getValue();
        assertEquals(criteriaContext.getAppId(), TEST_APP_ID);
        assertEquals(criteriaContext.getUserId(), ID);
        assertEquals(criteriaContext.getHealthCode(), HEALTH_CODE);
        assertEquals(criteriaContext.getClientInfo(), CLIENT_INFO);
        assertEquals(criteriaContext.getLanguages(), TestConstants.LANGUAGES);
        assertEquals(criteriaContext.getUserDataGroups(), TestConstants.USER_DATA_GROUPS);
        assertEquals(criteriaContext.getUserStudyIds(), ImmutableSet.of("studyA", "studyB", "studyC"));
    }
    
    @Test
    public void getParticipantFilteringStudiesAndExternalIds() {
        // There is a partial overlap of study memberships between caller and user, the studies that are 
        // not in the intersection, and the external IDs, should be removed from the participant
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE, null);
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", ID, "externalIdA");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", ID, "externalIdB");
        Enrollment en3 = Enrollment.create(TEST_APP_ID, "studyC", ID);
        // no third external ID, this one is just not in the external IDs map
        account.getEnrollments().addAll(ImmutableSet.of(en1, en2, en3));
        
        // Now, the caller only sees A and C
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("studyA", "studyC")).build());
        
        StudyParticipant participant = participantService.getParticipant(APP, ID, false);
        assertEquals(participant.getStudyIds(), ImmutableSet.of("studyA", "studyC"));
        assertEquals(participant.getExternalIds(), ImmutableMap.of("studyA", "externalIdA"));
    }

    @Test
    public void getStudyStartTime_FromActivitiesRetrieved() {
        // Set up mocks.
        when(activityEventService.getActivityEventMap(APP.getIdentifier(), HEALTH_CODE)).thenReturn(ImmutableMap.of(
                ActivityEventObjectType.ACTIVITIES_RETRIEVED.name().toLowerCase(), ACTIVITIES_RETRIEVED_DATETIME));

        // Execute and validate.
        DateTime result = participantService.getStudyStartTime(account);
        assertEquals(result, ACTIVITIES_RETRIEVED_DATETIME);
    }

    @Test
    public void getStudyStartTime_FromEnrollment() {
        // Set up mocks.
        when(activityEventService.getActivityEventMap(APP.getIdentifier(), HEALTH_CODE)).thenReturn(ImmutableMap.of(
                ActivityEventObjectType.ENROLLMENT.name().toLowerCase(), ENROLLMENT_DATETIME));

        // Execute and validate.
        DateTime result = participantService.getStudyStartTime(account);
        assertEquals(result, ENROLLMENT_DATETIME);
    }

    @Test
    public void getStudyStartTime_FromAccountCreatedOn() {
        // Set up mocks.
        account.setCreatedOn(CREATED_ON_DATETIME);
        when(activityEventService.getActivityEventMap(APP.getIdentifier(), HEALTH_CODE)).thenReturn(ImmutableMap.of());

        // Execute and validate.
        DateTime result = participantService.getStudyStartTime(account);
        assertEquals(result, CREATED_ON_DATETIME);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void signOutUserWhoDoesNotExist() {
        participantService.signUserOut(APP, ID, true);
    }

    @Test
    public void signOutUser() {
        // Need to look this up by email, not account ID
        AccountId accountId = AccountId.forId(APP.getIdentifier(), ID);
        
        // Setup
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        account.setId(ID);

        // Execute
        participantService.signUserOut(APP, ID, false);

        // Verify
        verify(accountService).getAccount(accountId);
        verify(accountService, never()).deleteReauthToken(any());
        verify(cacheProvider).removeSessionByUserId(ID);
    }

    @Test
    public void signOutUserDeleteReauthToken() {
        // Need to look this up by email, not account ID
        AccountId accountId = AccountId.forId(APP.getIdentifier(), ID);
        
        // Setup
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        account.setId(ID);

        // Execute
        participantService.signUserOut(APP, ID, true);

        // Verify
        verify(accountService).getAccount(accountId);
        verify(accountService).deleteReauthToken(account);
        verify(cacheProvider).removeSessionByUserId(ID);
    }

    @Test
    public void updateParticipantWithExternalIdValidationAddingId() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES).build());
        
        mockHealthCodeAndAccountRetrieval(null, null, null);

        StudyParticipant participant = withParticipant()
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        
        Account account = accountCaptor.getValue();
        assertEquals(account.getFirstName(), FIRST_NAME);
        assertEquals(account.getLastName(), LAST_NAME);
        assertEquals(account.getAttributes().get("can_be_recontacted"), "true");
        assertEquals(account.getClientData(), TestUtils.getClientData());
        
        assertEquals(account.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(account.getNotifyByEmail(), Boolean.TRUE);
        assertEquals(account.getDataGroups(), ImmutableSet.of("group1","group2"));
        assertEquals(account.getLanguages(), ImmutableList.of("de","fr"));
        assertNull(account.getTimeZone());
    }

    @Test
    public void updateParticipantWithSameExternalIdDoesntEnrollUser() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }
    
    // The exception here results from the fact that the caller can't see the existance of the 
    // participant, because the study IDs don't overlap
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateParticipantMustIncludeCallerStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of(STUDY_ID)).build());

        participantService.updateParticipant(APP, PARTICIPANT);
    }
    
    // Researchers are now global, so tests that researchers align with a study are removed.
    // Use “study coordinator” role to limit access to specific studies.
    
    @Test
    public void updateParticipantWithoutStudyChangesForAdmins() {
        Set<String> studies = ImmutableSet.of("studyA", "studyC");
        StudyParticipant participant = mockStudiesInRequest(studies, studies, ADMIN).build();
        
        mockHealthCodeAndAccountRetrieval();
        account.getEnrollments().add(Enrollment.create(APP.getIdentifier(), "studyC", ID));
        account.getEnrollments().add(Enrollment.create(APP.getIdentifier(), "studyA", ID));
        
        participantService.updateParticipant(APP, participant);
        
        // We've tested this collection more thoroughly in updateParticipantTransfersStudyIdsForAdmins()
        verify(cacheProvider, never()).removeSessionByUserId(any());
    }
    
    @Test
    public void updateParticipantDoesNotUpdateImmutableFields() {
        mockHealthCodeAndAccountRetrieval(null, null, null);
        account.setEmailVerified(null);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        RequestContext.set(new RequestContext.Builder().build());
 
        // There's a long list of fields you cannot update, set them all: 
        StudyParticipant participant = withParticipant()
                .withId(ID)
                .withEmail("asdf")
                .withPhone(new Phone("1234567890", "US"))
                .withEmailVerified(true)
                .withPhoneVerified(true)
                .withSynapseUserId("asdf")
                .withPassword("asdf")
                .withHealthCode("asdf")
                .withConsented(true)
                .withRoles(ImmutableSet.of(ADMIN))
                .withStatus(DISABLED)
                .withCreatedOn(DateTime.now())
                .withTimeZone(UTC).build();
        participantService.updateParticipant(APP, participant);
        
        assertNull(account.getEmail());
        assertNull(account.getPhone());
        assertNull(account.getEmailVerified());
        assertNull(account.getPhoneVerified());
        assertNull(account.getSynapseUserId());
        assertEquals(account.getHealthCode(), HEALTH_CODE);
        assertTrue(account.getRoles().isEmpty());
        assertEquals(account.getStatus(), UNVERIFIED);
        assertNull(account.getCreatedOn());
        assertNull(account.getTimeZone());
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateParticipantWithInvalidParticipant() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withDataGroups(ImmutableSet.of("bogusGroup")).build();
        participantService.updateParticipant(APP, participant);
    }
    
    @Test
    public void updateParticipantWithNoAccount() {
        doThrow(new EntityNotFoundException(Account.class)).when(accountService).getAccount(ACCOUNT_ID);
        try {
            participantService.updateParticipant(APP, PARTICIPANT);
            fail("Should have thrown exception.");
        } catch(EntityNotFoundException e) {
        }
        verify(accountService, never()).updateAccount(any());
        verifyNoMoreInteractions(enrollmentService);
    }
    
    @Test
    public void userCannotChangeStatus() {
        verifyStatusUpdate(EnumSet.noneOf(Roles.class), false);
    }
    
    @Test
    public void developerCannotChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(DEVELOPER), false);
    }
    
    @Test
    public void researcherCannotChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(RESEARCHER), false);
    }
    
    @Test
    public void adminCanChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(ADMIN), true);
    }

    @Test
    public void superadminCanChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(SUPERADMIN), true);
    }
    
    @Test
    public void workerCanChangeStatusOnEdit() {
        verifyStatusUpdate(EnumSet.of(WORKER), true);
    }

    @Test
    public void notSettingStatusDoesntClearStatus() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        mockHealthCodeAndAccountRetrieval();
        account.setStatus(AccountStatus.ENABLED);

        StudyParticipant participant = withParticipant().withStatus(null).build();

        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        assertEquals(account.getStatus(), AccountStatus.ENABLED);
    }

    @Test
    public void userCannotCreateAnybody() {
        verifyRoleCreate(ImmutableSet.of(), ImmutableSet.of());
    }

    @Test
    public void workerCannotCreateAnybody() {
        verifyRoleCreate(ImmutableSet.of(WORKER), ImmutableSet.of());
    }
    
    @Test
    public void developerCannotCreateAnybody() {
        verifyRoleCreate(ImmutableSet.of(DEVELOPER), ImmutableSet.of());
    }
    
    @Test
    public void researcherCanCreateDevelopers() {
        verifyRoleCreate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(DEVELOPER));
    }
    
    @Test
    public void adminCanCreateDeveloperAndResearcher() {
        verifyRoleCreate(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void superadminCanCreateEverybody() {
        verifyRoleCreate(ImmutableSet.of(SUPERADMIN), 
                ImmutableSet.of(SUPERADMIN, ADMIN, DEVELOPER, RESEARCHER, WORKER));
    }
    
    @Test
    public void workerCannotUpdateAnybody() {
        verifyRoleUpdate(ImmutableSet.of(WORKER), ImmutableSet.of());
    }
    
    @Test
    public void developerCannotUpdateAnybody() {
        verifyRoleUpdate(ImmutableSet.of(DEVELOPER), ImmutableSet.of());
    }
    
    @Test
    public void researcherCanUpdateDevelopers() {
        verifyRoleUpdate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(DEVELOPER));
    }
    
    @Test
    public void adminCanUpdateDeveloperAndResearcher() {
        verifyRoleUpdate(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void superadminCanUpdateEverybody() {
        verifyRoleUpdate(ImmutableSet.of(SUPERADMIN), 
                ImmutableSet.of(SUPERADMIN, ADMIN, DEVELOPER, RESEARCHER, WORKER));
    }

    // Now, verify that roles cannot *remove* roles they don't have permissions to remove
    
    @Test
    public void superadminCanRemoveSuperadmin() {
        account.setRoles(ImmutableSet.of(SUPERADMIN));
        verifyRoleUpdate(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(), ImmutableSet.of());
    }
    
    @Test
    public void superadminCanRemoveAdmin() {
        account.setRoles(ImmutableSet.of(ADMIN));
        verifyRoleUpdate(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(), ImmutableSet.of());
    }

    @Test
    public void superadminCanRemoveResearcher() {
        account.setRoles(ImmutableSet.of(RESEARCHER));
        verifyRoleUpdate(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(), ImmutableSet.of());
    }

    @Test
    public void superadminCanRemoveDeveloper() {
        account.setRoles(ImmutableSet.of(DEVELOPER));
        verifyRoleUpdate(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(), ImmutableSet.of());
    }
    
    @Test
    public void superadminCanRemoveWorker() {
        account.setRoles(ImmutableSet.of(WORKER));
        verifyRoleUpdate(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(), ImmutableSet.of());
    }
    
    @Test
    public void adminCannotRemoveSuperadmin() {
        account.setRoles(ImmutableSet.of(SUPERADMIN));
        verifyRoleUpdate(ImmutableSet.of(ADMIN), ImmutableSet.of(), ImmutableSet.of(SUPERADMIN));
    }
    
    @Test
    public void adminCannotRemoveAdmin() {
        account.setRoles(ImmutableSet.of(ADMIN));
        verifyRoleUpdate(ImmutableSet.of(ADMIN), ImmutableSet.of(), ImmutableSet.of(ADMIN));
    }

    @Test
    public void adminCanRemoveResearcher() {
        account.setRoles(ImmutableSet.of(RESEARCHER));
        verifyRoleUpdate(ImmutableSet.of(ADMIN), ImmutableSet.of(), ImmutableSet.of());
    }

    @Test
    public void adminCanRemoveDeveloper() {
        account.setRoles(ImmutableSet.of(DEVELOPER));
        verifyRoleUpdate(ImmutableSet.of(ADMIN), ImmutableSet.of(), ImmutableSet.of());
    }    
    
    @Test
    public void adminCannotRemoveWorker() {
        account.setRoles(ImmutableSet.of(WORKER));
        verifyRoleUpdate(ImmutableSet.of(ADMIN), ImmutableSet.of(), ImmutableSet.of(WORKER));
    }
    
    @Test
    public void researcherCannotRemoveSuperadmin() {
        account.setRoles(ImmutableSet.of(SUPERADMIN));
        verifyRoleUpdate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(), ImmutableSet.of(SUPERADMIN));
    }
    
    @Test
    public void researcherCannotRemoveAdmin() {
        account.setRoles(ImmutableSet.of(ADMIN));
        verifyRoleUpdate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(), ImmutableSet.of(ADMIN));
    }

    @Test
    public void researcherCanRemoveResearcher() {
        account.setRoles(ImmutableSet.of(RESEARCHER));
        verifyRoleUpdate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(), ImmutableSet.of(RESEARCHER));
    }

    @Test
    public void researcherCanRemoveDeveloper() {
        account.setRoles(ImmutableSet.of(DEVELOPER));
        verifyRoleUpdate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(), ImmutableSet.of());
    }
    
    @Test
    public void researcherCanRemoveWorker() {
        account.setRoles(ImmutableSet.of(WORKER));
        verifyRoleUpdate(ImmutableSet.of(RESEARCHER), ImmutableSet.of(), ImmutableSet.of(WORKER));
    }
    
    @Test
    public void developerCannotRemoveAnybody() {
        account.setRoles(ImmutableSet.of(DEVELOPER, RESEARCHER, ADMIN, SUPERADMIN, WORKER));
        verifyRoleUpdate(ImmutableSet.of(DEVELOPER), ImmutableSet.of(), 
                ImmutableSet.of(DEVELOPER, RESEARCHER, ADMIN, SUPERADMIN, WORKER));
    }     

    @Test
    public void getParticipantWithoutHistories() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = participantService.getParticipant(APP, ID, false);

        assertTrue(participant.getConsentHistories().keySet().isEmpty());
        assertNull(participant.isConsented());
    }

    @Test
    public void getParticipantWithHealthCode() {
        String id = "healthCode:" + ID;
        AccountId accountId = AccountId.forHealthCode(APP.getIdentifier(), ID);
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        StudyParticipant participant = participantService.getParticipant(APP, id, true);
        assertNotNull(participant);
        
        verify(accountService).getAccount(accountIdCaptor.capture());
        assertEquals(accountIdCaptor.getValue().getAppId(), APP.getIdentifier());
        assertEquals(accountIdCaptor.getValue().getHealthCode(), ID);
    }
    
    @Test
    public void getParticipantWithExternalId() {
        String id = "externalId:" + ID;
        AccountId accountId = AccountId.forExternalId(APP.getIdentifier(), ID);
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        StudyParticipant participant = participantService.getParticipant(APP, id, true);
        assertNotNull(participant);
        
        verify(accountService).getAccount(accountIdCaptor.capture());
        assertEquals(accountIdCaptor.getValue().getAppId(), APP.getIdentifier());
        assertEquals(accountIdCaptor.getValue().getExternalId(), ID);
    }
    
    @Test
    public void getParticipantWithStringId() {
        AccountId accountId = AccountId.forId(APP.getIdentifier(), ID);
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        StudyParticipant participant = participantService.getParticipant(APP, ID, true);
        assertNotNull(participant);
        
        verify(accountService).getAccount(accountIdCaptor.capture());
        assertEquals(accountIdCaptor.getValue().getAppId(), APP.getIdentifier());
        assertEquals(accountIdCaptor.getValue().getId(), ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantByAccountIdThrowsException() {
        participantService.getParticipant(APP, ID, true);
    }
    
    @Test
    public void getParticipantWithHistories() {
        mockHealthCodeAndAccountRetrieval();
        
        doReturn(APP.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(TEST_APP_ID, false);
        
        StudyParticipant participant = participantService.getParticipant(APP, ID, true);

        assertEquals(participant.getConsentHistories().keySet().size(), 1);
    }

    @Test
    public void getParticipantIsConsentedWithoutRequestInfo() {
        // Set up mocks.
        mockHealthCodeAndAccountRetrieval();

        doReturn(APP.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(TEST_APP_ID, false);

        // Execute and validate
        StudyParticipant participant = participantService.getParticipant(APP, ID, true);
        assertNull(participant.isConsented());
    }

    @Test
    public void getParticipantIsConsentedFalse() {
        // Set up mocks.
        mockHealthCodeAndAccountRetrieval();

        doReturn(APP.getIdentifier()).when(subpopulation).getGuidString();
        doReturn(SUBPOP_GUID).when(subpopulation).getGuid();
        doReturn(Lists.newArrayList(subpopulation)).when(subpopService).getSubpopulations(TEST_APP_ID, false);

        when(requestInfoService.getRequestInfo(ID)).thenReturn(REQUEST_INFO);

        ConsentStatus consentStatus1 = new ConsentStatus.Builder().withName("consent1").withGuid(SUBPOP_GUID)
                .withRequired(true).withConsented(false).withSignedMostRecentConsent(false).build();
        when(consentService.getConsentStatuses(any(), any())).thenReturn(
                ImmutableMap.of(SUBPOP_GUID_1, consentStatus1));

        // Execute and validate
        StudyParticipant participant = participantService.getParticipant(APP, ID, true);
        assertFalse(participant.isConsented());
    }

    @Test
    public void getParticipantWithAccount() throws Exception {
        mockHealthCodeAndAccountRetrieval();
        account.setClientData(TestUtils.getClientData());
        
        StudyParticipant participant = participantService.getParticipant(APP, account, false);
        
        // The most important thing here is that participant includes health code
        assertEquals(participant.getHealthCode(), HEALTH_CODE);
        // Other fields exist too, but getParticipant() is tested in its entirety earlier in this test.
        assertEquals(participant.getEmail(), EMAIL);
        assertEquals(participant.getId(), ID);
        assertEquals(participant.getClientData(), TestUtils.getClientData());
    }

    // Contrived test case for a case that never happens, but somehow does.
    // See https://sagebionetworks.jira.com/browse/BRIDGE-1463
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantWithoutAccountThrows404() {
        participantService.getParticipant(APP, (Account) null, false);
    }

    @Test
    public void requestResetPassword() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.requestResetPassword(APP, ID);
        
        verify(accountWorkflowService).requestResetPassword(APP, true, ACCOUNT_ID);
    }
    
    @Test
    public void requestResetPasswordNoAccountIsSilent() {
        participantService.requestResetPassword(APP, ID);
        
        verifyNoMoreInteractions(accountService);
    }
    
    @Test
    public void canGetActivityHistoryV2WithAllValues() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityHistory(APP, ID, ACTIVITY_GUID, START_DATE, END_DATE, PAGED_BY, PAGE_SIZE);

        verify(scheduledActivityService).getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, START_DATE, END_DATE, PAGED_BY,
                PAGE_SIZE);
    }
    
    @Test
    public void canGetActivityHistoryV2WithDefaults() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityHistory(APP, ID, ACTIVITY_GUID, null, null, null, PAGE_SIZE);

        verify(scheduledActivityService).getActivityHistory(HEALTH_CODE, ACTIVITY_GUID, null, null, null, PAGE_SIZE);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getActivityHistoryV2NoUserThrowsCorrectException() {
        participantService.getActivityHistory(APP, ID, ACTIVITY_GUID, null, null, null, PAGE_SIZE);
    }
    
    @Test
    public void deleteActivities() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.deleteActivities(APP, ID);
        
        verify(activityDao).deleteActivitiesForUser(HEALTH_CODE);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteActivitiesNoUserThrowsCorrectException() {
        participantService.deleteActivities(APP, ID);
    }
    
    @Test
    public void resendEmailVerification() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.resendVerification(APP, ChannelType.EMAIL, ID);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.EMAIL), accountIdCaptor.capture());
        
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(accountId.getAppId(), APP.getIdentifier());
        assertEquals(accountId.getEmail(), EMAIL);
    }
    
    @Test
    public void resendPhoneVerification() {
        mockHealthCodeAndAccountRetrieval(null, PHONE, null);
        
        participantService.resendVerification(APP, ChannelType.PHONE, ID);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.PHONE), accountIdCaptor.capture());
        
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(accountId.getAppId(), APP.getIdentifier());
        assertEquals(accountId.getPhone(), PHONE);
    }
    
    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void resendVerificationUnsupportedOperationException() {
        mockHealthCodeAndAccountRetrieval();
        
        // Use null so we don't have to create a dummy unsupported channel type
        participantService.resendVerification(APP, null, ID);
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Email address has not been set.")
    public void resendEmailVerificationWhenEmailNull() {
        mockHealthCodeAndAccountRetrieval();
        account.setEmail(null);
        
        participantService.resendVerification(APP, ChannelType.EMAIL, ID);    
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Phone number has not been set.")
    public void resendPhoneVerificationWhenEmailNull() {
        mockHealthCodeAndAccountRetrieval();
        account.setPhone(null);
        
        participantService.resendVerification(APP, ChannelType.PHONE, ID);    
    }

    @Test
    public void resendConsentAgreement() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.resendConsentAgreement(APP, SUBPOP_GUID, ID);
        
        verify(consentService).resendConsentAgreement(eq(APP), eq(SUBPOP_GUID), participantCaptor.capture());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getId(), ID);
    }
    
    @Test
    public void withdrawAllConsents() {
        mockHealthCodeAndAccountRetrieval();
        
        Withdrawal withdrawal = new Withdrawal("Reasons");
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawFromApp(APP, ID, withdrawal, withdrewOn);
        
        verify(consentService).withdrawFromApp(eq(APP), participantCaptor.capture(),
            eq(withdrawal), eq(withdrewOn));
        assertEquals(participantCaptor.getValue().getId(), ID);
    }
    
    @Test
    public void withdrawConsent() {
        mockHealthCodeAndAccountRetrieval();
        
        Withdrawal withdrawal = new Withdrawal("Reasons");
        long withdrewOn = DateTime.now().getMillis();
        
        participantService.withdrawConsent(APP, ID, SUBPOP_GUID, withdrawal, withdrewOn);
        
        verify(consentService).withdrawConsent(eq(APP), eq(SUBPOP_GUID), participantCaptor.capture(),
                contextCaptor.capture(), eq(withdrawal), eq(withdrewOn));
        assertEquals(participantCaptor.getValue().getId(), ID);
        assertEquals(contextCaptor.getValue().getUserId(), ID);
    }
    
    @Test
    public void getUploads() {
        mockHealthCodeAndAccountRetrieval();
        DateTime startTime = DateTime.parse("2015-11-01T00:00:00.000Z");
        DateTime endTime = DateTime.parse("2015-11-01T23:59:59.999Z");
        
        participantService.getUploads(APP, ID, startTime, endTime, 10, "ABC");
        
        verify(uploadService).getUploads(HEALTH_CODE, startTime, endTime, 10, "ABC");
    }
    
    @Test
    public void getUploadsWithoutDates() {
        // Just verify this throws no exceptions
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getUploads(APP, ID, null, null, 10, null);
        
        verify(uploadService).getUploads(HEALTH_CODE, null, null, 10, null);
    }
    
    @Test
    public void listNotificationRegistrations() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.listRegistrations(APP, ID);
        
        verify(notificationsService).listRegistrations(HEALTH_CODE);
    }
    
    @Test
    public void sendNotification() {
        mockHealthCodeAndAccountRetrieval();
        
        Set<String> erroredNotifications = ImmutableSet.of("ABC");
        NotificationMessage message = TestUtils.getNotificationMessage();
        
        when(notificationsService.sendNotificationToUser(any(), any(), any())).thenReturn(erroredNotifications);
        
        Set<String> returnedErrors = participantService.sendNotification(APP, ID, message);
        assertEquals(returnedErrors, erroredNotifications);
        
        verify(notificationsService).sendNotificationToUser(TEST_APP_ID, HEALTH_CODE, message);
    }

    @Test
    public void limitNotExceededException() {
        mockHealthCodeAndAccountRetrieval();
        APP.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(9);
        when(accountService.getPagedAccountSummaries(TEST_APP_ID, EMPTY_SEARCH))
                .thenReturn(accountSummaries);
        
        participantService.createParticipant(APP, PARTICIPANT, false);
    }
    
    @Test
    public void throwLimitExceededExactlyException() {
        APP.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(10);
        when(accountService.getPagedAccountSummaries(TEST_APP_ID, EMPTY_SEARCH)).thenReturn(accountSummaries);
        
        try {
            participantService.createParticipant(APP, PARTICIPANT, false);
            fail("Should have thrown exception");
        } catch(LimitExceededException e) {
            assertEquals(e.getMessage(), "While app is in evaluation mode, it may not exceed 10 accounts.");
        }
    }
    
    @Test(expectedExceptions = LimitExceededException.class)
    public void throwLimitExceededException() {
        APP.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(13);
        when(accountService.getPagedAccountSummaries(TEST_APP_ID, EMPTY_SEARCH)).thenReturn(accountSummaries);
        
        participantService.createParticipant(APP, PARTICIPANT, false);
    }
    
    @Test
    public void updateIdentifiersEmailSignInUpdatePhone() {
        // Verifies email-based sign in, phone update, account update, and an updated 
        // participant is returned... the common happy path.
        mockHealthCodeAndAccountRetrieval();
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, PHONE, null);
        
        StudyParticipant returned = participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getPhone(), TestConstants.PHONE);
        assertEquals(account.getPhoneVerified(), Boolean.FALSE);
        verify(accountService).authenticate(APP, EMAIL_PASSWORD_SIGN_IN);
        verify(accountService).updateAccount(account);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(returned.getId(), PARTICIPANT.getId());
    }

    @Test
    public void updateIdentifiersPhoneSignInUpdateEmail() {
        // This flips the method of sign in to use a phone, and sends an email update. 
        // Also tests the common path of creating unverified email address with verification email sent
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        APP.setEmailVerificationEnabled(true);
        APP.setAutoVerificationEmailSuppressed(false);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
        
        StudyParticipant returned = participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getEmail(), "email@email.com");
        assertEquals(account.getEmailVerified(), Boolean.FALSE);
        verify(accountService).authenticate(APP, PHONE_PASSWORD_SIGN_IN);
        verify(accountService).updateAccount(account);
        verify(accountWorkflowService).sendEmailVerificationToken(APP, ID, "email@email.com");
        assertEquals(PARTICIPANT.getId(), returned.getId());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidates() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithBlankEmail() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, "", null, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithInvalidPhone() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("US", "1231231234"), null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }
    
    @Test
    public void updateIdentifiersUsingReauthentication() {
        mockHealthCodeAndAccountRetrieval();
        when(accountService.reauthenticate(APP, REAUTH_REQUEST)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        IdentifierUpdate update = new IdentifierUpdate(REAUTH_REQUEST, null, TestConstants.PHONE, null);
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        verify(accountService).reauthenticate(APP, REAUTH_REQUEST);
    }

    @Test
    public void updateIdentifiersCreatesVerifiedEmailWithoutVerification() {
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        APP.setEmailVerificationEnabled(false);
        APP.setAutoVerificationEmailSuppressed(false); // can be true or false, doesn't matter
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);

        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getEmail(), "email@email.com");
        assertEquals(account.getEmailVerified(), Boolean.TRUE);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersCreatesUnverifiedEmailWithoutVerification() {
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        APP.setEmailVerificationEnabled(true);
        APP.setAutoVerificationEmailSuppressed(true);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, EMAIL, null, null);
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getEmailVerified(), Boolean.FALSE);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersAddsSynapseUserId() {
        mockAccountNoEmail();
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, EMAIL, null, SYNAPSE_USER_ID);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
    }

    @Test
    public void updateIdentifiersAuthenticatingToAnotherAccountInvalid() {
        // This ID does not match the ID in the request's context, and that will fail
        account.setId("another-user-id");
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
        
        try {
            participantService.updateIdentifiers(APP, CONTEXT, update);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            verify(accountService, never()).updateAccount(any());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        }
    }

    @Test
    public void updateIdentifiersDoNotOverwriteExistingIdentifiers() {
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE, EXTERNAL_ID);
        account.setEmailVerified(TRUE);
        account.setPhoneVerified(TRUE);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        // Now that an external ID addition will simply add another external ID, the 
        // test has been changed to submit an existing external ID.
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "updated@email.com",
                new Phone("4082588569", "US"), "88888");
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        // None of these have changed.
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getEmailVerified(), TRUE);
        assertEquals(account.getPhone(), PHONE);
        assertEquals(account.getPhoneVerified(), TRUE);
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
        verify(accountService, never()).updateAccount(any());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersDoesNotReassignExternalIdOnOtherUpdate() throws Exception {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        // Add phone
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("4082588569", "US"),
                null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        // externalIdService not called
        verify(accountService).updateAccount(any());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void addingExternalIdsOnUpdateDoesNothing() {
        // Let's make sure this account has an enrollment...it should not be changed.
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant()
                .withExternalIds(ENROLLMENT_MAP).build();
        
        participantService.updateParticipant(APP, participant);
        verify(enrollmentService, never()).addEnrollment(any(), any());
        
        assertEquals(Iterables.getFirst(account.getEnrollments(), null).getExternalId(), EXTERNAL_ID);
    }

    @Test
    public void updateParticipantWithNoExternalIdDoesNotChangeExistingId() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);

        // Participant has no external ID, so externalIdService is not called
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        participantService.updateParticipant(APP, participant);
        verify(enrollmentService, never()).addEnrollment(any(), any());
        assertEquals(Iterables.getFirst(account.getEnrollments(),  null).getExternalId(), EXTERNAL_ID);
    }

    @Test
    public void sameManagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant().withExternalIds(ENROLLMENT_MAP).build();
        participantService.updateParticipant(APP, participant);
        
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }
    
    // Removed because you can no longer simply remove an external ID
    // public void removingManagedExternalIdWorks();
    
    @Test
    public void sameUnmanagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(studyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, false)).thenReturn(Study.create());
        
        StudyParticipant participant = withParticipant().withExternalIds(ImmutableMap.of(TEST_STUDY_ID, EXTERNAL_ID))
                .build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        assertFalse(accountCaptor.getValue().getEnrollments().isEmpty());
    }
    
    @Test
    public void removingExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant().build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        assertFalse(accountCaptor.getValue().getEnrollments().isEmpty());
    }
    
    @Test
    public void removingExternalIdOnlyWorksForResearcher() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        StudyParticipant participant = withParticipant().build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        assertFalse(accountCaptor.getValue().getEnrollments().isEmpty());
    }
    
    private StudyParticipant.Builder withParticipant() {
        return new StudyParticipant.Builder().copyOf(PARTICIPANT);
    }
    
    @Test
    public void createParticipantNoExternalIdAddedDoesNothing() {
        RequestContext.set(new RequestContext.Builder().build());
        when(participantService.getAccount()).thenReturn(account);
        StudyParticipant participant = withParticipant().withExternalId(null).withExternalIds(null).build();
        
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(APP, account);
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void normalUserCannotCreateParticipantWithExternalId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId").build());
        when(participantService.getAccount()).thenReturn(account);
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());

        StudyParticipant participant = withParticipant()
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(APP, account);
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }
    @Test
    public void updateParticipantNoExternalIdsNoneAddedDoesNothing() {
        RequestContext.set(new RequestContext.Builder().build());
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(APP, participant);
        
        verify(enrollmentService, never()).addEnrollment(any(), any());
        verify(accountService).updateAccount(account);
        assertTrue(account.getEnrollments().isEmpty());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateParticipantNoExternalIdsAreAdded() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId").build());
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        StudyParticipant participant = withParticipant()
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(account);
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }

    @Test
    public void sendSmsMessage() {
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        account.setHealthCode(HEALTH_CODE);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        
        SmsTemplate template = new SmsTemplate("This is a test ${appShortName}"); 
        
        participantService.sendSmsMessage(APP, ID, template);

        verify(smsService).sendSmsMessage(eq(ID), smsProviderCaptor.capture());
        
        SmsMessageProvider provider = smsProviderCaptor.getValue();
        assertEquals(provider.getPhone(), TestConstants.PHONE);
        assertEquals(provider.getSmsRequest().getMessage(), "This is a test Bridge");
        assertEquals(provider.getSmsType(), "Promotional");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void sendSmsMessageThrowsIfNoPhone() { 
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        SmsTemplate template = new SmsTemplate("This is a test ${appShortName}"); 
        
        participantService.sendSmsMessage(APP, ID, template);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void sendSmsMessageThrowsIfPhoneUnverified() { 
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(false);
        
        SmsTemplate template = new SmsTemplate("This is a test ${appShortName}"); 
        
        participantService.sendSmsMessage(APP, ID, template);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void sendSmsMessageThrowsIfBlankMessage() {
        SmsTemplate template = new SmsTemplate("    "); 
        
        participantService.sendSmsMessage(APP, ID, template);
    }
    
    @Test
    public void getActivityEvents() {
        mockHealthCodeAndAccountRetrieval();
        
        participantService.getActivityEvents(APP, null, ID);
        
        verify(activityEventService).getActivityEventList(APP.getIdentifier(), null, HEALTH_CODE);
    }
    
    @Test
    public void getAccountThrowingExceptionHandlesPrefixedIDs() {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        account.setAppId(TEST_APP_ID);
        AccountId accountId = AccountId.forHealthCode(TEST_APP_ID, HEALTH_CODE);
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());

        // This directly calls getAccountThrowingException(); it should recognize and
        // handle the healthCode version of an ID.
        participantService.getActivityEvents(APP, null, "healthcode:"+HEALTH_CODE);
        
        // still works
        verify(accountService).getAccount(accountId);
        verify(activityEventService).getActivityEventList(APP.getIdentifier(), null, HEALTH_CODE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void normalUserCannotAddExternalIdOnUpdate() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId").build());
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant()
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        verify(enrollmentService, never()).addEnrollment(any(), any());
    }
    
    @Test
    public void researcherCannotChangeManagedExternalIdOnUpdate() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant()
                .withExternalIds(ImmutableMap.of(STUDY_ID, "newExternalId")).build();
        ExternalIdentifier newExtId = ExternalIdentifier.create(TEST_APP_ID, "newExternalId");
        newExtId.setStudyId(STUDY_ID);

        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture());
        
        assertFalse(collectExternalIds(account).contains("newExternalId"));
    }

    @Test
    public void getActivityHistory() {
        mockHealthCodeAndAccountRetrieval();
        DateTime scheduledOnStart = DateTime.now().minusDays(3);
        DateTime scheduledOnEnd = scheduledOnStart.plusDays(6);
        
        participantService.getActivityHistory(APP, ID, SURVEY, "referentGuid", scheduledOnStart, scheduledOnEnd,
                "offsetKey", 112);
        
        verify(scheduledActivityService).getActivityHistory(HEALTH_CODE, ActivityType.SURVEY, "referentGuid",
                scheduledOnStart, scheduledOnEnd, "offsetKey", 112);
    }
    
    @Test
    public void adminCanAddExternalIdOnCreate() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture());
        
        verify(enrollmentService).addEnrollment(any(Account.class), enrollmentCaptor.capture());
        Enrollment enrollment = enrollmentCaptor.getValue();
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getStudyId(), STUDY_ID);
        assertEquals(enrollment.getExternalId(), EXTERNAL_ID);
        assertEquals(enrollment.getAccountId(), ID);
    }
    @Test
    public void researcherCanAddExternalIdOnCreate() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture());
        verify(enrollmentService).addEnrollment(any(), enrollmentCaptor.capture());
        
        Enrollment enrollment = enrollmentCaptor.getValue();
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getStudyId(), STUDY_ID);
        assertEquals(enrollment.getExternalId(), EXTERNAL_ID);
        assertEquals(enrollment.getAccountId(), ID);
    }
    @Test
    public void researcherCannotAddStudyIdOnUpdate() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withStudyIds(ImmutableSet.of(STUDY_ID)).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        
        Account captured = accountCaptor.getValue();
        assertTrue(captured.getEnrollments().isEmpty());
    }
    
    @Test
    public void studyResearcherCanAddExternalIdOnCreate() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerEnrolledStudies(ImmutableSet.of(STUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(studyService.getStudy(TEST_APP_ID, STUDY_ID, false)).thenReturn(Study.create());
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalIds(ENROLLMENT_MAP).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture());
        verify(enrollmentService).addEnrollment(any(), enrollmentCaptor.capture());
        
        Enrollment enrollment = enrollmentCaptor.getValue();
        assertEquals(enrollment.getAppId(), TEST_APP_ID);
        assertEquals(enrollment.getStudyId(), STUDY_ID);
        assertEquals(enrollment.getExternalId(), EXTERNAL_ID);
        assertEquals(enrollment.getAccountId(), ID);
    }
    
    @Test
    public void studyResearcherCannotAddHaveNoStudyOnUpdate() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of(STUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        account.getEnrollments().add(Enrollment.create(TEST_APP_ID, STUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        // participant does not have the study. This should throw an error
        participantService.updateParticipant(APP, PARTICIPANT);

        verify(accountService).updateAccount(accountCaptor.capture());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getEnrollments().size(), 1);
        assertEquals(captured.getEnrollments(), account.getEnrollments());
    }
    
    @Test
    public void createGlobalCustomActivityEvent() throws Exception {
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withEventKey("anEvent")
                .withTimestamp(TIMESTAMP).build();
        
        AccountId accountId = AccountId.forId(APP.getIdentifier(), TEST_USER_ID);
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        participantService.createCustomActivityEvent(APP, TEST_USER_ID, request);
        
        verify(activityEventService).publishCustomEvent(APP, HEALTH_CODE, "anEvent", TIMESTAMP);
    }
    
    @Test
    public void createStudyScopedCustomActivityEvent() throws Exception {
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withEventKey("anEvent")
                .withTimestamp(TIMESTAMP).build();
        
        AccountId accountId = AccountId.forId(APP.getIdentifier(), TEST_USER_ID);
        when(accountService.getAccount(accountId)).thenReturn(Optional.of(account));
        
        participantService.createCustomActivityEvent(APP, TEST_USER_ID, request);
        
        verify(activityEventService).publishCustomEvent(APP, HEALTH_CODE, "anEvent", TIMESTAMP);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void createCustomActivityEventAccountNotFound() throws Exception {
        CustomActivityEventRequest request = new CustomActivityEventRequest.Builder()
                .withEventKey("anEvent")
                .withTimestamp(TIMESTAMP).build();
        
        participantService.createCustomActivityEvent(APP, TEST_USER_ID, request);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void requestParticipantRosterNullPassword() throws JsonProcessingException {
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword(null).build();

        participantService.requestParticipantRoster(APP, TEST_USER_ID, request);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void requestParticipantRosterBlankPassword() throws JsonProcessingException {
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword("").build();

        participantService.requestParticipantRoster(APP, TEST_USER_ID, request);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void requestParticipantRosterInvalidPassword() throws JsonProcessingException {
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword("badPassword").build();

        participantService.requestParticipantRoster(APP, TEST_USER_ID, request);
    }

    @Test
    public void requestParticipantRoster() throws JsonProcessingException {
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword(PASSWORD).withStudyId(STUDY_ID).build();

        String queueUrl = "https://sqs.us-east-1.amazonaws.com/420786776710/Bridge-WorkerPlatform-Request-local";
        when(bridgeConfig.getProperty("workerPlatform.request.sqs.queue.url")).thenReturn(queueUrl);

        when(sqsClient.sendMessage(eq(queueUrl), anyString())).thenReturn(mock(SendMessageResult.class));

        participantService.requestParticipantRoster(APP, TEST_USER_ID, request);

        String requestJson = "{\"service\":\"DownloadParticipantRosterWorker\",\"body\":{\"appId\":\"test-app\"," +
                "\"userId\":\"userId\",\"password\":\"P@ssword1\",\"studyId\":\"studyId\"}}";
        verify(sqsClient).sendMessage(queueUrl, requestJson);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void requestParticipantRoster_emailNotVerified() throws JsonProcessingException {
        account.setEmail(EMAIL);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword(PASSWORD).withStudyId(STUDY_ID).build();

        participantService.requestParticipantRoster(APP, TEST_USER_ID, request);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void requestParticipantRoster_noEmail() throws JsonProcessingException {
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        ParticipantRosterRequest request = new ParticipantRosterRequest.Builder().withPassword(PASSWORD).withStudyId(STUDY_ID).build();

        participantService.requestParticipantRoster(APP, TEST_USER_ID, request);
    }
    
    @Test
    public void updateParticipantNoteSuccessfulAsAdmin() {
        // RESEARCHER role set in Before method
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        account.setNote("original note");

        StudyParticipant participant = withParticipant()
                .withNote("updated note")
                .build();
        participantService.updateParticipant(APP, participant);

        assertEquals(account.getNote(), "updated note");
    }

    @Test
    public void updateParticipantNoteUnsuccessfulAsNonAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .build());

        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        account.setNote("original note");

        StudyParticipant participant = withParticipant()
                .withNote("updated note")
                .build();
        participantService.updateParticipant(APP, participant);

        assertEquals(account.getNote(), "original note");
    }

    @Test
    public void getParticipantHasNoteAsAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(account.getId())
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());

        account.setNote(TEST_NOTE);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));

        StudyParticipant adminRetrieved = participantService.getSelfParticipant(APP, CONTEXT, false);
        assertEquals(adminRetrieved.getNote(), TEST_NOTE);
    }

    @Test
    public void getParticipantDoesNotHaveNoteAsNonAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(account.getId())
                .withCallerRoles(ImmutableSet.of())
                .build());

        account.setNote(TEST_NOTE);
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));

        StudyParticipant nonAdminRetrieved = participantService.getSelfParticipant(APP, CONTEXT, false);
        assertNull(nonAdminRetrieved.getNote());
    }
    
    @Test
    public void installLinkCorrectlySelected() {
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("iPhone OS", "iphone-os-link");
        
        // Lacking android or universal, find the only link that's there.
        assertEquals(participantService.getInstallLink("Android", installLinks), "iphone-os-link");
        
        installLinks.put("Universal", "universal-link");
        assertEquals(participantService.getInstallLink("iPhone OS", installLinks), "iphone-os-link");
        assertEquals(participantService.getInstallLink("Android", installLinks), "universal-link");
        
        Map<String,String> emptyInstallLinks = Maps.newHashMap();
        assertNull(participantService.getInstallLink("iPhone OS", emptyInstallLinks));
    }
    
    @Test
    public void sendInstallLinkMessage_sendsPhone() {
        when(participantService.getInstallDateTime()).thenReturn(CREATED_ON);
        
        TemplateRevision revision = TemplateRevision.create();
        when(templateService.getRevisionForUser(APP, SMS_APP_INSTALL_LINK)).thenReturn(revision);
        
        participantService.sendInstallLinkMessage(APP, PROMOTIONAL, HEALTH_CODE, EMAIL, PHONE, "Android");
        
        verify(smsService).sendSmsMessage(eq(null), smsProviderCaptor.capture());
        SmsMessageProvider provider = smsProviderCaptor.getValue();
        assertEquals(provider.getApp(), APP);
        assertEquals(provider.getTemplateRevision(), revision);
        assertEquals(provider.getSmsTypeEnum(), PROMOTIONAL);
        assertEquals(provider.getPhone(), PHONE);
        assertEquals(provider.getTokenMap().get(APP_INSTALL_URL_KEY), "some.link");
        
        verify(activityEventService).publishInstallLinkSent(APP, HEALTH_CODE, CREATED_ON);
    }
    
    @Test
    public void sendInstallLinkMessage_sendsEmail() {
        TemplateRevision revision = TemplateRevision.create();
        when(templateService.getRevisionForUser(APP, EMAIL_APP_INSTALL_LINK)).thenReturn(revision);
        
        participantService.sendInstallLinkMessage(APP, TRANSACTIONAL, HEALTH_CODE, EMAIL, null, "Android");
        
        verify(sendMailService).sendEmail(emailProviderCaptor.capture());
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(provider.getApp(), APP);
        assertEquals(provider.getTemplateRevision(), revision);
        assertEquals(provider.getRecipientEmails(), ImmutableSet.of(EMAIL));
        assertEquals(provider.getType(), EmailType.APP_INSTALL);
        assertEquals(provider.getTokenMap().get(APP_INSTALL_URL_KEY), "some.link");
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ACCOUNT_UNABLE_TO_BE_CONTACTED_ERROR)
    public void sendInstallLinkMessage_noPhoneOrEmail() {
        participantService.sendInstallLinkMessage(APP, TRANSACTIONAL, HEALTH_CODE, null, null, null);
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = NO_INSTALL_LINKS_ERROR)
    public void sendInstallLinkMessage_noInstallLinks() {
        App app = App.create();
        app.setInstallLinks(ImmutableMap.of());
        
        participantService.sendInstallLinkMessage(app, TRANSACTIONAL, HEALTH_CODE, EMAIL, null, "Android");
    }

    @Test
    public void sendInstallLinkMessage_skipsEventWithNoHealthCode() {
        when(participantService.getInstallDateTime()).thenReturn(CREATED_ON);
        
        TemplateRevision revision = TemplateRevision.create();
        when(templateService.getRevisionForUser(APP, SMS_APP_INSTALL_LINK)).thenReturn(revision);
        
        participantService.sendInstallLinkMessage(APP, TRANSACTIONAL, null, EMAIL, PHONE, "Android");
        
        verify(activityEventService, never()).publishInstallLinkSent(any(), any(), any());
    }

    @Test
    public void updateClientTimeZone() {
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        StudyParticipant participant = withParticipant()
                .withClientTimeZone("America/Los_Angeles")
                .build();

        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture());

        Account capturedAccount = accountCaptor.getValue();
        assertEquals(capturedAccount.getClientTimeZone(), "America/Los_Angeles");
    }

    // getPagedAccountSummaries() filters studies in the query itself, as this is the only 
    // way to get correct paging.
    
    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying,
    // that it has been set. If the setter is not called, the existing status will be sent back to account store.
    private void verifyStatusUpdate(Set<Roles> callerRoles, boolean canSetStatus) {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(callerRoles).build());
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withStatus(DISABLED).build();
        
        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();

        if (canSetStatus) {
            assertEquals(account.getStatus(), DISABLED);
        } else {
            assertNotEquals(account.getStatus(), DISABLED);
        }
    }

    private void verifyRoleCreate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet) {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(callerRoles).build());
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant()
                .withRoles(ImmutableSet.of(SUPERADMIN, ADMIN, RESEARCHER, DEVELOPER, WORKER)).build();
        
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        if (rolesThatAreSet != null) {
            assertEquals(account.getRoles(), rolesThatAreSet);
        } else {
            assertEquals(ImmutableSet.of(), account.getRoles());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet, Set<Roles> expected) {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(callerRoles).build());

        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withRoles(rolesThatAreSet).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture());
        Account account = accountCaptor.getValue();
        
        if (expected != null) {
            assertEquals(account.getRoles(), expected);
        } else {
            assertEquals(ImmutableSet.of(), account.getRoles());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> expected) {
        verifyRoleUpdate(callerRoles, ImmutableSet.of(SUPERADMIN, ADMIN, RESEARCHER, DEVELOPER, WORKER), expected);
    }

    // Makes an app instance, so tests can modify it without affecting other tests.
    private static App makeStudy() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setHealthCodeExportEnabled(true);
        app.setUserProfileAttributes(APP_PROFILE_ATTRS);
        app.setDataGroups(APP_DATA_GROUPS);
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        app.getUserProfileAttributes().add("can_be_recontacted");
        return app;
    }
    
    private StudyParticipant.Builder mockStudiesInRequest(Set<String> callerStudies, Set<String> participantStudies, Roles... callerRoles) {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(callerStudies)
                .withCallerRoles( (callerRoles.length == 0) ? null : ImmutableSet.copyOf(callerRoles)).build());
        
        StudyParticipant.Builder builder = withParticipant().withStudyIds(participantStudies);
        
        for (String studyId : callerStudies) {
            when(studyService.getStudy(APP.getIdentifier(), studyId, false)).thenReturn(Study.create());
        }
        for (String studyId : participantStudies) {
            when(studyService.getStudy(APP.getIdentifier(), studyId, false)).thenReturn(Study.create());
        }
        return builder;
    }
}