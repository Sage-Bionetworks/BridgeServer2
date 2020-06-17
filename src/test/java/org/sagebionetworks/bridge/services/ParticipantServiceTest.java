package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.TRUE;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeUtils.collectExternalIds;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.sagebionetworks.bridge.models.schedules.ActivityType.SURVEY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
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
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.apps.SmsTemplate;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
    private static final String SUBSTUDY_ID = "substudyId";
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
            .withClientData(TestUtils.getClientData()).build();
    
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
    private SubstudyService substudyService;
    
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
    private ExternalIdService externalIdService;
    
    @Mock
    private AccountWorkflowService accountWorkflowService;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;

    @Captor
    ArgumentCaptor<App> appCaptor;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;

    @Captor
    ArgumentCaptor<SmsMessageProvider> providerCaptor;
    
    private Account account;
    
    private ExternalIdentifier extId;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        extId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        extId.setSubstudyId(SUBSTUDY_ID);
        APP.setExternalIdRequiredOnSignup(false);
        APP.setEmailVerificationEnabled(false);
        APP.setAccountLimit(0);

        account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        // In order to verify that the lambda has been executed
        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(2);
            if (accountConsumer != null) {
                accountConsumer.accept(account);    
            }
            return null;
        }).when(accountService).createAccount(any(), any(), any());
        doAnswer((InvocationOnMock invocation) -> {
            @SuppressWarnings("unchecked")
            Consumer<Account> accountConsumer = (Consumer<Account>) invocation.getArgument(1);
            if (accountConsumer != null) {
                accountConsumer.accept(account);    
            }
            return null;
        }).when(accountService).updateAccount(any(), any());
        
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES)
                .withCallerSubstudies(CALLER_SUBS).build());
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    private void mockAccountRetrievalWithSubstudyD() {
        mockHealthCodeAndAccountRetrieval();
        AccountSubstudy as = AccountSubstudy.create(APP.getIdentifier(), "substudyD", ID);
        as.setExternalId(EXTERNAL_ID);
        account.setAccountSubstudies(Sets.newHashSet(as));
    }
    
    private void mockHealthCodeAndAccountRetrieval() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
    }
    
    private void mockHealthCodeAndAccountRetrieval(String email, Phone phone, String externalId) {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        account.setEmail(email);
        account.setPhone(phone);
        Set<AccountSubstudy> acctSubstudies = new HashSet<>();
        if (externalId != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID);
            acctSubstudies.add(acctSubstudy);
            acctSubstudy.setExternalId(externalId);
        }
        account.setAccountSubstudies(acctSubstudies);
        account.setAppId(TEST_APP_ID);
        when(participantService.getAccount()).thenReturn(account);
        when(participantService.generateGUID()).thenReturn(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
    }
    
    private void mockAccountNoEmail() {
        account.setId(ID);
        account.setHealthCode(HEALTH_CODE);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
    }
    
    @Test
    public void createParticipant() {
        APP.setEmailVerificationEnabled(true);
        when(participantService.generateGUID()).thenReturn(ID);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID)
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        IdentifierHolder idHolder = participantService.createParticipant(APP, participant, true);
        assertEquals(idHolder.getIdentifier(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        
        // suppress email (true) == sendEmail (false)
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        verify(accountWorkflowService).sendEmailVerificationToken(APP, ID, EMAIL);
        
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
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertEquals(account.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
        assertEquals(account.getNotifyByEmail(), Boolean.TRUE);
        assertNull(account.getTimeZone());
        assertEquals(account.getDataGroups(), ImmutableSet.of("group1","group2"));
        assertEquals(account.getLanguages(), ImmutableList.of("de","fr"));
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getExternalId(), EXTERNAL_ID);
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
        
        // don't update cache
        Mockito.verifyNoMoreInteractions(cacheProvider);
    }

    @Test
    public void createParticipantTransfersSubstudyIds() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies).build();
        mockHealthCodeAndAccountRetrieval();
        
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(accountSubstudies.size(), 2);
        
        AccountSubstudy substudyA = accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        assertEquals(substudyA.getAppId(), APP.getIdentifier());
        assertEquals(substudyA.getSubstudyId(), "substudyA");
        assertEquals(substudyA.getAccountId(), ID);
        
        AccountSubstudy substudyB = accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyB")).findAny().get();
        assertEquals(substudyB.getAppId(), APP.getIdentifier());
        assertEquals(substudyB.getSubstudyId(), "substudyB");
        assertEquals(substudyB.getAccountId(), ID);
    }

    @Test
    public void createParticipantWithExternalIdValidation() {
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID))
            .thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        // The order of these calls matters.
        InOrder inOrder = Mockito.inOrder(accountService, externalIdService);
        inOrder.verify(accountService).createAccount(eq(APP), eq(account), any());
        inOrder.verify(externalIdService).commitAssignExternalId(extId);
    }

    @Test
    public void createParticipantWithInvalidParticipant() {
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        
        // It doesn't get more invalid than this...
        StudyParticipant participant = new StudyParticipant.Builder().build();
        
        try {
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
        }
        verifyNoMoreInteractions(accountService);
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void createParticipantWithExternalIdAndSubstudyCallerValidates() { 
        // This is a substudy caller, so the substudy relationship needs to be enforced
        // when creating a participant. In this case, the relationship is implied by the 
        // external ID but not provided in the externalIds set. It works anyway.
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES)
                .withCallerSubstudies(ImmutableSet.of("substudy1")).build());

        ExternalIdentifier extId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        extId.setSubstudyId("substudy1");
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, "substudy1", false)).thenReturn(Substudy.create());
        
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        participantService.createParticipant(APP, participant, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = ".*substudy2.*is not a substudy of the caller")
    public void createParticipantWithExternalIdAndSubstudyCallerThatDontMatch() throws Exception { 
        // This is a substudy caller assigning an external ID, but the external ID is not in one of the 
        // caller's substudies.
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES)
                .withCallerSubstudies(ImmutableSet.of("substudy1")).build());

        ExternalIdentifier extId = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        extId.setSubstudyId("substudy2");
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, "substudy2", false)).thenReturn(Substudy.create());
        
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        participantService.createParticipant(APP, participant, false);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*externalId is not a valid external ID.*")
    public void createParticipantWithMissingExternalIdAndSubstudyCaller() { 
        // This is a substudy caller supplying an external ID that doesn't exist.
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES)
                .withCallerSubstudies(ImmutableSet.of("substudy1")).build());

        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.empty());
        
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        participantService.createParticipant(APP, participant, false);
    }
    
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
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant idParticipant = withParticipant().withPhone(null).withEmail(null).withPassword(null)
                .withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, idParticipant, false);
        
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
        assertFalse(account.getPhoneVerified());
        assertFalse(account.getEmailVerified());
    }
    
    @Test
    public void createParticipantExternalIdAndPasswordIsEnabled() {
        mockHealthCodeAndAccountRetrieval(null, null, null);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant participant = withParticipant().withEmail(null).withPhone(null).withExternalId(EXTERNAL_ID)
                .withPassword(PASSWORD).build();
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
    
    @Test
    public void createParticipantSynapseUserIdWithDeviationIsDisabled() {
        mockHealthCodeAndAccountRetrieval(null, null, null);

        StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId(SYNAPSE_USER_ID)
                .withPassword(PASSWORD).build();
        participantService.createParticipant(APP, participant, false);
        
        assertEquals(account.getStatus(), AccountStatus.UNVERIFIED);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp=".*must be assigned to one or more of these substudies: substudyId.*")
    public void createParticipantMustIncludeCallerSubstudy() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID)).build());
        
        participantService.createParticipant(APP, PARTICIPANT, false);
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
        AccountSubstudy as1 = AccountSubstudy.create(TEST_APP_ID, "substudyA", ID);
        AccountSubstudy as2 = AccountSubstudy.create(TEST_APP_ID, "substudyB", ID);
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));

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
        assertEquals(criteriaContext.getUserSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);

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
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantEmailDoesNotExist() {
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        participantService.getParticipant(APP, ID, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantAccountIdDoesNotExist() {
        participantService.getParticipant(APP, "externalId:some-junk", false);
    }
    
    @Test
    public void getSelfParticipantWithHistory() throws Exception {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(TestConstants.USER_SUBSTUDY_IDS).build());
        
        // Some data to verify
        account.setId(ID);
        account.setLastName("lastName");
        Set<AccountSubstudy> accountSubstudies = new HashSet<>();
        for (String substudyId : ImmutableList.of("substudyA", "substudyB", "substudyC")) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(APP.getIdentifier(), substudyId, ID);
            accountSubstudies.add(acctSubstudy);
        }
        account.setAccountSubstudies(accountSubstudies);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create("foo1");
        account.setConsentSignatureHistory(subpopGuid, ImmutableList.of(new ConsentSignature.Builder()
                .withConsentCreatedOn(START_DATE.getMillis()).build()));
        when(accountService.getAccount(any())).thenReturn(account);
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
        assertEquals(retrieved.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        // Consent was calculated
        assertTrue(retrieved.isConsented());
        // There is history
        UserConsentHistory history = retrieved.getConsentHistories().get(subpopGuid.getGuid()).get(0);
        assertEquals(history.getConsentCreatedOn(), START_DATE.getMillis());
    }
    
    @Test
    public void getSelfParticipantNoHistory() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(TestConstants.USER_SUBSTUDY_IDS).build());
        
        // Some data to verify
        account.setId(ID);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        Set<AccountSubstudy> accountSubstudies = new HashSet<>();
        for (String substudyId : ImmutableList.of("substudyA", "substudyB", "substudyC")) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(APP.getIdentifier(), substudyId, ID);
            accountSubstudies.add(acctSubstudy);
        }
        account.setAccountSubstudies(accountSubstudies);
        SubpopulationGuid subpopGuid = SubpopulationGuid.create("foo1");
        account.setConsentSignatureHistory(subpopGuid, ImmutableList.of(new ConsentSignature.Builder()
                .withConsentCreatedOn(START_DATE.getMillis()).build()));
        when(accountService.getAccount(any())).thenReturn(account);
        when(consentService.getConsentStatuses(CONTEXT, account)).thenReturn(TestConstants.CONSENTED_STATUS_MAP);
        
        StudyParticipant retrieved = participantService.getSelfParticipant(APP, CONTEXT, false);
        
        assertEquals(retrieved.getId(), CONTEXT.getUserId());
        assertEquals(retrieved.getSynapseUserId(), SYNAPSE_USER_ID);
        // These have been filtered
        assertEquals(retrieved.getSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        // Consent was calculated
        assertTrue(retrieved.isConsented());
        // There is no history
        assertTrue(retrieved.getConsentHistories().isEmpty());
    }
    
    @Test
    public void getStudyParticipant() {
        when(participantService.getAccount()).thenReturn(account);
        when(participantService.generateGUID()).thenReturn(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);

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
        AccountSubstudy acctSubstudy1 = AccountSubstudy.create(TEST_APP_ID, "substudyA", ID);
        acctSubstudy1.setExternalId("externalIdA");
        AccountSubstudy acctSubstudy2 = AccountSubstudy.create(TEST_APP_ID, "substudyB", ID);
        acctSubstudy2.setExternalId("externalIdB");
        AccountSubstudy acctSubstudy3 = AccountSubstudy.create(TEST_APP_ID, "substudyC", ID);
        // no third external ID, this one is just not in the external IDs map
        account.setAccountSubstudies(ImmutableSet.of(acctSubstudy1, acctSubstudy2, acctSubstudy3));
        
        
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
        assertEquals(participant.getExternalIds().get("substudyA"), "externalIdA");
        assertEquals(participant.getExternalIds().get("substudyB"), "externalIdB");
        
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
        assertEquals(criteriaContext.getUserSubstudyIds(), ImmutableSet.of("substudyA", "substudyB", "substudyC"));
    }
    
    @Test
    public void getStudyParticipantFilteringSubstudiesAndExternalIds() {
        // There is a partial overlap of substudy memberships between caller and user, the substudies that are 
        // not in the intersection, and the external IDs, should be removed from the participant
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE, null);
        AccountSubstudy acctSubstudy1 = AccountSubstudy.create(TEST_APP_ID, "substudyA", ID);
        acctSubstudy1.setExternalId("externalIdA");
        AccountSubstudy acctSubstudy2 = AccountSubstudy.create(TEST_APP_ID, "substudyB", ID);
        acctSubstudy2.setExternalId("externalIdB");
        AccountSubstudy acctSubstudy3 = AccountSubstudy.create(TEST_APP_ID, "substudyC", ID);
        // no third external ID, this one is just not in the external IDs map
        account.setAccountSubstudies(ImmutableSet.of(acctSubstudy1, acctSubstudy2, acctSubstudy3));
        
        // Now, the caller only sees A and C
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyC")).build());
        
        StudyParticipant participant = participantService.getParticipant(APP, ID, false);
        assertEquals(participant.getSubstudyIds(), ImmutableSet.of("substudyA", "substudyC"));
        assertEquals(participant.getExternalIds(), ImmutableMap.of("substudyA", "externalIdA"));
    }

    @Test
    public void getStudyStartTime_FromActivitiesRetrieved() {
        // Set up mocks.
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(activityEventService.getActivityEventMap(APP.getIdentifier(), HEALTH_CODE)).thenReturn(ImmutableMap.of(
                ActivityEventObjectType.ACTIVITIES_RETRIEVED.name().toLowerCase(), ACTIVITIES_RETRIEVED_DATETIME));

        // Execute and validate.
        DateTime result = participantService.getStudyStartTime(ACCOUNT_ID);
        assertEquals(result, ACTIVITIES_RETRIEVED_DATETIME);
    }

    @Test
    public void getStudyStartTime_FromEnrollment() {
        // Set up mocks.
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(activityEventService.getActivityEventMap(APP.getIdentifier(), HEALTH_CODE)).thenReturn(ImmutableMap.of(
                ActivityEventObjectType.ENROLLMENT.name().toLowerCase(), ENROLLMENT_DATETIME));

        // Execute and validate.
        DateTime result = participantService.getStudyStartTime(ACCOUNT_ID);
        assertEquals(result, ENROLLMENT_DATETIME);
    }

    @Test
    public void getStudyStartTime_FromAccountCreatedOn() {
        // Set up mocks.
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        account.setCreatedOn(CREATED_ON_DATETIME);
        when(activityEventService.getActivityEventMap(APP.getIdentifier(), HEALTH_CODE)).thenReturn(ImmutableMap.of());

        // Execute and validate.
        DateTime result = participantService.getStudyStartTime(ACCOUNT_ID);
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
        when(accountService.getAccount(accountId)).thenReturn(account);
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
        when(accountService.getAccount(accountId)).thenReturn(account);
        account.setId(ID);

        // Execute
        participantService.signUserOut(APP, ID, true);

        // Verify
        verify(accountService).getAccount(accountId);
        verify(accountService).deleteReauthToken(accountIdCaptor.capture());
        verify(cacheProvider).removeSessionByUserId(ID);

        assertEquals(accountIdCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(accountIdCaptor.getValue().getId(), ID);
    }

    @Test
    public void updateParticipantWithExternalIdValidationAddingId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(RESEARCH_CALLER_ROLES).build());
        
        mockHealthCodeAndAccountRetrieval(null, null, null);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));

        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        // The order here is significant.
        InOrder inOrder = Mockito.inOrder(accountService, externalIdService);
        inOrder.verify(accountService).updateAccount(accountCaptor.capture(), any());
        inOrder.verify(externalIdService).commitAssignExternalId(extId);
        
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
    public void updateParticipantWithSameExternalIdDoesntAssignExtId() {
        mockHealthCodeAndAccountRetrieval();

        // account and participant have the same ID, so externalIdService is not called
        participantService.updateParticipant(APP, PARTICIPANT);
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void updateParticipantTransfersSubstudyIdsForAdmins() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies, ADMIN).build();
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(accountSubstudies.size(), 2);
        
        // get() throws exception if accountSubstudy not found
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyB")).findAny().get();
    }
    
    @Test
    public void updateParticipantTransfersSubstudyIdsForSuperadmins() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies, SUPERADMIN).build();
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
        
        Set<AccountSubstudy> accountSubstudies = accountCaptor.getValue().getAccountSubstudies();
        assertEquals(accountSubstudies.size(), 2);
        
        // get() throws exception if accountSubstudy not found
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyA")).findAny().get();
        accountSubstudies.stream()
                .filter((as) -> as.getSubstudyId().equals("substudyB")).findAny().get();
    }    
    
    // The exception here results from the fact that the caller can't see the existance of the 
    // participant, because the substudy IDs don't overlap
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateParticipantMustIncludeCallerSubstudy() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID)).build());

        participantService.updateParticipant(APP, PARTICIPANT);
    }
    
    @Test
    public void addingSubstudyToAccountClearsCache() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        StudyParticipant participant = mockSubstudiesInRequest(ImmutableSet.of(), substudies, ADMIN).build();
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(APP, participant);
        
        assertEquals(account.getAccountSubstudies().size(), 2);
        verify(externalIdService, never()).unassignExternalId(any(), any());
        verify(cacheProvider).removeSessionByUserId(ID);
    }
    
    @Test
    public void removingSubstudyFromAccountClearsCache() { 
        Set<String> substudies = ImmutableSet.of("substudyA");
        StudyParticipant participant = mockSubstudiesInRequest(ImmutableSet.of(), substudies, ADMIN).build();
        
        mockHealthCodeAndAccountRetrieval();
        AccountSubstudy asA = AccountSubstudy.create(APP.getIdentifier(), "substudyA", ID);
        account.getAccountSubstudies().add(asA);
        
        AccountSubstudy asB = AccountSubstudy.create(APP.getIdentifier(), "substudyB", ID);
        asB.setExternalId("extB");
        account.getAccountSubstudies().add(asB);
        
        participantService.updateParticipant(APP, participant);
        
        // We've tested this collection more thoroughly in updateParticipantTransfersSubstudyIdsForAdmins()
        assertEquals(account.getAccountSubstudies().size(), 1);
        verify(externalIdService).unassignExternalId(account, "extB");
        verify(cacheProvider).removeSessionByUserId(ID);
    }
    
    @Test
    public void updateParticipantWithoutSubstudyChangesForAdmins() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyC");
        StudyParticipant participant = mockSubstudiesInRequest(substudies, substudies, ADMIN).build();
        
        mockHealthCodeAndAccountRetrieval();
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyC", ID));
        account.getAccountSubstudies().add(AccountSubstudy.create(APP.getIdentifier(), "substudyA", ID));
        
        participantService.updateParticipant(APP, participant);
        
        // We've tested this collection more thoroughly in updateParticipantTransfersSubstudyIdsForAdmins()
        verify(cacheProvider, never()).removeSessionByUserId(any());
    }
    
    @Test
    public void updateParticipantDoesNotUpdateImmutableFields() {
        mockHealthCodeAndAccountRetrieval(null, null, null);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
 
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
        assertNull(account.getStatus());
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
        verify(accountService, never()).updateAccount(any(), any());
        verifyNoMoreInteractions(externalIdService);
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
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        mockHealthCodeAndAccountRetrieval();
        account.setStatus(AccountStatus.ENABLED);

        StudyParticipant participant = withParticipant().withStatus(null).build();

        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
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
        when(accountService.getAccount(accountId)).thenReturn(account);
        
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
        when(accountService.getAccount(accountId)).thenReturn(account);
        
        StudyParticipant participant = participantService.getParticipant(APP, id, true);
        assertNotNull(participant);
        
        verify(accountService).getAccount(accountIdCaptor.capture());
        assertEquals(accountIdCaptor.getValue().getAppId(), APP.getIdentifier());
        assertEquals(accountIdCaptor.getValue().getExternalId(), ID);
    }
    
    @Test
    public void getParticipantWithStringId() {
        AccountId accountId = AccountId.forId(APP.getIdentifier(), ID);
        when(accountService.getAccount(accountId)).thenReturn(account);
        
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
    public void getStudyParticipantWithAccount() throws Exception {
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
    public void getStudyParticipantWithoutAccountThrows404() {
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

    // Creating an account and supplying an externalId
    @Test
    public void callsExternalIdService() {
        APP.setExternalIdRequiredOnSignup(true);
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        
        participantService.createParticipant(APP, participant, false);
        
        // Validated and required, use reservation service and don't set as option
        verify(externalIdService).commitAssignExternalId(extId);
    }

    @Test
    public void limitNotExceededException() {
        mockHealthCodeAndAccountRetrieval();
        APP.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(9);
        when(accountService.getPagedAccountSummaries(TEST_APP_ID, AccountSummarySearch.EMPTY_SEARCH))
                .thenReturn(accountSummaries);
        
        participantService.createParticipant(APP, PARTICIPANT, false);
    }
    
    @Test
    public void throwLimitExceededExactlyException() {
        APP.setAccountLimit(10);
        when(accountSummaries.getTotal()).thenReturn(10);
        when(accountService.getPagedAccountSummaries(TEST_APP_ID, AccountSummarySearch.EMPTY_SEARCH)).thenReturn(accountSummaries);
        
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
        when(accountService.getPagedAccountSummaries(TEST_APP_ID, AccountSummarySearch.EMPTY_SEARCH)).thenReturn(accountSummaries);
        
        participantService.createParticipant(APP, PARTICIPANT, false);
    }
    
    // This test does not make sense in that the caller is always operating on their own account.
    // However adding involves adding a new substudy.
    @Test
    public void updateIdentifiersCanAddExternalIdInOtherSubstudy() {
        // caller in question is in substudyA
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID)).build());
        
        // account is associated to the EXTERNAL_ID in SUBSTUDY_ID
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        // extId is not in the same substudy. This should work
        extId.setSubstudyId("anotherSubstudy");
        extId.setIdentifier("newExtId");
        when(externalIdService.getExternalId(TEST_APP_ID, "newExtId")).thenReturn(Optional.of(extId));
        
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, "newExtId", null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        verify(externalIdService).commitAssignExternalId(extId);
        
        assertEquals(accountCaptor.getValue().getAccountSubstudies().size(), 2);
        RequestContext context = BridgeUtils.getRequestContext();
        assertEquals(context.getCallerSubstudies(), ImmutableSet.of(SUBSTUDY_ID, "anotherSubstudy"));
    }
    
    @Test
    public void updateIdentifiersAssignsExternalIdEvenWhenAlreadyAssigned() {
        // Fully associated external ID can be changed by an update.
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyB")).build());
        mockHealthCodeAndAccountRetrieval();
        AccountSubstudy as = AccountSubstudy.create(TEST_APP_ID, "substudyB", ID);
        as.setExternalId(EXTERNAL_ID);
        account.setAccountSubstudies(Sets.newHashSet(as));
        account.setId(ID);
        
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        
        ExternalIdentifier newExtId = ExternalIdentifier.create(TEST_APP_ID, "newExternalId");
        newExtId.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(TEST_APP_ID, "newExternalId")).thenReturn(Optional.of(newExtId));
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, "newExternalId", null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        verify(accountService).updateAccount(eq(account), any());
        
        assertEquals(account.getAccountSubstudies().size(), 2);
        
        AccountSubstudy acctSubstudyA = findBySubstudyId(account, "substudyA");
        assertEquals(acctSubstudyA.getSubstudyId(), "substudyA");
        assertEquals(acctSubstudyA.getExternalId(), "newExternalId");
        
        AccountSubstudy acctSubstudyB = findBySubstudyId(account, "substudyB");
        assertEquals(acctSubstudyB.getSubstudyId(), "substudyB");
        assertEquals(acctSubstudyB.getExternalId(), EXTERNAL_ID);
    }
    
    @Test
    public void updateIdentifiersEmailSignInUpdatePhone() {
        // Verifies email-based sign in, phone update, account update, and an updated 
        // participant is returned... the common happy path.
        mockHealthCodeAndAccountRetrieval();
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, PHONE, null, null);
        
        StudyParticipant returned = participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getPhone(), TestConstants.PHONE);
        assertEquals(account.getPhoneVerified(), Boolean.FALSE);
        verify(accountService).authenticate(APP, EMAIL_PASSWORD_SIGN_IN);
        verify(accountService).updateAccount(eq(account), eq(null));
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        assertEquals(returned.getId(), PARTICIPANT.getId());
    }

    @Test
    public void updateIdentifiersPhoneSignInUpdateEmail() {
        // This flips the method of sign in to use a phone, and sends an email update. 
        // Also tests the common path of creating unverified email address with verification email sent
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        APP.setEmailVerificationEnabled(true);
        APP.setAutoVerificationEmailSuppressed(false);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null, null);
        
        StudyParticipant returned = participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getEmail(), "email@email.com");
        assertEquals(account.getEmailVerified(), Boolean.FALSE);
        verify(accountService).authenticate(APP, PHONE_PASSWORD_SIGN_IN);
        verify(accountService).updateAccount(eq(account), eq(null));
        verify(accountWorkflowService).sendEmailVerificationToken(APP, ID, "email@email.com");
        assertEquals(PARTICIPANT.getId(), returned.getId());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidates() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, null, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithBlankEmail() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, "", null, null, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithBlankExternalId() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, " ", null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateIdentifiersValidatesWithInvalidPhone() {
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("US", "1231231234"), null, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
    }
    
    @Test
    public void updateIdentifiersUsingReauthentication() {
        mockHealthCodeAndAccountRetrieval();
        when(accountService.reauthenticate(APP, REAUTH_REQUEST)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(REAUTH_REQUEST, null, TestConstants.PHONE, null, null);
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        verify(accountService).reauthenticate(APP, REAUTH_REQUEST);
    }

    @Test
    public void updateIdentifiersCreatesVerifiedEmailWithoutVerification() {
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        APP.setEmailVerificationEnabled(false);
        APP.setAutoVerificationEmailSuppressed(false); // can be true or false, doesn't matter
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null, null);

        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getEmail(), "email@email.com");
        assertEquals(account.getEmailVerified(), Boolean.TRUE);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersCreatesUnverifiedEmailWithoutVerification() {
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        APP.setEmailVerificationEnabled(true);
        APP.setAutoVerificationEmailSuppressed(true);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, EMAIL, null, null, null);
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getEmailVerified(), Boolean.FALSE);
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateIdentifiersAddsSynapseUserId() {
        mockAccountNoEmail();
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, EMAIL, null, null, SYNAPSE_USER_ID);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
    }

    @Test
    public void updateIdentifiersCreatesExternalIdWithAssignment() {
        ExternalIdentifier differentExternalId = ExternalIdentifier.create(TEST_APP_ID, "extid");
        differentExternalId.setSubstudyId(SUBSTUDY_ID);
        
        mockAccountNoEmail();
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(externalIdService.getExternalId(any(), eq("extid"))).thenReturn(Optional.of(differentExternalId));
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, null, null, "extid", null);
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        verify(externalIdService).commitAssignExternalId(differentExternalId);
    }

    @Test
    public void updateIdentifiersAuthenticatingToAnotherAccountInvalid() {
        // This ID does not match the ID in the request's context, and that will fail
        account.setId("another-user-id");
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null, null);
        
        try {
            participantService.updateIdentifiers(APP, CONTEXT, update);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            verify(accountService, never()).updateAccount(any(), any());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
            verify(externalIdService, never()).commitAssignExternalId(any());
        }
    }

    @Test
    public void updateIdentifiersDoNotOverwriteExistingIdentifiers() {
        mockHealthCodeAndAccountRetrieval(EMAIL, PHONE, EXTERNAL_ID);
        account.setEmailVerified(TRUE);
        account.setPhoneVerified(TRUE);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        when(accountService.authenticate(APP, PHONE_PASSWORD_SIGN_IN)).thenReturn(account);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        // Now that an external ID addition will simply add another external ID, the 
        // test has been changed to submit an existing external ID.
        IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "updated@email.com",
                new Phone("4082588569", "US"), EXTERNAL_ID, "88888");
        
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        // None of these have changed.
        assertEquals(account.getEmail(), EMAIL);
        assertEquals(account.getEmailVerified(), TRUE);
        assertEquals(account.getPhone(), PHONE);
        assertEquals(account.getPhoneVerified(), TRUE);
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
        verify(accountService, never()).updateAccount(any(), any());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void updateIdentifiersDoesNotReassignExternalIdOnOtherUpdate() throws Exception {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        // Add phone
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("4082588569", "US"),
                null, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        // externalIdService not called
        verify(accountService).updateAccount(any(), eq(null));
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void updateIdentifiersDoesNothingWhenExternalIdResubmitted() throws Exception {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        when(accountService.getAccount(any())).thenReturn(account);
        
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        // Submit the same external ID as an update
        IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, EXTERNAL_ID, null);
        participantService.updateIdentifiers(APP, CONTEXT, update);
        
        // Nothing is called. Nothing happens.
        verify(accountService, never()).updateAccount(any(), any());
        verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());        
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createRequiredExternalIdValidated() {
        mockHealthCodeAndAccountRetrieval();
        APP.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = withParticipant().withRoles(ImmutableSet.of()).build();
        
        participantService.createParticipant(APP, participant, false);
    }

    @Test
    public void createRequiredExternalIdWithRolesOK() {
        mockHealthCodeAndAccountRetrieval();
        APP.setExternalIdRequiredOnSignup(true);
        
        // developer
        StudyParticipant participant = withParticipant().build();
        
        participantService.createParticipant(APP, participant, false);
        // called with null, which does nothing.
        verify(externalIdService).commitAssignExternalId(null);
    }
    
    @Test
    public void createManagedExternalIdOK() {
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(externalIdService).commitAssignExternalId(extId);
    }

    @Test
    public void badManagedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.empty());
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(accountService, never()).createAccount(any(), any(), any());
            verify(externalIdService, never()).commitAssignExternalId(any());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        }
    }

    @Test
    public void usedExternalIdThrows() {
        mockHealthCodeAndAccountRetrieval();
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        extId.setHealthCode("AAA");
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
            verify(accountService, never()).createAccount(any(), any(), any());
            verify(externalIdService, never()).commitAssignExternalId(any());
            verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        }
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateMissingManagedExternalIdFails() {
        // In this case the ID is not in the external IDs table, so it fails validation.
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withExternalId("newExternalId").build();
        participantService.updateParticipant(APP, participant);
    }
    
    @Test
    public void addingManagedExternalIdOnUpdateOK() {
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        
        participantService.updateParticipant(APP, participant);
        
        ArgumentCaptor<ExternalIdentifier> extIdCaptor = ArgumentCaptor.forClass(ExternalIdentifier.class);
        
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getExternalId(), EXTERNAL_ID);
        verify(externalIdService).commitAssignExternalId(extIdCaptor.capture());
        assertEquals(extIdCaptor.getValue().getHealthCode(), HEALTH_CODE);
    }

    @Test
    public void updatingBlankExternalIdFails() {
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withExternalId("").build();
        try {
            participantService.updateParticipant(APP, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            
        }
        verify(externalIdService, never()).commitAssignExternalId(any());
    }

    // To change the ID associated to a substudy, you'd have to be an researcher/admin and do it in two 
    // steps, unassigning the first ID and then assigning the second ID. Non-admin users cannot do this, 
    // they can only add external IDs.
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void changingExternalIdDoesNotWork() throws Exception {
        ExternalIdentifier newExternalId = ExternalIdentifier.create(TEST_APP_ID, "newExternalId");
        newExternalId.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, "newExternalId")).thenReturn(Optional.of(newExternalId));
        
        ExternalIdentifier identifier = ExternalIdentifier.create(TEST_APP_ID, "oldExternalId");
        identifier.setSubstudyId(SUBSTUDY_ID);
        identifier.setHealthCode(HEALTH_CODE);
        when(externalIdService.getExternalId(TEST_APP_ID, "oldExternalId")).thenReturn(Optional.of(identifier));
        mockHealthCodeAndAccountRetrieval(EMAIL, null, "oldExternalId");
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        
        // This record has a different external ID than the mocked account
        StudyParticipant participant = withParticipant().withExternalId("newExternalId").build();
        participantService.updateParticipant(APP, participant);
    }

    @Test
    public void updateParticipantWithNoExternalIdDoesNotChangeExistingId() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);

        // Participant has no external ID, so externalIdService is not called
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        participantService.updateParticipant(APP, participant);
        verify(externalIdService, never()).commitAssignExternalId(any());
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(),  null).getExternalId(), EXTERNAL_ID);
    }

    @Test
    public void sameManagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    // Removed because you can no longer simply remove an external ID
    // public void removingManagedExternalIdWorks();
    
    @Test
    public void sameUnmanagedExternalIdOnUpdateIgnored() {
        mockHealthCodeAndAccountRetrieval(null, null, EXTERNAL_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void removingExternalIdIgnored() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant().build();
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    @Test
    public void removingExternalIdOnlyWorksForResearcher() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerRoles(ImmutableSet.of(Roles.DEVELOPER)).build());
        
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant().build();
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).unassignExternalId(any(), any());
        verify(externalIdService, never()).commitAssignExternalId(any());
    }
    
    private StudyParticipant.Builder withParticipant() {
        return new StudyParticipant.Builder().copyOf(PARTICIPANT);
    }
    
    @Test
    public void createParticipantValidatesUnmanagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant participant = withParticipant().withExternalId("  ").build();
        
        try {
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("externalId").get(0), "externalId cannot be blank");
        }
    }
    
    @Test
    public void createParticipantValidatesManagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
        try {
            StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("externalId").get(0), "externalId is not a valid external ID");
        }
    }
    @Test
    public void createParticipantNoExternalIdAddedDoesNothing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        when(participantService.getAccount()).thenReturn(account);
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), eq(account), any());
        verify(externalIdService).commitAssignExternalId(null);
    }
    @Test
    public void createParticipantExternalIdAddedUpdatesExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(participantService.getAccount()).thenReturn(account);
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());

        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), eq(account), any());
        verify(externalIdService).commitAssignExternalId(extId);
    }
    @Test
    public void updateParticipantValidatesManagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
            participantService.updateParticipant(APP, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("externalId").get(0), "externalId is not a valid external ID");
        }        
    }
    @Test
    public void updateParticipantValidatesUnmanagedExternalId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(" ").build();
            participantService.updateParticipant(APP, participant);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("externalId").get(0), "externalId cannot be blank");
        }        
    }
    @Test
    public void updateParticipantNoExternalIdsNoneAddedDoesNothing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountService).updateAccount(account, null);
        assertTrue(account.getAccountSubstudies().isEmpty());
    }

    @Test
    public void updateParticipantNoExternalIdsOneAddedUpdates() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(eq(account), any());
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getExternalId(), EXTERNAL_ID);
        verify(externalIdService).commitAssignExternalId(extId);
    }
    @Test
    public void updateParticipantExternalIdsExistNoneAddedDoesNothing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        mockAccountRetrievalWithSubstudyD();
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountService).updateAccount(account, null);
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getExternalId(), EXTERNAL_ID);
    }
    @Test
    public void updateParticipantAsResearcherNoExternalIdsNoneAddedDoesNothing() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        
        StudyParticipant participant = withParticipant().withExternalId(null).build();
        
        participantService.updateParticipant(APP, participant);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountService).updateAccount(account, null);
        assertTrue(account.getAccountSubstudies().isEmpty());
    }

    @Test
    public void updateParticipantAsResearcherNoExternalIdsOneAddedUpdates() {
        mockHealthCodeAndAccountRetrieval();
        
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(eq(account), any());
        assertEquals(Iterables.getFirst(account.getAccountSubstudies(), null).getExternalId(), EXTERNAL_ID);
        verify(externalIdService).commitAssignExternalId(extId);
    }

    @Test
    public void updateParticipantAsResearcherExternalIdsExistNoneMatchOneAddedUpdates() {
        mockAccountRetrievalWithSubstudyD();
        
        ExternalIdentifier nextExtId = ExternalIdentifier.create(TEST_APP_ID, "newExternalId");
        nextExtId.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, "newExternalId"))
            .thenReturn(Optional.of(nextExtId));

        StudyParticipant participant = withParticipant().withExternalId("newExternalId").build();
        
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(eq(account), any());
        assertTrue(collectExternalIds(account).contains(EXTERNAL_ID));
        assertTrue(collectExternalIds(account).contains("newExternalId"));
        verify(externalIdService).commitAssignExternalId(nextExtId);
    }
    @Test
    public void updateParticipantAsResearcherExternalIdsExistAndMatchOneAddedDoesNothing() {
        mockAccountRetrievalWithSubstudyD();

        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));

        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(externalIdService, never()).commitAssignExternalId(any());
        verify(accountService).updateAccount(account, null);
        assertEquals(account.getAccountSubstudies().size(), 1);
        assertTrue(collectExternalIds(account).contains(EXTERNAL_ID));
    }
    
    @Test
    public void sendSmsMessage() {
        when(accountService.getAccount(any())).thenReturn(account);
        account.setHealthCode(HEALTH_CODE);
        account.setPhone(TestConstants.PHONE);
        account.setPhoneVerified(true);
        
        SmsTemplate template = new SmsTemplate("This is a test ${appShortName}"); 
        
        participantService.sendSmsMessage(APP, ID, template);

        verify(smsService).sendSmsMessage(eq(ID), providerCaptor.capture());
        
        SmsMessageProvider provider = providerCaptor.getValue();
        assertEquals(provider.getPhone(), TestConstants.PHONE);
        assertEquals(provider.getSmsRequest().getMessage(), "This is a test Bridge");
        assertEquals(provider.getSmsType(), "Promotional");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void sendSmsMessageThrowsIfNoPhone() { 
        when(accountService.getAccount(any())).thenReturn(account);
        
        SmsTemplate template = new SmsTemplate("This is a test ${appShortName}"); 
        
        participantService.sendSmsMessage(APP, ID, template);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void sendSmsMessageThrowsIfPhoneUnverified() { 
        when(accountService.getAccount(any())).thenReturn(account);
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
        
        participantService.getActivityEvents(APP, ID);
        
        verify(activityEventService).getActivityEventList(APP.getIdentifier(), HEALTH_CODE);
    }
    
    @Test
    public void normalUserCanAddExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        mockHealthCodeAndAccountRetrieval();
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        assertEquals(Iterables.getFirst(accountCaptor.getValue().getAccountSubstudies(), null).getExternalId(),
                EXTERNAL_ID);
    }
    
    // The previous version of this test verified that a normal user could not change their external ID, 
    // but we want a stronger contract, so now we're throwing an exception if the user attempts to assign
    // an external ID that would in effect be an attempt to replace an existing ID.
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void normalUserCannotChangeExternalIdOnUpdate() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        
        extId.setSubstudyId(SUBSTUDY_ID); // same substudy, which is not allowable
        when(externalIdService.getExternalId(TEST_APP_ID, "differentId")).thenReturn(Optional.of(extId));
        
        StudyParticipant participant = withParticipant().withExternalId("differentId").build();
        participantService.updateParticipant(APP, participant);
    }
    
    @Test
    public void researcherCanChangeManagedExternalIdOnUpdate() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, EXTERNAL_ID);
        
        StudyParticipant participant = withParticipant().withExternalId("newExternalId").build();
        ExternalIdentifier newExtId = ExternalIdentifier.create(TEST_APP_ID, "newExternalId");
        newExtId.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, "newExternalId")).thenReturn(Optional.of(newExtId));

        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        assertTrue(collectExternalIds(account).contains("newExternalId"));
        verify(externalIdService).commitAssignExternalId(newExtId);
    }

    @Test 
    public void beginAssignExternalId() {
        Account account = Account.create();
        account.setId(ID);
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(existing));
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, EXTERNAL_ID);
        
        assertEquals(externalId.getIdentifier(), EXTERNAL_ID);
        assertEquals(externalId.getHealthCode(), HEALTH_CODE);
        assertEquals(externalId.getAppId(), TEST_APP_ID);
        assertEquals(externalId.getSubstudyId(), SUBSTUDY_ID);
    }    
    
    @Test
    public void beginAssignExternalIdIdentifierMissing() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, null);
        assertNull(externalId);
    }
    
    @Test
    public void beginAssignExternalIdIdentifierObjectMissing() {
        when(externalIdService.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.empty());
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, ID);
        assertNull(externalId);
    }
    
    @Test 
    public void beginAssignExternalIdHealthCodeExistsEqual() {
        account.setId(ID);
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        existing.setHealthCode(HEALTH_CODE); // despite assignment, we update everything
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(existing));
        
        // This is okay and it proceeds because the health codes match. It's a reassignment so we 
        // don't throw an error.
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, EXTERNAL_ID);
        assertSame(externalId, existing);
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void beginAssignExternalIdHealthCodeExistsNotEqual() {
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TEST_APP_ID, ID);
        existing.setHealthCode("anotherHealthCode");
        when(externalIdService.getExternalId(TEST_APP_ID, ID)).thenReturn(Optional.of(existing));
        
        participantService.beginAssignExternalId(account, ID);
    }
    
    @Test 
    public void beginAssignExternalIdAccountHasSingleSubstudyId() {
        // Note that this association does not have an external ID
        AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID);
        
        account.setId(ID);
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        account.getAccountSubstudies().add(acctSubstudy);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TEST_APP_ID, EXTERNAL_ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(existing));
        
        ExternalIdentifier externalId = participantService.beginAssignExternalId(account, EXTERNAL_ID);
        assertEquals(externalId.getHealthCode(), HEALTH_CODE);
        
        // Not changed. (This is not surprising now that beginAssignExternalId just does precondition
        // checks and does not alter the account object.)
        assertEquals(account.getAccountSubstudies().size(), 1);
        // Not changed
        assertNull(Iterables.getFirst(account.getAccountSubstudies(), null).getExternalId());
    }
    
    @Test
    public void rollbackCreateParticipantWhenAccountCreationFails() {
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(participantService.getAccount()).thenReturn(account);
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        doThrow(new ConcurrentModificationException("")).when(accountService).createAccount(eq(APP), eq(account), any());
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
            participantService.createParticipant(APP, participant, false);
            fail("Should have thrown an exception");
        } catch(ConcurrentModificationException e) {
            verify(externalIdService).unassignExternalId(account, EXTERNAL_ID); 
        }
    }
    
    @Test
    public void rollbackUpdateParticipantWhenAccountUpdateFails() {
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        doThrow(new ConcurrentModificationException("")).when(accountService).updateAccount(eq(account), any());
        
        try {
            StudyParticipant participant = withParticipant().withExternalId(EXTERNAL_ID).build();
            participantService.updateParticipant(APP, participant);
            fail("Should have thrown an exception");
        } catch(ConcurrentModificationException e) {
            verify(externalIdService).unassignExternalId(account, EXTERNAL_ID); 
        }
    }
    
    @Test
    public void rollbackUpdateIdentifiersWhenAccountUpdateFails() {
        mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
        account.setAccountSubstudies(new HashSet<>());
        when(accountService.authenticate(APP, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        extId.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        // Now... accountService throws an exception
        doThrow(new ConcurrentModificationException("")).when(accountService).updateAccount(eq(account), any());
        try {
            IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, EXTERNAL_ID, null);
            participantService.updateIdentifiers(APP, CONTEXT, update);
            fail("Should have thrown an exception");
        } catch(ConcurrentModificationException e) {
            verify(externalIdService).unassignExternalId(account, EXTERNAL_ID); 
        }
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
    
    /* ==================== */
    /* TESTS */
    /* ==================== */
    
    @Test
    public void adminCanAddSubstudyIdOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(participantService.generateGUID()).thenReturn(ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of(SUBSTUDY_ID)).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertNull(acctSubstudy.getExternalId());
        assertEquals(acctSubstudy.getAccountId(), ID);
    }
    @Test
    public void adminCanAddSubstudyIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of(SUBSTUDY_ID)).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertNull(acctSubstudy.getExternalId());
        assertEquals(acctSubstudy.getAccountId(), ID);
    }
    @Test
    public void adminCanAddExternalIdOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
        assertEquals(acctSubstudy.getAccountId(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        assertEquals(extId.getHealthCode(), HEALTH_CODE);
    }
    @Test
    public void adminCanAddExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
        assertEquals(acctSubstudy.getAccountId(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        assertEquals(extId.getHealthCode(), HEALTH_CODE);       
    }
    @Test
    public void adminCanRemoveSubstudyIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        account.setId(ID);
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        // participant does not have the substudy. It will be removed
        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertTrue(captured.getAccountSubstudies().isEmpty());
    }
    
    @Test
    public void researcherCanAddSubstudyIdOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(participantService.generateGUID()).thenReturn(ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of(SUBSTUDY_ID)).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertNull(acctSubstudy.getExternalId());
        assertEquals(acctSubstudy.getAccountId(), ID);
    }
    @Test
    public void researcherCanAddExternalIdOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
        assertEquals(acctSubstudy.getAccountId(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        assertEquals(extId.getHealthCode(), HEALTH_CODE);
    }
    @Test
    public void researcherCannotAddSubstudyIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of(SUBSTUDY_ID)).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertTrue(captured.getAccountSubstudies().isEmpty());
    }
    @Test
    public void researcherCanAddExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
        assertEquals(acctSubstudy.getAccountId(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        assertEquals(extId.getHealthCode(), HEALTH_CODE);       
    }
    @Test
    public void researcherCannotRemoveExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        assertSame(captured.getAccountSubstudies(), account.getAccountSubstudies());
    }
    @Test
    public void researcherCannotRemoveSubstudyIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        account.setId(ID);
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        // participant does not have the substudy. It will be removed
        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertSame(captured.getAccountSubstudies(), account.getAccountSubstudies());
    }
    
    @Test
    public void substudyResearcherCanAddSubstudyIdOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(participantService.generateGUID()).thenReturn(ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of(SUBSTUDY_ID)).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertNull(acctSubstudy.getExternalId());
        assertEquals(acctSubstudy.getAccountId(), ID);        
    }
    @Test
    public void substudyResearcherCanAddExternalIdOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(EXTERNAL_ID).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
        assertEquals(acctSubstudy.getAccountId(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        assertEquals(extId.getHealthCode(), HEALTH_CODE);
    }
    @Test
    public void substudyResearcherCannotAddSubstudyIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID, "secondSubstudyId"))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        when(substudyService.getSubstudy(TEST_APP_ID, "secondSubstudyId", false)).thenReturn(Substudy.create());
        account.setId(ID);
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of("secondSubstudyId")).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        assertSame(captured.getAccountSubstudies(), account.getAccountSubstudies());
    }
    @Test
    public void substudyResearcherCanAddExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(externalIdService.getExternalId(TEST_APP_ID, EXTERNAL_ID)).thenReturn(Optional.of(extId));
        
        account.setAccountSubstudies(null);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        account.setId(ID);
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId(EXTERNAL_ID).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
    }
    
    @Test
    public void substudyResearcherCannotRemoveExternalIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertSame(captured.getAccountSubstudies(), account.getAccountSubstudies());        
    }
    @Test
    public void substudyResearcherCannotRemoveSubstudyIdOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        account.setId(ID);
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        // participant does not have the substudy. It will be removed
        participantService.updateParticipant(APP, PARTICIPANT);
        
        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertSame(captured.getAccountSubstudies(), account.getAccountSubstudies());        
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp="someOtherSubstudy is not a substudy of the caller")
    public void substudyResearcherCannotAddExternalIdOfOtherSubstudyOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        extId.setSubstudyId("someOtherSubstudy");
        when(externalIdService.getExternalId(TEST_APP_ID, "otherExternalId")).thenReturn(Optional.of(extId));
        when(participantService.generateGUID()).thenReturn(ID, HEALTH_CODE);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("otherExternalId").build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        
        AccountSubstudy acctSubstudy = Iterables.getFirst(captured.getAccountSubstudies(), null);
        assertEquals(acctSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(acctSubstudy.getSubstudyId(), SUBSTUDY_ID);
        assertEquals(acctSubstudy.getExternalId(), EXTERNAL_ID);
        assertEquals(acctSubstudy.getAccountId(), ID);
        
        verify(externalIdService).commitAssignExternalId(extId);
        assertEquals(extId.getHealthCode(), HEALTH_CODE);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp="substudyId is not a substudy of the caller")
    public void substudyResearcherCannotAddSubstudyIdOfOtherSubstudyOnCreate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("someOtherSubstudy"))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, SUBSTUDY_ID, false)).thenReturn(Substudy.create());
        
        // participant does not have the substudy. It will be removed
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of(SUBSTUDY_ID)).build();
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertTrue(captured.getAccountSubstudies().isEmpty());           
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp="someOtherSubstudy is not a substudy of the caller")
    public void substudyResearcherCannotAddExternalIdOfOtherSubstudyOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        extId.setSubstudyId("someOtherSubstudy");
        when(externalIdService.getExternalId(TEST_APP_ID, "otherExternalId")).thenReturn(Optional.of(extId));
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        account.setId(ID);
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withExternalId("otherExternalId").build();
        participantService.updateParticipant(APP, participant);
    }
    
    @Test
    public void substudyResearcherCannotAddSubstudyIdOfOtherSubstudyOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        when(substudyService.getSubstudy(TEST_APP_ID, "someOtherSubstudy", false)).thenReturn(Substudy.create());
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);

        // participant does not have the substudy. It will be removed
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withSubstudyIds(ImmutableSet.of("someOtherSubstudy")).build();
        
        // This doesn't throw an exception because you just can't change the substudy association
        // after the participant is created (unlike external ID where you can add them to an existing
        // account).
        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        assertEquals(captured.getAccountSubstudies(), account.getAccountSubstudies());
    }
    
    @Test
    public void substudyResearcherCannotAddHaveNoSubstudyOnUpdate() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        account.getAccountSubstudies().add(AccountSubstudy.create(TEST_APP_ID, SUBSTUDY_ID, ID));
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(account);

        // participant does not have the substudy. This should throw an error
        participantService.updateParticipant(APP, PARTICIPANT);

        verify(accountService).updateAccount(accountCaptor.capture(), any());
        
        Account captured = accountCaptor.getValue();
        assertEquals(captured.getAccountSubstudies().size(), 1);
        assertEquals(captured.getAccountSubstudies(), account.getAccountSubstudies());
    }
    
    // getPagedAccountSummaries() filters substudies in the query itself, as this is the only 
    // way to get correct paging.
    
    // There's no actual vs expected here because either we don't set it, or we set it and that's what we're verifying,
    // that it has been set. If the setter is not called, the existing status will be sent back to account store.
    private void verifyStatusUpdate(Set<Roles> callerRoles, boolean canSetStatus) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(callerRoles).build());
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withStatus(AccountStatus.ENABLED).build();
        
        participantService.updateParticipant(APP, participant);

        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
        Account account = accountCaptor.getValue();

        if (canSetStatus) {
            assertEquals(account.getStatus(), AccountStatus.ENABLED);
        } else {
            assertNull(account.getStatus());
        }
    }

    private void verifyRoleCreate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(callerRoles).build());
        
        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant()
                .withRoles(ImmutableSet.of(SUPERADMIN, ADMIN, RESEARCHER, DEVELOPER, WORKER)).build();
        
        participantService.createParticipant(APP, participant, false);
        
        verify(accountService).createAccount(eq(APP), accountCaptor.capture(), any());
        Account account = accountCaptor.getValue();
        
        if (rolesThatAreSet != null) {
            assertEquals(account.getRoles(), rolesThatAreSet);
        } else {
            assertEquals(ImmutableSet.of(), account.getRoles());
        }
    }
    
    private void verifyRoleUpdate(Set<Roles> callerRoles, Set<Roles> rolesThatAreSet, Set<Roles> expected) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(callerRoles).build());

        mockHealthCodeAndAccountRetrieval();
        
        StudyParticipant participant = withParticipant().withRoles(rolesThatAreSet).build();
        participantService.updateParticipant(APP, participant);
        
        verify(accountService).updateAccount(accountCaptor.capture(), eq(null));
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
    
    private StudyParticipant.Builder mockSubstudiesInRequest(Set<String> callerSubstudies, Set<String> participantSubstudies, Roles... callerRoles) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(callerSubstudies)
                .withCallerRoles( (callerRoles.length == 0) ? null : ImmutableSet.copyOf(callerRoles)).build());
        
        StudyParticipant.Builder builder = withParticipant().withSubstudyIds(participantSubstudies);
        
        for (String substudyId : callerSubstudies) {
            when(substudyService.getSubstudy(APP.getIdentifier(), substudyId, false)).thenReturn(Substudy.create());
        }
        for (String substudyId : participantSubstudies) {
            when(substudyService.getSubstudy(APP.getIdentifier(), substudyId, false)).thenReturn(Substudy.create());
        }
        return builder;
    }
    
    private AccountSubstudy findBySubstudyId(Account account, String substudyId) {
        for (AccountSubstudy acctSubstudy : account.getAccountSubstudies()) {
            if (acctSubstudy.getSubstudyId().equals(substudyId)) {
                return acctSubstudy;
            }
        }
        return null;
    }
}
