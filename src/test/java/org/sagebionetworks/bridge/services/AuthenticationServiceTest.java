package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID_WITH_HEALTHCODE;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.apps.PasswordPolicy.DEFAULT_PASSWORD_POLICY;
import static org.sagebionetworks.bridge.services.AuthenticationService.ROTATIONS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class AuthenticationServiceTest extends Mockito {
    private static final String IP_ADDRESS = "ip-address";
    private static final List<String> LANGUAGES = ImmutableList.of("es","de");
    private static final String SESSION_TOKEN = "SESSION_TOKEN";
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String TOKEN_UNFORMATTED = "ABCDEF";
    private static final String REAUTH_TOKEN = "GHI-JKL";
    
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
    
    private static final SignIn SIGN_IN_REQUEST_WITH_EMAIL = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withEmail(EMAIL).build();
    private static final SignIn PHONE_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withPhone(PHONE).withToken(TOKEN).build();
    private static final SignIn EMAIL_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withEmail(EMAIL).withToken(TOKEN).build();
    private static final SignIn EMAIL_PASSWORD_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withEmail(EMAIL).withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID)
            .withPhone(PHONE).withPassword(PASSWORD).build();
    private static final SignIn REAUTH_SIGN_IN = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withReauthToken(REAUTH_TOKEN).build();
    
    private static final CacheKey CACHE_KEY_EMAIL_SIGNIN = CacheKey.emailSignInRequest(EMAIL_SIGN_IN);
    private static final CacheKey CACHE_KEY_PHONE_SIGNIN = CacheKey.phoneSignInRequest(PHONE_SIGN_IN);
    private static final CacheKey CACHE_KEY_SIGNIN_TO_SESSION = CacheKey.channelSignInToSessionToken(TOKEN_UNFORMATTED);
    private static final CacheKey CACHE_KEY_PASSWORD_RESET_FOR_EMAIL = CacheKey.passwordResetForEmail(TOKEN, TEST_APP_ID);
    private static final CacheKey CACHE_KEY_PASSWORD_RESET_FOR_PHONE = CacheKey.passwordResetForPhone(TOKEN, TEST_APP_ID);
    private static final CacheKey CACHE_KEY_SPTOKEN = CacheKey.verificationToken(TOKEN);

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("ABC");
    private static final ConsentStatus CONSENTED_STATUS = new ConsentStatus.Builder().withName("Name")
            .withGuid(SUBPOP_GUID).withRequired(true).withConsented(true).build();
    private static final ConsentStatus UNCONSENTED_STATUS = new ConsentStatus.Builder().withName("Name")
            .withGuid(SUBPOP_GUID).withRequired(true).withConsented(false).build();
    private static final Map<SubpopulationGuid, ConsentStatus> CONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SUBPOP_GUID, CONSENTED_STATUS).build();
    private static final Map<SubpopulationGuid, ConsentStatus> UNCONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SUBPOP_GUID, UNCONSENTED_STATUS).build();
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder()
            .withUserId(TEST_USER_ID).withAppId(TEST_APP_ID).build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withId(TEST_USER_ID).build();
    private static final String EXTERNAL_ID = "ext-id";

    private static final StudyParticipant PARTICIPANT_WITH_ATTRIBUTES = new StudyParticipant.Builder().withId(TEST_USER_ID)
            .withHealthCode(HEALTH_CODE).withDataGroups(USER_DATA_GROUPS).withStudyIds(TestConstants.USER_STUDY_IDS)
            .withLanguages(LANGUAGES).build();
    
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_APP_ID, TestConstants.EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_APP_ID, PHONE);

    private static final String STUDY_A = "studyA";
    
    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private BridgeConfig config;
    @Mock
    private ConsentService consentService;
    @Mock
    private AccountService accountService;
    @Mock
    private ParticipantService participantService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private AppService appService;
    @Mock
    private PasswordResetValidator passwordResetValidator;
    @Mock
    private AccountWorkflowService accountWorkflowService;
    @Mock
    private IntentService intentService;
    @Mock
    private OAuthProviderService oauthProviderService;
    @Mock
    private SponsorService sponsorService;
    @Mock
    private AccountSecretDao accountSecretDao;
    @Mock 
    private StudyService studyService;
    @Mock
    private AccountSecret mockSecret;
    @Mock
    private Account mockAccount;
    @Mock
    private ActivityEventService activityEventService;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    @Captor
    private ArgumentCaptor<Account> accountCaptor;
    @Captor
    private ArgumentCaptor<AccountId> accountIdCaptor;
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    @Spy
    @InjectMocks
    private AuthenticationService service;
    
    private App app;

    private Account account;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        // Create inputs.
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setSupportEmail(SUPPORT_EMAIL);
        app.setName("Sender");
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        account = Account.create();
        account.setId(TEST_USER_ID);

        doReturn(SESSION_TOKEN).when(service).getGuid();
        doReturn(app).when(appService).getApp(TEST_APP_ID);
        
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
    }
    
    @AfterMethod
    public void after() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    void setIpAddress(String ipAddress) {
        RequestContext.set(new RequestContext.Builder().withCallerIpAddress(ipAddress).build());
    }
    
    @Test // Test some happy path stuff, like the correct initialization of the user session
    public void signIn() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        app.setReauthenticationEnabled(true);
        
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID);
        
        account.setReauthToken("REAUTH_TOKEN");
        account.setHealthCode(HEALTH_CODE);
        account.setEnrollments(ImmutableSet.of(en1, en2));
        account.setId(TEST_USER_ID);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        
        setIpAddress(IP_ADDRESS);
        
        CriteriaContext context = new CriteriaContext.Builder()
            .withAppId(TEST_APP_ID)
            .withLanguages(LANGUAGES)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/13")).build();
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(contextCaptor.capture(), any());
        doReturn(REAUTH_TOKEN).when(service).generateReauthToken();
        doReturn(Environment.PROD).when(config).getEnvironment();
        
        UserSession session = service.signIn(app, context, EMAIL_PASSWORD_SIGN_IN);
        
        InOrder inOrder = Mockito.inOrder(cacheProvider, accountSecretDao);
        inOrder.verify(accountSecretDao).removeSecrets(REAUTH, TEST_USER_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        inOrder.verify(cacheProvider).setUserSession(session);
        
        assertEquals(session.getConsentStatuses(), CONSENTED_STATUS_MAP);
        assertTrue(session.isAuthenticated());
        assertFalse(session.isSynapseAuthenticated());
        assertEquals(session.getIpAddress(), IP_ADDRESS);
        assertEquals(session.getSessionToken(), SESSION_TOKEN);
        assertEquals(session.getInternalSessionToken(), SESSION_TOKEN);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        assertEquals(session.getEnvironment(), Environment.PROD);
        assertEquals(session.getAppId(), TEST_APP_ID);

        // updated context
        CriteriaContext updatedContext = contextCaptor.getValue();
        assertEquals(updatedContext.getHealthCode(), HEALTH_CODE);
        assertEquals(updatedContext.getLanguages(), LANGUAGES);
        assertEquals(updatedContext.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(updatedContext.getUserStudyIds(), TestConstants.USER_STUDY_IDS);
        assertEquals(updatedContext.getUserId(), TEST_USER_ID);
        
        verify(accountSecretDao).createSecret(AccountSecretType.REAUTH, TEST_USER_ID, REAUTH_TOKEN);
    }
    
    @Test
    public void signInWithAccountNotFound() throws Exception {
        app.setReauthenticationEnabled(true);
        when(accountDao.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));
                
        try {
            service.signIn(app, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        
        // Do not change anything about the session, don't rotate the reauth keys, etc.
        verifyNoMoreInteractions(cacheProvider);
        verifyNoMoreInteractions(accountSecretDao);
        verifyNoMoreInteractions(accountService);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void signInWithBadCredentials() throws Exception {
        service.signIn(app, CONTEXT, new SignIn.Builder().build());
    }
    
    @Test
    public void signInThrowsConsentRequiredException() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        app.setReauthenticationEnabled(true);
        
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID);
        
        account.setReauthToken("REAUTH_TOKEN");
        account.setHealthCode(HEALTH_CODE);
        account.setEnrollments(ImmutableSet.of(en1, en2));
        account.setId(TEST_USER_ID);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));

        setIpAddress(IP_ADDRESS);
        
        CriteriaContext context = new CriteriaContext.Builder()
            .withAppId(TEST_APP_ID)
            .withLanguages(LANGUAGES)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/13")).build();
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(contextCaptor.capture(), any());
        doReturn(REAUTH_TOKEN).when(service).generateReauthToken();
        doReturn(Environment.PROD).when(config).getEnvironment();
        
        UserSession session = null;
        try {
            session = service.signIn(app, context, EMAIL_PASSWORD_SIGN_IN);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            session = e.getUserSession();
        }
        InOrder inOrder = Mockito.inOrder(cacheProvider, accountSecretDao);
        inOrder.verify(accountSecretDao).removeSecrets(REAUTH, TEST_USER_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        inOrder.verify(cacheProvider).setUserSession(session);
        
        assertEquals(session.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        assertTrue(session.isAuthenticated());
        assertEquals(session.getIpAddress(), IP_ADDRESS);
        assertEquals(session.getSessionToken(), SESSION_TOKEN);
        assertEquals(session.getInternalSessionToken(), SESSION_TOKEN);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        assertEquals(session.getEnvironment(), Environment.PROD);
        assertEquals(session.getAppId(), TEST_APP_ID);

        // updated context
        CriteriaContext updatedContext = contextCaptor.getValue();
        assertEquals(updatedContext.getHealthCode(), HEALTH_CODE);
        assertEquals(updatedContext.getLanguages(), LANGUAGES);
        assertEquals(updatedContext.getUserDataGroups(), USER_DATA_GROUPS);
        assertEquals(updatedContext.getUserStudyIds(), TestConstants.USER_STUDY_IDS);
        assertEquals(updatedContext.getUserId(), TEST_USER_ID);
        
        verify(accountSecretDao).createSecret(AccountSecretType.REAUTH, TEST_USER_ID, REAUTH_TOKEN);
    }
    
    @Test
    public void signInWithEmail() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setId(TEST_USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(app, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void unconsentedSignInWithEmail() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(app, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithEmail() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setReauthToken(REAUTH_TOKEN);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        // Does not throw consent required exception, despite being unconsented, because user has DEVELOPER role.
        UserSession retrieved = service.signIn(app, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        assertEquals(retrieved.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
    }
    
    @Test
    public void signInWithPhone() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setId(TEST_USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        doReturn(Optional.of(account)).when(accountDao).getAccount(PHONE_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(app, CONTEXT, PHONE_PASSWORD_SIGN_IN);
        
        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void unconsentedSignInWithPhone() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));

        doReturn(Optional.of(account)).when(accountDao).getAccount(PHONE_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(app, CONTEXT, PHONE_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithPhone() throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setReauthToken(REAUTH_TOKEN);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(PHONE_PASSWORD_SIGN_IN.getAccountId());
        
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        // Does not throw consent required exception, despite being unconsented, because user has RESEARCHER role. 
        UserSession retrieved = service.signIn(app, CONTEXT, PHONE_PASSWORD_SIGN_IN);

        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        assertEquals(retrieved.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
    }
    
    @Test
    public void signOut() {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setReauthToken(TOKEN);
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").withId(TEST_USER_ID).build());
        
        when(accountDao.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        service.signOut(session);
        
        verify(accountSecretDao).removeSecrets(REAUTH, TEST_USER_ID);
        verify(cacheProvider).removeSession(session);
    }
    
    @Test
    public void signOutNoSessionToken() {
        service.signOut(null);
        
        verify(accountSecretDao, never()).removeSecrets(any(), any());
        verify(cacheProvider, never()).removeSession(any());
    }
    
    @Test
    public void signOutNoAccount() {
        UserSession session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setReauthToken(TOKEN);
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").withId(TEST_USER_ID).build());

        when(accountService.getAccount(any())).thenReturn(Optional.empty());
        
        service.signOut(session);

        verify(accountSecretDao, never()).removeSecrets(any(), any());
        verify(cacheProvider, never()).removeSession(any());
    }

    @Test
    public void emailSignIn() {
        account.setId(TEST_USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);

        assertNotNull(retSession);
        assertEquals(retSession.getReauthToken(), REAUTH_TOKEN);
        assertFalse(retSession.isSynapseAuthenticated());

        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        inOrder.verify(accountDao).updateAccount(account);
        inOrder.verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        inOrder.verify(cacheProvider).setUserSession(retSession);
        inOrder.verify(cacheProvider).setExpiration(CACHE_KEY_EMAIL_SIGNIN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
        inOrder.verify(cacheProvider).setObject(CACHE_KEY_SIGNIN_TO_SESSION, SESSION_TOKEN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
    }

    // branch coverage
    @Test
    public void emailSignIn_CachedTokenWithNoSession() {
        account.setId(TEST_USER_ID);
        account.setReauthToken(REAUTH_TOKEN);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(cacheProvider.getObject(CACHE_KEY_SIGNIN_TO_SESSION, String.class)).thenReturn(SESSION_TOKEN);
        when(cacheProvider.getUserSession(SESSION_TOKEN)).thenReturn(null);

        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);

        assertNotNull(retSession);
        assertEquals(retSession.getReauthToken(), REAUTH_TOKEN);

        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        inOrder.verify(accountDao).updateAccount(account);
        inOrder.verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        inOrder.verify(cacheProvider).setUserSession(retSession);
        inOrder.verify(cacheProvider).setExpiration(CACHE_KEY_EMAIL_SIGNIN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
        inOrder.verify(cacheProvider).setObject(CACHE_KEY_SIGNIN_TO_SESSION, SESSION_TOKEN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
    }

    @Test
    public void emailSignIn_CachedSession() {
        account.setId(TEST_USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(cacheProvider.getObject(CACHE_KEY_SIGNIN_TO_SESSION, String.class)).thenReturn(SESSION_TOKEN);

        UserSession cachedSession = new UserSession();
        cachedSession.setSessionToken(SESSION_TOKEN);
        cachedSession.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(cacheProvider.getUserSession(SESSION_TOKEN)).thenReturn(cachedSession);

        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
        assertNotNull(retSession);

        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        inOrder.verify(accountDao).updateAccount(account);

        // Because we got the cached session, we don't do certain operations.
        verify(accountDao).updateAccount(account);
        verify(cacheProvider, never()).removeSessionByUserId(any());
        verify(cacheProvider, never()).setUserSession(any());
        verify(cacheProvider, never()).setExpiration(any(), anyInt());
        verify(cacheProvider, never()).setObject(any(), any(), anyInt());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void emailSignInNoAccount() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountService.getAccount(any())).thenReturn(Optional.empty());
        service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void emailSignIn_NoCachedToken() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(null);
        service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void emailSignIn_WrongToken() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn("badtoken");
        service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void emailSignIn_WrongEmail() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);

        SignIn wrongEmailSignIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail("wrong-email@email.com")
                .withToken(TOKEN).build();
        service.channelSignIn(ChannelType.EMAIL, CONTEXT, wrongEmailSignIn);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void emailSignInInvalidEntity() {
        service.channelSignIn(ChannelType.EMAIL, CONTEXT, SIGN_IN_REQUEST_WITH_EMAIL);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void emailSignInThrowsAccountDisabled() {
        account.setStatus(AccountStatus.DISABLED);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());

        service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
    }

    @Test
    public void emailSignInThrowsConsentRequired() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withStatus(AccountStatus.DISABLED).build();

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        try {
            service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());
            assertEquals(e.getUserSession().getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        }
    }

    @Test
    public void emailSignInAdminOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withRoles(Sets.newHashSet(Roles.ADMIN)).build();

        doReturn(participant).when(participantService).getParticipant(app, account, false);
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_SIGN_IN.getAccountId());
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Does not throw a consent required exception because the participant is an admin.
        UserSession retrieved = service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
        assertEquals(retrieved.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
    }

    @Test
    public void reauthentication() {
        app.setReauthenticationEnabled(true);
        account.setId(TEST_USER_ID);
        
        doReturn(REAUTH_TOKEN).when(service).generateReauthToken();

        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID).withEmail(EMAIL).build();
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(any());
        doReturn(Optional.of(AccountSecret.create())).when(accountSecretDao)
            .verifySecret(REAUTH, TEST_USER_ID, REAUTH_SIGN_IN.getReauthToken(), ROTATIONS);
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        
        UserSession session = service.reauthenticate(app, CONTEXT, REAUTH_SIGN_IN);
        assertEquals(session.getParticipant().getEmail(), EMAIL);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        
        verify(cacheProvider).setUserSession(sessionCaptor.capture());
        
        UserSession captured = sessionCaptor.getValue();
        assertEquals(captured.getParticipant().getEmail(), EMAIL);
        assertEquals(captured.getReauthToken(), REAUTH_TOKEN);
        
        verify(accountSecretDao).createSecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void reauthenticateThrowsConsentRequiredException() {
        app.setReauthenticationEnabled(true);

        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID).withEmail(EMAIL).build();
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(Optional.of(account)).when(accountDao).getAccount(any());
        doReturn(Optional.of(AccountSecret.create())).when(accountSecretDao)
            .verifySecret(REAUTH, TEST_USER_ID, REAUTH_SIGN_IN.getReauthToken(), ROTATIONS);
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        
        service.reauthenticate(app, CONTEXT, REAUTH_SIGN_IN);
    }
    
    @Test
    public void reauthenticateIgnoresConsentForAdmins() {
        app.setReauthenticationEnabled(true);

        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withRoles(ImmutableSet.of(Roles.DEVELOPER)).withEmail(EMAIL).build();
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(any());
        doReturn(Optional.of(AccountSecret.create())).when(accountSecretDao)
            .verifySecret(REAUTH, TEST_USER_ID, REAUTH_SIGN_IN.getReauthToken(), ROTATIONS);

        doReturn(participant).when(participantService).getParticipant(app, account, false);
        
        service.reauthenticate(app, CONTEXT, REAUTH_SIGN_IN);
    }
    
    @Test
    public void reauthenticationPersistsExistingSessionTokens() {
        app.setReauthenticationEnabled(true);
        
        UserSession existing = new UserSession();
        existing.setSessionToken("existingToken");
        existing.setInternalSessionToken("existingInternalToken");
        doReturn(existing).when(cacheProvider).getUserSessionByUserId(TEST_USER_ID);

        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID).withEmail(EMAIL).build();
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(any());
        doReturn(Optional.of(AccountSecret.create())).when(accountSecretDao)
            .verifySecret(REAUTH, TEST_USER_ID, REAUTH_SIGN_IN.getReauthToken(), ROTATIONS);
        
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        
        UserSession session = service.reauthenticate(app, CONTEXT, REAUTH_SIGN_IN);
        assertEquals(session.getSessionToken(), "existingToken");
        assertEquals(session.getInternalSessionToken(), "existingInternalToken");
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void reauthTokenRequired() {
        app.setReauthenticationEnabled(true);
        
        service.reauthenticate(app, CONTEXT, EMAIL_SIGN_IN); // doesn't have reauth token
    }
    
    @Test
    public void reauthThrowsUnconsentedException() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withStatus(AccountStatus.ENABLED).build();
        
        app.setReauthenticationEnabled(true);
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(any());
        doReturn(Optional.of(AccountSecret.create())).when(accountSecretDao)
            .verifySecret(AccountSecretType.REAUTH, TEST_USER_ID, REAUTH_SIGN_IN.getReauthToken(), ROTATIONS);
        
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        try {
            service.reauthenticate(app, CONTEXT, REAUTH_SIGN_IN);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            assertEquals(e.getUserSession().getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        }
    }
    
    @Test
    public void signUpWithEmailOK() {
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL).withPassword(PASSWORD)
                .build();
        
        service.signUp(app, participant);
        
        verify(participantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getEmail(), EMAIL);
        assertEquals(captured.getPassword(), PASSWORD);
    }

    @Test
    public void signUpWithPhoneOK() {
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE)
                .withPassword(PASSWORD).build();
        
        service.signUp(app, participant);
        
        verify(participantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getPhone().getNumber(), TestConstants.PHONE.getNumber());
        assertEquals(captured.getPassword(), PASSWORD);
    }
    
    @Test
    public void signUpExistingAccount() {
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(EMAIL).withPassword(PASSWORD)
                .build();
        doThrow(new EntityAlreadyExistsException(Account.class, "userId", "user-id")).when(participantService)
                .createParticipant(app, participant, true);
        
        service.signUp(app, participant);
        
        verify(participantService).createParticipant(eq(app), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(app), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals(captured.getId(), "user-id");
        assertEquals(captured.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void signUpExistingExternalId() {
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        doThrow(new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", EXTERNAL_ID)).when(participantService)
                .createParticipant(app, participant, true);
        
        service.signUp(app, participant);
        
        verify(participantService).createParticipant(eq(app), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(app), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals(captured.getExternalId(), EXTERNAL_ID);
        assertEquals(captured.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void signUpExistingUnknownEntity() {
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        doThrow(new EntityAlreadyExistsException(AppConfig.class, "identifier", EXTERNAL_ID)).when(participantService)
                .createParticipant(app, participant, true);
        
        service.signUp(app, participant);
        
        verify(participantService).createParticipant(eq(app), any(), eq(true));
        
        // We don't send a message. That's the logic... it's debatable.
        verify(accountWorkflowService, never()).notifyAccountExists(any(), any());
    }

    @Test
    public void phoneSignIn() {
        account.setId(TEST_USER_ID);

        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder().withDataGroups(USER_DATA_GROUPS)
                .withEmail(EMAIL).withHealthCode(HEALTH_CODE).withId(TEST_USER_ID).withLanguages(LANGUAGES)
                .withFirstName("Test").withLastName("Tester").withPhone(TestConstants.PHONE).build();
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(PHONE_SIGN_IN.getAccountId());
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate.
        UserSession session = service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);

        assertEquals(session.getParticipant().getEmail(), EMAIL);
        assertEquals(session.getParticipant().getFirstName(), "Test");
        assertEquals(session.getParticipant().getLastName(), "Tester");
        assertFalse(session.isSynapseAuthenticated());

        // this doesn't pass if our mock calls above aren't executed, but verify these:
        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(PHONE_SIGN_IN.getAccountId());
        inOrder.verify(accountDao).updateAccount(account);
        inOrder.verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
        inOrder.verify(cacheProvider).setUserSession(session);
        inOrder.verify(cacheProvider).setExpiration(CACHE_KEY_PHONE_SIGNIN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
        inOrder.verify(cacheProvider).setObject(CACHE_KEY_SIGNIN_TO_SESSION, SESSION_TOKEN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
    }

    @Test
    public void phoneSignIn_TokenFormattedWithSpace() {
        account.setId(TEST_USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(PHONE_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate. Just verify that it succeeds and doesn't throw. Details are tested in above tests.
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(TestConstants.PHONE).withToken("ABC DEF")
                .build();
        service.channelSignIn(ChannelType.PHONE, CONTEXT, signIn);
    }

    @Test
    public void phoneSignIn_UnformattedToken() {
        account.setId(TEST_USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(Optional.of(account)).when(accountDao).getAccount(any());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate. Just verify that it succeeds and doesn't throw. Details are tested in above tests.
        SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(TestConstants.PHONE)
                .withToken(TOKEN_UNFORMATTED).build();
        service.channelSignIn(ChannelType.PHONE, CONTEXT, signIn);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void phoneSignInNoAccount() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountService.getAccount(any())).thenReturn(Optional.empty());
        service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void phoneSignIn_NoCachedToken() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(null);
        service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void phoneSignIn_WrongToken() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn("badtoken");
        service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void phoneSignIn_WrongPhone() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);

        SignIn wrongPhoneSignIn = new SignIn.Builder().withAppId(TEST_APP_ID)
                .withPhone(new Phone("4082588569", "US")).withToken(TOKEN).build();
        service.channelSignIn(ChannelType.PHONE, CONTEXT, wrongPhoneSignIn);
    }

    @Test
    public void phoneSignInThrowsConsentRequired() {
        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder().withId(TEST_USER_ID)
                .withEmail(EMAIL).withFirstName("Test").withLastName("Tester").build();
        doReturn(participant).when(participantService).getParticipant(app, account, false);
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(Optional.of(account)).when(accountDao).getAccount(PHONE_SIGN_IN.getAccountId());

        try {
            service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());
            assertEquals(e.getUserSession().getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        }
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void verifyEmailInvalid() {
        Verification ev = new Verification(null);
        service.verifyChannel(ChannelType.EMAIL, ev);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void verifyPhoneInvalid() {
        Verification ev = new Verification(null);
        service.verifyChannel(ChannelType.PHONE, ev);
    }
    
    @Test
    public void languagesArePersistedFromContext() {
        // This specifically has to be a mock to easily mock the editAccount method on the DAO.
        Account mockAccount = mock(Account.class);

        CriteriaContext context = new CriteriaContext.Builder().withLanguages(LANGUAGES)
                .withUserId(TEST_USER_ID).withAppId(TEST_APP_ID).build();
        TestUtils.mockEditAccount(accountService, mockAccount);
        doReturn(Optional.of(mockAccount)).when(accountDao).getAccount(any());
        
        // No languages.
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build();
        doReturn(participant).when(participantService).getParticipant(app, mockAccount, false);
        
        service.getSession(app, context);
        
        verify(accountService).editAccount(eq(ACCOUNT_ID_WITH_HEALTHCODE), any());
        verify(mockAccount).setLanguages(ImmutableList.copyOf(LANGUAGES));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void generatePasswordExternalIdNotSubmitted() {
        service.generatePassword(app, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordNoAccountDoNotCreateAccount() {
        service.generatePassword(app, EXTERNAL_ID);
    }
    
    @Test
    public void generatePasswordAccountNotFound() {
        try {
            service.generatePassword(app, EXTERNAL_ID);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(participantService, never()).createParticipant(any(), any(), anyBoolean());
    }
    
    @Test
    public void changePassword() throws Exception {
        Account account = mockGetAccountById(ACCOUNT_ID, false);
        account.setStatus(UNVERIFIED);
        
        service.changePassword(account, ChannelType.PHONE, "asdf");
        verify(accountDao).updateAccount(account);
    }

    @Test
    public void changePasswordSuccess() throws Exception {
        when(passwordResetValidator.supports(any())).thenReturn(true);

        // Set up test account
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(UNVERIFIED);

        // execute and verify
        service.changePassword(account, ChannelType.EMAIL, PASSWORD);
        verify(accountDao).updateAccount(accountCaptor.capture());

        Account updatedAccount = accountCaptor.getValue();
        assertEquals(updatedAccount.getId(), TEST_USER_ID);
        assertEquals(updatedAccount.getModifiedOn(), MODIFIED_ON);
        assertEquals(updatedAccount.getPasswordAlgorithm(), PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        assertEquals(updatedAccount.getPasswordModifiedOn(), MODIFIED_ON);
        assertTrue(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);

        // validate password hash
        assertTrue(DEFAULT_PASSWORD_ALGORITHM.checkHash(updatedAccount.getPasswordHash(), PASSWORD));
    }

    @Test
    public void changePasswordForPhone() throws Exception {
        // Set up test account
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setPhone(PHONE);
        account.setStatus(UNVERIFIED);

        // execute and verify
        service.changePassword(account, ChannelType.PHONE, PASSWORD);
        verify(accountDao).updateAccount(accountCaptor.capture());

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        Account updatedAccount = accountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertTrue(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }
    
    @Test
    public void changePasswordForExternalId() {
        Enrollment en = Enrollment.create(TEST_APP_ID, STUDY_A, TEST_USER_ID);
        en.setExternalId("anExternalId");
        
        // Set up test account
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setStatus(UNVERIFIED);
        account.getEnrollments().add(en);

        // execute and verify
        service.changePassword(account, null, PASSWORD);
        verify(accountDao).updateAccount(accountCaptor.capture());

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        Account updatedAccount = accountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(updatedAccount.getStatus(), ENABLED);
    }

    @Test
    public void generatePasswordExternalIdNotFound() {
        Account account = Account.create();
        account.setEnrollments(new HashSet<>());
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        
        try {
            service.generatePassword(app, EXTERNAL_ID);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(participantService, never()).createParticipant(any(), any(), anyBoolean());
    }
    
    @Test
    public void generatePasswordOK() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        doReturn(PASSWORD).when(service).generatePassword(anyInt());
        
        Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, "account");
        enrollment.setExternalId(EXTERNAL_ID);
        account.setEnrollments(ImmutableSet.of(enrollment));
        
        when(accountDao.getAccount(any())).thenReturn(Optional.of(account));
        account.setHealthCode(HEALTH_CODE);
        
        GeneratedPassword password = service.generatePassword(app, EXTERNAL_ID);
        assertEquals(password.getExternalId(), EXTERNAL_ID);
        assertEquals(password.getPassword(), PASSWORD);
        
        verify(accountDao).updateAccount(account);
        
        assertEquals(account.getModifiedOn(), MODIFIED_ON);
        assertEquals(account.getPasswordAlgorithm(), DEFAULT_PASSWORD_ALGORITHM);
        assertNotEquals(account.getPasswordHash(), PASSWORD);
        assertEquals(account.getPasswordModifiedOn(), MODIFIED_ON);
    }
    
    @Test
    public void generatedPasswordPassesValidation() {
        // This is a very large password, which you could set in a app like this
        String password = service.generatePassword(100);
        
        App app = mock(App.class);
        when(app.getPasswordPolicy()).thenReturn(DEFAULT_PASSWORD_POLICY);
        
        AppService appService = mock(AppService.class);
        when(appService.getApp(TEST_APP_ID)).thenReturn(app);
        
        PasswordResetValidator validator = new PasswordResetValidator();
        validator.setAppService(appService);
        
        assertEquals(password.length(), 100);
        
        PasswordReset passwordReset = new PasswordReset(password, "sptoken", TEST_APP_ID);
        Validate.entityThrowingException(validator, passwordReset);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordExternalIdMismatchesCallerStudies() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("studyB")).build());
        
        account.setEnrollments(ImmutableSet.of(Enrollment.create(app.getIdentifier(), "studyA", "id")));
        
        service.generatePassword(app, EXTERNAL_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void generatePasswordAccountMismatchesCallerStudies() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("studyA")).build());
        
        when(accountDao.getAccount(any())).thenReturn(Optional.of(account));
        Enrollment en = Enrollment.create(app.getIdentifier(), "studyB", "id", EXTERNAL_ID);
        account.setEnrollments(Sets.newHashSet(en));
        
        service.generatePassword(app, EXTERNAL_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordExternalIdInStudyNotAccessibleToCaller() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("studyA")).build());
        
        // The account is returned but filtering has been applied such that the
        // external ID is not there, despite using it to retrieve the account.
        when(accountService.getAccount(any())).thenReturn(Optional.of(account));
        Enrollment en = Enrollment.create(app.getIdentifier(), "studyB", "id");
        account.setEnrollments(Sets.newHashSet(en));
        
        service.generatePassword(app, EXTERNAL_ID);
    }
    
    @Test
    public void creatingExternalIdOnlyAccountSucceedsIfIdsManaged() {
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).withPhone(null).withExternalId("id").build();
        service.signUp(app, participant);
        
        verify(participantService).createParticipant(app, participant, true);
    }
    
    @Test
    public void signInWithIntentToParticipate()
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setId(TEST_USER_ID);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        
        Account consentedAccount = Account.create();
        consentedAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        consentedAccount.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));

        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(app, account,
                false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        doReturn(Optional.of(consentedAccount)).when(accountDao).getAccount(any());
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(app, consentedAccount,
                false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(consentedAccount));
        
        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(app, account)).thenReturn(true);
        
        service.signIn(app, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void emailSignInWithIntentToParticipate() {
        Account consentedAccount = Account.create();
        consentedAccount.setId(TEST_USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(
                Optional.of(account), Optional.of(consentedAccount));
        when(participantService.getParticipant(app, account, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(UNCONSENTED_STATUS_MAP);

        when(participantService.getParticipant(app, consentedAccount, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(consentedAccount))).thenReturn(CONSENTED_STATUS_MAP);

        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(app, account)).thenReturn(true);

        service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void phoneSignInWithIntentToParticipate() {
        Account consentedAccount = Account.create();
        consentedAccount.setId(TEST_USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(
               Optional.of(account), Optional.of(consentedAccount));
        when(participantService.getParticipant(app, account, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(UNCONSENTED_STATUS_MAP);

        when(participantService.getParticipant(app, consentedAccount, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(consentedAccount))).thenReturn(CONSENTED_STATUS_MAP);

        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(app, account)).thenReturn(true);

        service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);
    }

    @Test
    public void consentedSignInDoesNotExecuteIntentToParticipate()
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        
        doReturn(Optional.of(account)).when(accountDao).getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(app, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));

        service.signIn(app, CONTEXT, EMAIL_PASSWORD_SIGN_IN);

        verify(intentService, never()).registerIntentToParticipate(app, account);
    }

    @Test
    public void consentedEmailSignInDoesNotExecuteIntentToParticipate() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(Optional.of(account));
        when(participantService.getParticipant(app, account, false)).thenReturn(PARTICIPANT);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(CONSENTED_STATUS_MAP);

        service.channelSignIn(ChannelType.EMAIL, CONTEXT, EMAIL_SIGN_IN);

        verify(intentService, never()).registerIntentToParticipate(app, account);
    }

    @Test
    public void consentedPhoneSignInDoesNotExecuteIntentToParticipate() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(Optional.of(account));
        when(participantService.getParticipant(app, account, false)).thenReturn(PARTICIPANT);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(CONSENTED_STATUS_MAP);

        service.channelSignIn(ChannelType.PHONE, CONTEXT, PHONE_SIGN_IN);

        verify(intentService, never()).registerIntentToParticipate(app, account);
    }

    // Most of the other behaviors are tested in other methods. This test specifically tests the session created has
    // the correct attributes.
    @Test
    public void getSessionFromAccount() {
        // Create inputs.
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setReauthenticationEnabled(true);
        
        setIpAddress(IP_ADDRESS);
        
        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID).build();

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withOrgMembership(TEST_ORG_ID)
                .build();
        
        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(participant);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        when(service.generateReauthToken()).thenReturn(REAUTH_TOKEN);
        when(sponsorService.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID)).thenReturn(USER_STUDY_IDS);
        
        // Execute and validate.
        UserSession session = service.getSessionFromAccount(app, context, account);
        assertSame(session.getParticipant(), participant);
        assertNotNull(session.getSessionToken());
        assertNotNull(session.getInternalSessionToken());
        assertTrue(session.isAuthenticated());
        assertEquals(session.getEnvironment(), Environment.LOCAL);
        assertEquals(session.getIpAddress(), IP_ADDRESS);
        assertEquals(session.getAppId(), TEST_APP_ID);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        assertEquals(session.getConsentStatuses(), CONSENTED_STATUS_MAP);
        
        verify(accountSecretDao).createSecret(AccountSecretType.REAUTH, TEST_USER_ID, REAUTH_TOKEN);
        
        RequestContext retValue = RequestContext.updateFromSession(session, sponsorService);
        assertEquals(retValue.getCallerAppId(), TEST_APP_ID);
        assertEquals(retValue.getOrgSponsoredStudies(), USER_STUDY_IDS);
        assertEquals(retValue.getCallerUserId(), TEST_USER_ID);
        assertEquals(retValue.getCallerOrgMembership(), TEST_ORG_ID);
    }
    
    @Test
    public void getSessionFromAccountWithoutReauthentication() {
        // Create inputs.
        App app = App.create();
        app.setReauthenticationEnabled(false);

        setIpAddress(IP_ADDRESS);

        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID).build();

        Account account = Account.create();

        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(PARTICIPANT);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        
        // Execute and validate.
        UserSession session = service.getSessionFromAccount(app, context, account);
        assertNull(session.getReauthToken());
        
        verify(service, never()).generateReauthToken();
        verify(accountSecretDao, never()).createSecret(any(), any(), any());
    }

    // branch coverage
    @Test
    public void getSessionFromAccountReauthenticationFlagNull() {
        // Create inputs.
        App app = App.create();
        app.setReauthenticationEnabled(null);

        setIpAddress(IP_ADDRESS);

        CriteriaContext context = new CriteriaContext.Builder().withAppId(TEST_APP_ID).build();

        Account account = Account.create();

        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(PARTICIPANT);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);

        // Execute and validate.
        UserSession session = service.getSessionFromAccount(app, context, account);
        assertNull(session.getReauthToken());

        verify(service, never()).generateReauthToken();
        verify(accountSecretDao, never()).createSecret(any(), any(), any());
    }

    @Test
    public void getSessionSucceeds() {
        UserSession session = new UserSession();
        when(cacheProvider.getUserSession(TOKEN)).thenReturn(session);
        
        UserSession retValue = service.getSession(TOKEN);
        assertEquals(retValue, session);
        
        verify(cacheProvider).getUserSession(TOKEN);
    }
    
    @Test
    public void getSessionFails() {
        when(cacheProvider.getUserSession(TOKEN)).thenReturn(null);
        
        UserSession retValue = service.getSession(TOKEN);
        assertNull(retValue);
        
        verify(cacheProvider).getUserSession(TOKEN);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp="Account not found.")
    public void getSessionNotFound() {
        CriteriaContext context = new CriteriaContext.Builder().withUserId(TEST_USER_ID)
                .withAppId(TEST_APP_ID).build();        
        
        service.getSession(app, context);
    }
    
    @Test
    public void getSessionNoToken() {
        assertNull( service.getSession(null) );
        verify(cacheProvider, never()).getUserSession(TOKEN);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void resetPasswordInvalid() {
        PasswordResetValidator validator = new PasswordResetValidator();
        validator.setAppService(appService);
        service.setPasswordResetValidator(validator);
        
        PasswordReset reset = new PasswordReset(PASSWORD, null, TEST_APP_ID);
        service.resetPassword(reset);
    }
    
    @Test
    public void existingLanguagePreferencesAreLoaded()
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        // Language prefs in the user object and the criteria context are different; the values from the 
        // database are taken. These cannot be picked up from the HTTP request once they are set.
        account.setLanguages(TestConstants.LANGUAGES);
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        
        when(accountDao.getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId()))
            .thenReturn(Optional.of(account));
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withId(TEST_USER_ID).withLanguages(TestConstants.LANGUAGES).build();
        when(participantService.getParticipant(app, account, false)).thenReturn(participant);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withContext(CONTEXT)
                .withLanguages(ImmutableList.of("es")).build();
        
        UserSession session = service.signIn(app, context, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(session.getParticipant().getLanguages(), TestConstants.LANGUAGES);
        
        verify(accountService, never()).editAccount(any(), any());
   }
    
    @Test
    public void languagePreferencesArePersisted()
            throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
        account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
        
        // Language prefs are not persisted, so the context should cause an update
        when(accountDao.getAccount(EMAIL_PASSWORD_SIGN_IN.getAccountId()))
            .thenReturn(Optional.of(account));
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withHealthCode(HEALTH_CODE).build();
        
        when(participantService.getParticipant(app, account, false)).thenReturn(participant);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withContext(CONTEXT)
                .withLanguages(TestConstants.LANGUAGES).build();
        
        UserSession session = service.signIn(app, context, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(session.getParticipant().getLanguages(), TestConstants.LANGUAGES);
        
        // Note that the context does not have the healthCode, you must use the participant
        verify(accountService).editAccount(eq(ACCOUNT_ID_WITH_HEALTHCODE), any());
   }
    
   @Test
   public void oauthSignIn() { 
       OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_APP_ID, "vendorId",
               "authToken", "callbackUrl");
       AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
       when(oauthProviderService.oauthSignIn(token)).thenReturn(accountId);
       
       account.setRoles(ImmutableSet.of(DEVELOPER));
       when(accountDao.getAccount(accountId)).thenReturn(Optional.of(account));
       
       StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId("12345").build();
       when(participantService.getParticipant(any(), eq(account), eq(false))).thenReturn(participant);
       
       UserSession session = service.oauthSignIn(CONTEXT, token);
       
       assertEquals(session.getParticipant().getSynapseUserId(), "12345");
       assertTrue(session.isSynapseAuthenticated());
       verify(accountSecretDao).removeSecrets(REAUTH, TEST_USER_ID);
       verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
       verify(cacheProvider).setUserSession(session);
   }
   
   @Test(expectedExceptions = EntityNotFoundException.class)
   public void oauthSignInNotFoundWrongToken() {
       OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_APP_ID, "vendorId",
               "authToken", "callbackUrl");
       AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
       when(oauthProviderService.oauthSignIn(token)).thenReturn(accountId);
       
       service.oauthSignIn(CONTEXT, token);
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void oauthSignInNotFoundNoToken() {
       OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_APP_ID, "vendorId",
               "authToken", "callbackUrl");
       
       service.oauthSignIn(CONTEXT, token);
   }
   
   @Test(expectedExceptions = UnauthorizedException.class)
   public void oauthSignInNotAnAdministrativeUser() {
       OAuthAuthorizationToken token = new OAuthAuthorizationToken(TEST_APP_ID, "vendorId",
               "authToken", "callbackUrl");
       AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
       when(oauthProviderService.oauthSignIn(token)).thenReturn(accountId);
       
       when(accountDao.getAccount(accountId)).thenReturn(Optional.of(account));
       
       StudyParticipant participant = new StudyParticipant.Builder().withSynapseUserId("12345").build();
       when(participantService.getParticipant(any(), eq(account), eq(false))).thenReturn(participant);
       
       service.oauthSignIn(CONTEXT, token);
   }
   
   @Test
   public void signUp_newExternalIdsFormatIgnoresMigrationCode() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalIds(ImmutableMap.of(TEST_APP_ID, "externalId"))
               .build();
       
       service.signUp(app, participant);
       
       verify(accountService, never()).getAccount(any());
       verify(studyService, never()).getStudyIds(TEST_APP_ID);
   }
   
   @Test
   public void signUp_bothExternalIdFormatsIgnoresMigrationCode() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalId(EXTERNAL_ID)
               .withExternalIds(ImmutableMap.of(TEST_APP_ID, EXTERNAL_ID))
               .build();
       
       service.signUp(app, participant);
       
       verify(accountService, never()).getAccount(any());
       verify(studyService, never()).getStudyIds(TEST_APP_ID);
   }
   
   @Test
   public void signIn_oldExternalIdsFormatForExistingAccountReturnsQuietly() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalId(EXTERNAL_ID)
               .build();
       
       AccountId accountId = AccountId.forExternalId(TEST_APP_ID, EXTERNAL_ID);
       when(accountDao.getAccount(accountId)).thenReturn(Optional.of(account));
       
       IdentifierHolder retValue = service.signUp(app, participant);
       assertEquals(retValue.getIdentifier(), TEST_USER_ID);
       
       verify(accountDao).getAccount(accountId);
       verify(studyService, never()).getStudyIds(TEST_APP_ID);
       verify(participantService, never()).createParticipant(app, participant, true);
   }
   
   @Test
   public void signUp_oldExternalIdsFormatOneStudyRemapsToStudy() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalId(EXTERNAL_ID)
               .build();
       
       when(studyService.getStudyIds(TEST_APP_ID)).thenReturn(ImmutableSet.of("studyA"));
       
       service.signUp(app, participant);
       
       verify(participantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
       Map<String,String> map = participantCaptor.getValue().getExternalIds();
       assertEquals(map.get("studyA"), EXTERNAL_ID);
   }
   
   @Test
   public void signUp_oldExternalIdsFormatTwoStudiesOneTestRemapsToOtherStudy() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalId(EXTERNAL_ID)
               .build();
       
       when(studyService.getStudyIds(TEST_APP_ID)).thenReturn(ImmutableSet.of("test", "studyA"));
       
       service.signUp(app, participant);
       
       verify(participantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
       Map<String,String> map = participantCaptor.getValue().getExternalIds();
       assertEquals(map.get("studyA"), EXTERNAL_ID);       
   }

   @Test
   public void signUp_oldExternalIdsFormatNoStudiesDoesNotRemap() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalId(EXTERNAL_ID)
               .build();
       
       when(studyService.getStudyIds(TEST_APP_ID)).thenReturn(ImmutableSet.of());
       
       service.signUp(app, participant);
       
       verify(participantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
       assertTrue(participantCaptor.getValue().getExternalIds().isEmpty());
       assertEquals(participantCaptor.getValue().getExternalId(), EXTERNAL_ID);
   }

   @Test
   public void signUp_oldExternalIdsFormatTwoOrMoreStudiesPicksOne() {
       StudyParticipant participant = new StudyParticipant.Builder()
               .withExternalId(EXTERNAL_ID)
               .build();
       
       when(studyService.getStudyIds(TEST_APP_ID)).thenReturn(ImmutableSet.of("test", "studyA", "studyB"));
       
       service.signUp(app, participant);
       
       verify(participantService).createParticipant(eq(app), participantCaptor.capture(), eq(true));
       Map<String,String> map = participantCaptor.getValue().getExternalIds();
       String extId =  map.containsKey("studyA") ? map.get("studyA") : map.get("studyB");
       assertEquals(extId, EXTERNAL_ID);
       assertFalse(map.keySet().contains("test"));
   }
   
   @Test
   public void verifyChannel() throws Exception {
       Account account = mockGetAccountById(ACCOUNT_ID, false);
       account.setEmailVerified(false);
       
       service.verifyChannel(ChannelType.EMAIL, account);
       verify(accountDao).updateAccount(account);
   }

   @Test
   public void verifyEmailUsingToken() {
       Account account = Account.create();
       account.setAppId(TEST_APP_ID);
       account.setId(TEST_USER_ID);
       account.setEmail(EMAIL);
       account.setStatus(UNVERIFIED);
       account.setEmailVerified(FALSE);

       service.verifyChannel(ChannelType.EMAIL, account);

       verify(accountDao).updateAccount(account);
       assertEquals(account.getStatus(), ENABLED);
       assertEquals(account.getEmailVerified(), TRUE);
       assertEquals(account.getModifiedOn(), MODIFIED_ON);
       assertEquals(account.getStatus(), ENABLED);
       assertEquals(account.getEmailVerified(), TRUE);
   }

   @Test
   public void verifyEmailUsingAccountNoChangeNecessary() {
       Account account = Account.create();
       account.setId(TEST_USER_ID);
       account.setStatus(ENABLED);
       account.setEmailVerified(TRUE);

       service.verifyChannel(ChannelType.EMAIL, account);
       
       verify(accountDao, never()).updateAccount(any());
   }

   @Test
   public void verifyEmailWithDisabledAccountMakesNoChanges() {
       Account account = Account.create();
       account.setStatus(DISABLED);

       service.verifyChannel(ChannelType.EMAIL, account);
       
       verify(accountDao, never()).updateAccount(any());
       assertEquals(account.getStatus(), DISABLED);
   }

   @Test
   public void verifyPhoneUsingToken() {
       Account account = Account.create();
       account.setAppId(TEST_APP_ID);
       account.setId(TEST_USER_ID);
       account.setPhone(PHONE);
       account.setStatus(UNVERIFIED);
       account.setPhoneVerified(FALSE);

       service.verifyChannel(ChannelType.PHONE, account);

       verify(accountDao).updateAccount(account);
       assertEquals(account.getStatus(), ENABLED);
       assertEquals(account.getPhoneVerified(), TRUE);
       // modifiedOn is stored as a long, which loses the time zone of the original time stamp.
       assertEquals(account.getModifiedOn(), MODIFIED_ON);
       assertEquals(account.getStatus(), ENABLED);
       assertEquals(account.getPhoneVerified(), TRUE);
       verify(accountDao).updateAccount(account);
   }

   @Test
   public void verifyPhoneUsingAccountNoChangeNecessary() {
       Account account = Account.create();
       account.setId(TEST_USER_ID);
       account.setStatus(ENABLED);
       account.setPhoneVerified(TRUE);

       service.verifyChannel(ChannelType.PHONE, account);
       verify(accountDao, never()).updateAccount(any());
   }

   @Test
   public void verifyPhoneWithDisabledAccountMakesNoChanges() {
       Account account = Account.create();
       account.setStatus(DISABLED);

       service.verifyChannel(ChannelType.PHONE, account);
       verify(accountDao, never()).updateAccount(any());
       assertEquals(account.getStatus(), DISABLED);
   }

   private Account mockGetAccountById(AccountId accountId, boolean generatePasswordHash)
           throws InvalidKeyException, InvalidKeySpecException, NoSuchAlgorithmException {
       Account account = Account.create();
       account.setAppId(TEST_APP_ID);
       account.setId(TEST_USER_ID);
       account.setEmail(EMAIL);
       account.setEmailVerified(TRUE);
       account.setHealthCode(HEALTH_CODE);
       account.setVersion(1);
       if (generatePasswordHash) {
           // Password hashes are expensive to generate. Only generate them if the test actually needs them.
           account.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
           account.setPasswordHash(DEFAULT_PASSWORD_ALGORITHM.generateHash(PASSWORD));
       }
       when(accountDao.getAccount(accountId)).thenReturn(Optional.of(account));
       return account;
   }

   @Test
   public void authenticate() throws Exception {
       App app = App.create();
       Account account = mockGetAccountById(ACCOUNT_ID, false);
       when(accountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(account));
       doNothing().when(service).verifyPassword(any(), any());

       Account returnVal = service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       assertEquals(returnVal, account);
       verify(accountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
   }

   @Test
   public void reauthenticate() throws Exception {
       App app = App.create();
       app.setReauthenticationEnabled(true);
       
       Account account = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);
       when(accountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
               .thenReturn(Optional.of(mockSecret));

       Account returnVal = service.reauthenticate(app, REAUTH_SIGN_IN);
       assertEquals(returnVal, account);
       verify(accountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
   }

   @Test
   public void authenticateSuccessWithHealthCode() throws Exception {
       mockGetAccountById(EMAIL_PASSWORD_SIGN_IN.getAccountId(), true);

       App app = App.create();

       Account account = service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       assertEquals(account.getId(), TEST_USER_ID);
       assertEquals(account.getAppId(), TEST_APP_ID);
       assertEquals(account.getEmail(), EMAIL);
       assertEquals(account.getHealthCode(), HEALTH_CODE);
       assertEquals(account.getVersion(), 1); // version not incremented by update
   }

   // This test is just a negative test to verify that the reauth token is not being rotated...
   // regardless of how app.reauthenticationEnabled is set, it will succeed because we don't
   // touch the reauth token
   @Test
   public void authenticateSuccessNoReauthentication() throws Exception {
       App app = App.create();
       app.setReauthenticationEnabled(false);

       mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);

       Account account = service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       // not incremented by reauthentication
       assertEquals(account.getVersion(), 1);

       // No reauthentication token rotation occurs
       verify(accountDao, never()).updateAccount(any());
       assertNull(account.getReauthToken());
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void authenticateAccountNotFound() throws Exception {
       when(accountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.empty());

       App app = App.create();

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }

   @Test(expectedExceptions = UnauthorizedException.class)
   public void authenticateAccountUnverified() throws Exception {
       // mock hibernate
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
       persistedAccount.setEmailVerified(false);

       App app = App.create();
       app.setEmailVerificationEnabled(true);

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }
   
   @Test
   public void authenticateAccountUnverifiedNoEmailVerification() throws Exception {
       // mock hibernate
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
       persistedAccount.setEmailVerified(false);

       App app = App.create();
       app.setEmailVerificationEnabled(false);

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }

   @Test(expectedExceptions = AccountDisabledException.class)
   public void authenticateAccountDisabled() throws Exception {
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
       persistedAccount.setStatus(DISABLED);

       App app = App.create();

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void authenticateAccountHasNoPassword() throws Exception {
       mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);

       App app = App.create();

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }

   // branch coverage
   @Test(expectedExceptions = EntityNotFoundException.class)
   public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);
       persistedAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);

       App app = App.create();

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void authenticateBadPassword() throws Exception {
       mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);

       App app = App.create();

       service.authenticate(app, new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
               .withPassword("wrong password").build());
   }

   @Test
   public void reauthenticateSuccess() throws Exception {
       mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, false);

       AccountSecret secret = AccountSecret.create();
       when(accountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
               .thenReturn(Optional.of(secret));

       App app = App.create();
       app.setReauthenticationEnabled(true);

       Account account = service.reauthenticate(app, REAUTH_SIGN_IN);
       assertEquals(account.getId(), TEST_USER_ID);
       assertEquals(account.getAppId(), TEST_APP_ID);
       assertEquals(account.getEmail(), EMAIL);
       // Version has not been incremented by an update
       assertEquals(account.getVersion(), 1);

       verify(accountDao).getAccount(ACCOUNT_ID_WITH_EMAIL);
       verify(accountDao, never()).createAccount(any(), any());
       verify(accountDao, never()).updateAccount(any());

       // verify token verification
       verify(accountSecretDao).verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, 3);
   }

   @Test
   public void reauthenticationDisabled() throws Exception {
       App app = App.create();
       app.setReauthenticationEnabled(false);

       try {
           service.reauthenticate(app, REAUTH_SIGN_IN);
           fail("Should have thrown exception");
       } catch (UnauthorizedException e) {
           // expected exception
       }
       verify(accountDao, never()).getAccount(any());
       verify(accountDao, never()).updateAccount(any());
   }

   // branch coverage
   @Test
   public void reauthenticationFlagNull() {
       App app = App.create();
       app.setReauthenticationEnabled(null);

       try {
           service.reauthenticate(app, REAUTH_SIGN_IN);
           fail("Should have thrown exception");
       } catch (UnauthorizedException e) {
           // expected exception
       }
       verify(accountDao, never()).getAccount(any());
       verify(accountDao, never()).updateAccount(any());
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void reauthenticateAccountNotFound() throws Exception {
       when(accountDao.getAccount(REAUTH_SIGN_IN.getAccountId())).thenReturn(Optional.empty());

       App app = App.create();
       app.setReauthenticationEnabled(true);

       service.reauthenticate(app, REAUTH_SIGN_IN);
   }

   @Test(expectedExceptions = UnauthorizedException.class)
   public void reauthenticateAccountUnverified() throws Exception {
       Account persistedAccount = mockGetAccountById(REAUTH_SIGN_IN.getAccountId(), false);
       persistedAccount.setEmailVerified(false);

       AccountSecret secret = AccountSecret.create();
       when(accountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
               .thenReturn(Optional.of(secret));

       App app = App.create();
       app.setReauthenticationEnabled(true);
       app.setEmailVerificationEnabled(true);

       service.reauthenticate(app, REAUTH_SIGN_IN);
   }
   
   @Test
   public void reauthenticateAccountUnverifiedNoEmailVerification() throws Exception {
       Account persistedAccount = mockGetAccountById(REAUTH_SIGN_IN.getAccountId(), false);
       persistedAccount.setEmailVerified(false);

       AccountSecret secret = AccountSecret.create();
       when(accountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
               .thenReturn(Optional.of(secret));

       App app = App.create();
       app.setReauthenticationEnabled(true);
       app.setEmailVerificationEnabled(false);

       service.reauthenticate(app, REAUTH_SIGN_IN);
   }

   @Test(expectedExceptions = AccountDisabledException.class)
   public void reauthenticateAccountDisabled() throws Exception {
       Account persistedAccount = mockGetAccountById(REAUTH_SIGN_IN.getAccountId(), false);
       persistedAccount.setStatus(DISABLED);

       AccountSecret secret = AccountSecret.create();
       when(accountSecretDao.verifySecret(REAUTH, TEST_USER_ID, REAUTH_TOKEN, ROTATIONS))
               .thenReturn(Optional.of(secret));

       App app = App.create();
       app.setReauthenticationEnabled(true);

       service.reauthenticate(app, REAUTH_SIGN_IN);
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void reauthenticateAccountHasNoReauthToken() throws Exception {
       Account persistedAccount = mockGetAccountById(REAUTH_SIGN_IN.getAccountId(), false);
       persistedAccount.setStatus(DISABLED);

       // it has no record in the secrets table

       App app = App.create();
       app.setReauthenticationEnabled(true);

       service.reauthenticate(app, REAUTH_SIGN_IN);
   }

   // This throws ENFE if password fails, so this just verifies a negative case (account status
   // doesn't change outcome of test)
   @Test(expectedExceptions = EntityNotFoundException.class)
   public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID, false);
       persistedAccount.setStatus(DISABLED);

       App app = App.create();

       SignIn signIn = new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
               .withPassword("bad password").build();
       service.authenticate(app, signIn);
   }

   @Test(expectedExceptions = EntityNotFoundException.class)
   public void reauthenticateBadReauthToken() throws Exception {
       mockGetAccountById(ACCOUNT_ID, false);

       App app = App.create();
       app.setReauthenticationEnabled(true);

       service.reauthenticate(app, new SignIn.Builder().withAppId(TEST_APP_ID).withEmail(EMAIL)
               .withReauthToken("wrong reauth token").build());
   }

   @Test(expectedExceptions = UnauthorizedException.class)
   public void authenticateAccountUnverifiedEmailFails() throws Exception {
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_EMAIL, true);
       persistedAccount.setEmailVerified(false);

       App app = App.create();
       app.setVerifyChannelOnSignInEnabled(true);
       app.setEmailVerificationEnabled(true);

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }

   @Test(expectedExceptions = UnauthorizedException.class)
   public void authenticateAccountUnverifiedPhoneFails() throws Exception {
       // mock hibernate
       Account persistedAccount = mockGetAccountById(ACCOUNT_ID_WITH_PHONE, true);
       persistedAccount.setPhoneVerified(null);

       App app = App.create();
       app.setVerifyChannelOnSignInEnabled(true);

       // execute and verify - Verify just ID, app, and email, and health code mapping is enough.
       SignIn phoneSignIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
               .withPassword(PASSWORD).build();
       service.authenticate(app, phoneSignIn);
   }
   
   @Test
   public void authenticateAccountEmailUnverifiedWithoutEmailVerificationOK() throws Exception {
       // mock hibernate 
       Account persistedAccount = mockGetAccountById(EMAIL_PASSWORD_SIGN_IN.getAccountId(), true); 
       persistedAccount.setEmailVerified(false);

       App app = App.create();
       app.setEmailVerificationEnabled(false);

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN); 
   }

   @Test
   public void authenticateAccountUnverifiedEmailSucceedsForLegacy() throws Exception {
       // mock hibernate 
       Account persistedAccount = mockGetAccountById(EMAIL_PASSWORD_SIGN_IN.getAccountId(), true); 
       persistedAccount.setEmailVerified(false);

       App app = App.create();
       app.setVerifyChannelOnSignInEnabled(false);

       service.authenticate(app, EMAIL_PASSWORD_SIGN_IN);
   }
   
   @Test
   public void authenticateAccountUnverifiedPhoneSucceedsForLegacy() throws Exception {
       SignIn phoneSignIn = new SignIn.Builder().withAppId(TEST_APP_ID).withPhone(PHONE)
               .withPassword(PASSWORD).build();

       // mock hibernate
       Account persistedAccount = mockGetAccountById(phoneSignIn.getAccountId(), true);
       persistedAccount.setPhoneVerified(null);
       
       App app = App.create();
       app.setVerifyChannelOnSignInEnabled(false);

       // execute and verify - Verify just ID, app, and email, and health code mapping is enough. 
       service.authenticate(app, phoneSignIn);
   }
   
   @Test
   public void resetPasswordWithEmail() {
       when(cacheProvider.getObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(EMAIL);
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.of(mockAccount));
       when(passwordResetValidator.supports(any())).thenReturn(true);

       PasswordReset passwordReset = new PasswordReset("newPassword", TOKEN, TEST_APP_ID);
       service.resetPassword(passwordReset);
       
       verify(cacheProvider).getObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL, String.class);
       verify(cacheProvider).removeObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL);
       verify(service).changePassword(mockAccount, ChannelType.EMAIL, "newPassword");
   }
   
   @Test
   public void resetPasswordWithPhone() {
       when(cacheProvider.getObject(CACHE_KEY_PASSWORD_RESET_FOR_PHONE, Phone.class)).thenReturn(TestConstants.PHONE);
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(Optional.of(mockAccount));
       when(passwordResetValidator.supports(any())).thenReturn(true);

       PasswordReset passwordReset = new PasswordReset("newPassword", TOKEN, TEST_APP_ID);
       service.resetPassword(passwordReset);
       
       verify(cacheProvider).getObject(CACHE_KEY_PASSWORD_RESET_FOR_PHONE, Phone.class);
       verify(cacheProvider).removeObject(CACHE_KEY_PASSWORD_RESET_FOR_PHONE);
       verify(service).changePassword(mockAccount, ChannelType.PHONE, "newPassword");
   }
   
   @Test
   public void resetPasswordInvalidSptokenThrowsException() {
       when(cacheProvider.getObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(null);
       when(passwordResetValidator.supports(any())).thenReturn(true);
       
       PasswordReset passwordReset = new PasswordReset("newPassword", TOKEN, TEST_APP_ID);
       try {
           service.resetPassword(passwordReset);
           fail("Should have thrown exception");
       } catch(BadRequestException e) {
           assertEquals(e.getMessage(), "Password reset token has expired (or already been used).");
       }
       verify(cacheProvider).getObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL, String.class);
       verify(cacheProvider, never()).removeObject(any());
       verify(service, never()).changePassword(any(), any(ChannelType.class), any());
   }
   
   @Test
   public void resetPasswordInvalidAccount() {
       when(cacheProvider.getObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(EMAIL);
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(Optional.empty());
       when(passwordResetValidator.supports(any())).thenReturn(true);

       PasswordReset passwordReset = new PasswordReset("newPassword", TOKEN, TEST_APP_ID);
       
       try {
           service.resetPassword(passwordReset);
           fail("Should have thrown an exception");
       } catch(EntityNotFoundException e) {
           // expected exception
       }
       verify(cacheProvider).getObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL, String.class);
       verify(cacheProvider).removeObject(CACHE_KEY_PASSWORD_RESET_FOR_EMAIL);
       verify(service, never()).changePassword(any(), any(ChannelType.class), any());
   }
   
   @Test(expectedExceptions = EntityNotFoundException.class)
   public void signOutUserWhoDoesNotExist() {
       service.signUserOut(app, TEST_USER_ID, true);
   }

   @Test
   public void signOutUser() {
       // Need to look this up by email, not account ID
       AccountId accountId = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
       
       // Setup
       when(accountDao.getAccount(accountId)).thenReturn(Optional.of(account));
       account.setId(TEST_USER_ID);

       // Execute
       service.signUserOut(app, TEST_USER_ID, false);

       // Verify
       verify(accountDao).getAccount(accountId);
       verify(service, never()).deleteReauthToken(any());
       verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
   }

   @Test
   public void signOutUserDeleteReauthToken() {
       // Need to look this up by email, not account ID
       AccountId accountId = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
       
       // Setup
       when(accountDao.getAccount(accountId)).thenReturn(Optional.of(account));
       account.setId(TEST_USER_ID);

       // Execute
       service.signUserOut(app, TEST_USER_ID, true);

       // Verify
       verify(accountDao).getAccount(accountId);
       verify(service).deleteReauthToken(TEST_USER_ID);
       verify(cacheProvider).removeSessionByUserId(TEST_USER_ID);
   }
   
   @Test
   public void verifyEmail() {
       when(service.getDateTime()).thenReturn(TIMESTAMP);
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
           createJson("{'appId':'"+TEST_APP_ID+"','type':'email','userId':'userId',"+
                   "'expiresOn':"+ TIMESTAMP.getMillis()+"}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(Optional.of(mockAccount));
       when(mockAccount.getId()).thenReturn("accountId");
       
       Verification verification = new Verification(TOKEN);
       
       service.verifyChannel(ChannelType.EMAIL, verification);
       
       verify(cacheProvider).getObject(CACHE_KEY_SPTOKEN, String.class);
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyWithoutCreatedFailsCorrectly() {
       // This is a dumb test, but prior to the introduction of the expiresOn value, the verification 
       // object's TTL is the timeout value for the link working... the cache returns null and 
       // the correct error is thrown.
       service.verifyChannel(ChannelType.EMAIL, new Verification(TOKEN));
   }
   
   // This almost seems logically impossible, but maybe if an admin deleted an account
   // and an email was hanging out there...
   @Test(expectedExceptions = EntityNotFoundException.class, 
           expectedExceptionsMessageRegExp = ".*Account not found.*")
   public void verifyNoAccount() {
       when(service.getDateTime()).thenReturn(TIMESTAMP);
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               createJson("{'appId':'" + TEST_APP_ID + "','type':'email','userId':'userId','expiresOn':"
                       + TIMESTAMP.getMillis() + "}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.EMAIL, verification);
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyWithMismatchedChannel() {
       when(service.getDateTime()).thenReturn(TIMESTAMP);
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               createJson("{'appId':'" + TEST_APP_ID + "','type':'email','userId':'userId','expiresOn':"
                       + TIMESTAMP.getMillis() + "}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       
       Verification verification = new Verification(TOKEN);
       // Should be email but was called through the phone API
       service.verifyChannel(ChannelType.PHONE, verification);
   }    
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyEmailExpired() {
       when(service.getDateTime()).thenReturn(TIMESTAMP.plusSeconds(1));
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               createJson("{'appId':'" + TEST_APP_ID + "','type':'email','userId':'userId','expiresOn':"
                       + TIMESTAMP.getMillis() + "}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(Optional.of(mockAccount));
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.EMAIL, verification);
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=".*That email address has already been verified.*")
   public void verifyEmailAlreadyVerified() {
       when(service.getDateTime()).thenReturn(TIMESTAMP.plusSeconds(1));
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
           createJson("{'appId':'"+TEST_APP_ID+"','type':'email','userId':'userId','expiresOn':"+
                   TIMESTAMP.getMillis()+"}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(Optional.of(mockAccount));
       when(mockAccount.getId()).thenReturn("accountId");
       when(mockAccount.getEmailVerified()).thenReturn(TRUE);
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.EMAIL, verification);        
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyEmailBadSptokenThrowsException() {
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(null);
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.EMAIL, verification);
   }
   
   @Test
   public void verifyPhone() {
       when(service.getDateTime()).thenReturn(TIMESTAMP);
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'phone','userId':'userId','expiresOn':"+
                       TIMESTAMP.getMillis()+"}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(Optional.of(mockAccount));
       when(mockAccount.getId()).thenReturn("accountId");
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.PHONE, verification);
       
       verify(cacheProvider).getObject(CACHE_KEY_SPTOKEN, String.class);
   }
   
   @Test(expectedExceptions = BadRequestException.class,
           expectedExceptionsMessageRegExp=".*That phone number has already been verified.*")
   public void verifyPhoneAlreadyVerified() {
       when(service.getDateTime()).thenReturn(TIMESTAMP);
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'phone','userId':'userId','expiresOn':"+
                       TIMESTAMP.getMillis()+"}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(Optional.of(mockAccount));
       when(mockAccount.getId()).thenReturn("accountId");
       when(mockAccount.getPhoneVerified()).thenReturn(TRUE);
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.PHONE, verification);
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyPhoneExpired() {
       when(service.getDateTime()).thenReturn(TIMESTAMP.plusSeconds(1));
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'phone','userId':'userId','expiresOn':"+
                       TIMESTAMP.getMillis()+"}"));
       when(appService.getApp(TEST_APP_ID)).thenReturn(app);
       when(accountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(Optional.of(mockAccount));
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.PHONE, verification);
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyEmailViaPhoneFails() {
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'email','userId':'userId'}"));
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.PHONE, verification);
       
       verifyNoMoreInteractions(cacheProvider);
   }
   
   @Test(expectedExceptions = BadRequestException.class, 
           expectedExceptionsMessageRegExp=AuthenticationService.VERIFY_TOKEN_EXPIRED)
   public void verifyPhoneViaEmailFails() {
       when(cacheProvider.getObject(CACHE_KEY_SPTOKEN, String.class)).thenReturn(
               TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'phone','userId':'userId'}"));
       
       Verification verification = new Verification(TOKEN);
       service.verifyChannel(ChannelType.EMAIL, verification);
       
       verifyNoMoreInteractions(cacheProvider);
   }
   
   @Test
   public void updateIdentifiersEmailSignInUpdatePhone() {
       // Verifies email-based sign in, phone update, account update, and an updated 
       // participant is returned... the common happy path.
       mockHealthCodeAndAccountRetrieval();
       doReturn(account).when(service).authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       when(participantService.getParticipant(app, TEST_USER_ID, false)).thenReturn(PARTICIPANT);       
       
       IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, PHONE, null);
       
       StudyParticipant returned = service.updateIdentifiers(app, CONTEXT, update);
       
       assertEquals(account.getPhone(), TestConstants.PHONE);
       assertEquals(account.getPhoneVerified(), Boolean.FALSE);
       verify(service).authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       verify(accountService).updateAccount(account);
       verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
       assertEquals(returned.getId(), PARTICIPANT.getId());
   }

   @Test
   public void updateIdentifiersPhoneSignInUpdateEmail() {
       // This flips the method of sign in to use a phone, and sends an email update. 
       // Also tests the common path of creating unverified email address with verification email sent
       mockAccountNoEmail();
       doReturn(account).when(service).authenticate(app, PHONE_PASSWORD_SIGN_IN);
       when(participantService.getParticipant(app, TEST_USER_ID, false)).thenReturn(PARTICIPANT);       
       
       app.setEmailVerificationEnabled(true);
       app.setAutoVerificationEmailSuppressed(false);
       
       IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
       
       StudyParticipant returned = service.updateIdentifiers(app, CONTEXT, update);
       
       assertEquals(account.getEmail(), "email@email.com");
       assertEquals(account.getEmailVerified(), Boolean.FALSE);
       verify(service).authenticate(app, PHONE_PASSWORD_SIGN_IN);
       verify(accountService).updateAccount(account);
       verify(accountWorkflowService).sendEmailVerificationToken(app, TEST_USER_ID, "email@email.com");
       assertSame(returned, PARTICIPANT);
   }
   
   @Test(expectedExceptions = InvalidEntityException.class)
   public void updateIdentifiersValidates() {
       IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, null, null);
       service.updateIdentifiers(app, CONTEXT, update);
   }
   
   @Test(expectedExceptions = InvalidEntityException.class)
   public void updateIdentifiersValidatesWithBlankEmail() {
       IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, "", null, null);
       service.updateIdentifiers(app, CONTEXT, update);
   }
   
   @Test(expectedExceptions = InvalidEntityException.class)
   public void updateIdentifiersValidatesWithInvalidPhone() {
       IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("US", "1231231234"), null);
       service.updateIdentifiers(app, CONTEXT, update);
   }
   
   @Test
   public void updateIdentifiersUsingReauthentication() {
       mockHealthCodeAndAccountRetrieval();
       doReturn(account).when(service).reauthenticate(app, REAUTH_SIGN_IN);
       
       IdentifierUpdate update = new IdentifierUpdate(REAUTH_SIGN_IN, null, TestConstants.PHONE, null);
       
       service.updateIdentifiers(app, CONTEXT, update);
       
       verify(service).reauthenticate(app, REAUTH_SIGN_IN);
   }

   @Test
   public void updateIdentifiersCreatesVerifiedEmailWithoutVerification() {
       mockAccountNoEmail();
       doReturn(account).when(service).authenticate(app, PHONE_PASSWORD_SIGN_IN);

       app.setEmailVerificationEnabled(false);
       app.setAutoVerificationEmailSuppressed(false); // can be true or false, doesn't matter
       
       IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);

       service.updateIdentifiers(app, CONTEXT, update);
       
       assertEquals(account.getEmail(), "email@email.com");
       assertEquals(account.getEmailVerified(), Boolean.TRUE);
       verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
   }
   
   @Test
   public void updateIdentifiersCreatesUnverifiedEmailWithoutVerification() {
       mockAccountNoEmail();
       doReturn(account).when(service).authenticate(app, PHONE_PASSWORD_SIGN_IN);
       
       app.setEmailVerificationEnabled(true);
       app.setAutoVerificationEmailSuppressed(true);
       
       IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, EMAIL, null, null);
       
       service.updateIdentifiers(app, CONTEXT, update);
       
       assertEquals(account.getEmail(), EMAIL);
       assertEquals(account.getEmailVerified(), Boolean.FALSE);
       verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
   }
   
   @Test
   public void updateIdentifiersAddsSynapseUserId() {
       account.setId(TEST_USER_ID);
       doReturn(account).when(service).authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       
       IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, EMAIL, null, SYNAPSE_USER_ID);
       service.updateIdentifiers(app, CONTEXT, update);
       
       assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
   }

   @Test
   public void updateIdentifiersAuthenticatingToAnotherAccountInvalid() {
       // This ID does not match the ID in the request's context, and that will fail
       account.setId("another-user-id");
       doReturn(account).when(service).authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       
       IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "email@email.com", null, null);
       
       try {
           service.updateIdentifiers(app, CONTEXT, update);
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
       doReturn(account).when(service).authenticate(app, PHONE_PASSWORD_SIGN_IN);
       
       // Now that an external ID addition will simply add another external ID, the 
       // test has been changed to submit an existing external ID.
       IdentifierUpdate update = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN, "updated@email.com",
               new Phone("4082588569", "US"), "88888");
       
       service.updateIdentifiers(app, CONTEXT, update);
       
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
       doReturn(account).when(service).authenticate(app, EMAIL_PASSWORD_SIGN_IN);
       
       // Add phone
       IdentifierUpdate update = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN, null, new Phone("4082588569", "US"),
               null);
       service.updateIdentifiers(app, CONTEXT, update);
       
       // externalIdService not called
       verify(accountService).updateAccount(any());
       verify(accountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
   }
   
   private void mockHealthCodeAndAccountRetrieval() {
       mockHealthCodeAndAccountRetrieval(EMAIL, null, null);
   }
   
   private void mockHealthCodeAndAccountRetrieval(String email, Phone phone, String externalId) {
       account.setId(TEST_USER_ID);
       account.setHealthCode(HEALTH_CODE);
       account.setEmail(email);
       account.setEmailVerified(TRUE);
       account.setPhone(phone);
       Set<Enrollment> enrollments = new HashSet<>();
       if (externalId != null) {
           Enrollment enrollment = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, externalId);
           enrollments.add(enrollment);
       }
       account.setEnrollments(enrollments);
       account.setAppId(TEST_APP_ID);
       when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
       when(studyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, false)).thenReturn(Study.create());
   }
   
   private void mockAccountNoEmail() {
       account.setId(TEST_USER_ID);
       account.setHealthCode(HEALTH_CODE);
       when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
   }
}