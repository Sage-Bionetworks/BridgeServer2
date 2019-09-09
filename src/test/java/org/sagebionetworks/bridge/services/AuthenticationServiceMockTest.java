package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.BridgeUtils;
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
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.sagebionetworks.bridge.validators.ValidatorUtils;
import org.springframework.validation.Errors;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class AuthenticationServiceMockTest {
    private static final Set<String> DATA_GROUP_SET = ImmutableSet.of("group1", "group2");
    private static final String IP_ADDRESS = "ip-address";
    private static final List<String> LANGUAGES = ImmutableList.of("es","de");
    private static final String SESSION_TOKEN = "SESSION_TOKEN";
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String TOKEN_UNFORMATTED = "ABCDEF";
    private static final String REAUTH_TOKEN = "GHI-JKL";
    private static final String USER_ID = "user-id";
    private static final String PASSWORD = "Password~!1";
    private static final SignIn SIGN_IN_REQUEST_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID)
            .withEmail(RECIPIENT_EMAIL).build();
    private static final SignIn SIGN_IN_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withToken(TOKEN).build();
    private static final SignIn SIGN_IN_WITH_PHONE = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).withToken(TOKEN).build();

    private static final SignIn EMAIL_PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).withPassword(PASSWORD).build();
    private static final SignIn REAUTH_REQUEST = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withReauthToken(TOKEN).build();

    private static final CacheKey CACHE_KEY_EMAIL_SIGNIN = CacheKey.emailSignInRequest(SIGN_IN_WITH_EMAIL);
    private static final CacheKey CACHE_KEY_PHONE_SIGNIN = CacheKey.phoneSignInRequest(SIGN_IN_WITH_PHONE);
    private static final CacheKey CACHE_KEY_SIGNIN_TO_SESSION = CacheKey.channelSignInToSessionToken(
            TOKEN_UNFORMATTED);

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
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withId(USER_ID).build();
    private static final AccountId ACCOUNT_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, USER_ID);
    private static final String EXTERNAL_ID = "ext-id";
    private static final String HEALTH_CODE = "health-code";

    private static final StudyParticipant PARTICIPANT_WITH_ATTRIBUTES = new StudyParticipant.Builder().withId(USER_ID)
            .withHealthCode(HEALTH_CODE).withDataGroups(DATA_GROUP_SET).withSubstudyIds(TestConstants.USER_SUBSTUDY_IDS)
            .withLanguages(LANGUAGES).build();

    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private BridgeConfig config;
    @Mock
    private ConsentService consentService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private ParticipantService participantService;
    @Mock
    private StudyService studyService;
    @Mock
    private PasswordResetValidator passwordResetValidator;
    @Mock
    private AccountWorkflowService accountWorkflowService;
    @Mock
    private ExternalIdService externalIdService;
    @Mock
    private IntentService intentService;
    @Mock
    private AccountSecretDao accountSecretDao;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    @Captor
    private ArgumentCaptor<AccountId> accountIdCaptor;
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    @Spy
    private AuthenticationService service;

    private Study study;

    private Account account;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        // Create inputs.
        study = Study.create();
        study.setIdentifier(STUDY_ID);
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setName("Sender");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        account = Account.create();
        account.setId(USER_ID);

        // Wire up service.
        service.setCacheProvider(cacheProvider);
        service.setBridgeConfig(config);
        service.setConsentService(consentService);
        service.setAccountDao(accountDao);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setParticipantService(participantService);
        service.setStudyService(studyService);
        service.setAccountWorkflowService(accountWorkflowService);
        service.setExternalIdService(externalIdService);
        service.setIntentToParticipateService(intentService);
        service.setAccountSecretDao(accountSecretDao);

        doReturn(SESSION_TOKEN).when(service).getGuid();
        doReturn(study).when(studyService).getStudy(STUDY_ID);
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    void setIpAddress(String ipAddress) {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerIpAddress(ipAddress).build());
    }
    
    @Test // Test some happy path stuff, like the correct initialization of the user session
    public void signIn() {
        study.setReauthenticationEnabled(true);
        
        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", USER_ID);
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", USER_ID);
        
        account.setReauthToken("REAUTH_TOKEN");
        account.setHealthCode(HEALTH_CODE);
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));
        account.setId(USER_ID);
        
        setIpAddress(IP_ADDRESS);
        
        CriteriaContext context = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY)
            .withLanguages(LANGUAGES)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/13")).build();
        
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(contextCaptor.capture(), any());
        doReturn(REAUTH_TOKEN).when(service).generateReauthToken();
        doReturn(Environment.PROD).when(config).getEnvironment();
        
        UserSession session = service.signIn(study, context, EMAIL_PASSWORD_SIGN_IN);
        
        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(USER_ID);
        inOrder.verify(cacheProvider).setUserSession(session);
        
        assertEquals(session.getConsentStatuses(), CONSENTED_STATUS_MAP);
        assertTrue(session.isAuthenticated());
        assertEquals(session.getIpAddress(), IP_ADDRESS);
        assertEquals(session.getSessionToken(), SESSION_TOKEN);
        assertEquals(session.getInternalSessionToken(), SESSION_TOKEN);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        assertEquals(session.getEnvironment(), Environment.PROD);
        assertEquals(session.getStudyIdentifier(), TestConstants.TEST_STUDY);

        // updated context
        CriteriaContext updatedContext = contextCaptor.getValue();
        assertEquals(updatedContext.getHealthCode(), HEALTH_CODE);
        assertEquals(updatedContext.getLanguages(), LANGUAGES);
        assertEquals(updatedContext.getUserDataGroups(), DATA_GROUP_SET);
        assertEquals(updatedContext.getUserSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        assertEquals(updatedContext.getUserId(), USER_ID);
        
        verify(accountSecretDao).createSecret(AccountSecretType.REAUTH, USER_ID, REAUTH_TOKEN);
    }
    
    @Test
    public void signInWithAccountNotFound() throws Exception {
        study.setReauthenticationEnabled(true);
        when(accountDao.authenticate(study, EMAIL_PASSWORD_SIGN_IN))
                .thenThrow(new EntityNotFoundException(Account.class));
        try {
            service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        
        // Do not change anything about the session, don't rotate the reauth keys, etc.
        verifyNoMoreInteractions(cacheProvider);
        verifyNoMoreInteractions(accountSecretDao);
        verifyNoMoreInteractions(accountDao);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void signInWithBadCredentials() throws Exception {
        service.signIn(study, CONTEXT, new SignIn.Builder().build());
    }
    
    @Test
    public void signInThrowsConsentRequiredException() {
        study.setReauthenticationEnabled(true);
        
        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", USER_ID);
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", USER_ID);
        
        account.setReauthToken("REAUTH_TOKEN");
        account.setHealthCode(HEALTH_CODE);
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));
        account.setId(USER_ID);
        
        setIpAddress(IP_ADDRESS);
        
        CriteriaContext context = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY)
            .withLanguages(LANGUAGES)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/13")).build();
        
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(contextCaptor.capture(), any());
        doReturn(REAUTH_TOKEN).when(service).generateReauthToken();
        doReturn(Environment.PROD).when(config).getEnvironment();
        
        UserSession session = null;
        try {
            session = service.signIn(study, context, EMAIL_PASSWORD_SIGN_IN);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            session = e.getUserSession();
        }
        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(USER_ID);
        inOrder.verify(cacheProvider).setUserSession(session);
        
        assertEquals(session.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        assertTrue(session.isAuthenticated());
        assertEquals(session.getIpAddress(), IP_ADDRESS);
        assertEquals(session.getSessionToken(), SESSION_TOKEN);
        assertEquals(session.getInternalSessionToken(), SESSION_TOKEN);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        assertEquals(session.getEnvironment(), Environment.PROD);
        assertEquals(session.getStudyIdentifier(), TestConstants.TEST_STUDY);

        // updated context
        CriteriaContext updatedContext = contextCaptor.getValue();
        assertEquals(updatedContext.getHealthCode(), HEALTH_CODE);
        assertEquals(updatedContext.getLanguages(), LANGUAGES);
        assertEquals(updatedContext.getUserDataGroups(), DATA_GROUP_SET);
        assertEquals(updatedContext.getUserSubstudyIds(), TestConstants.USER_SUBSTUDY_IDS);
        assertEquals(updatedContext.getUserId(), USER_ID);
        
        verify(accountSecretDao).createSecret(AccountSecretType.REAUTH, USER_ID, REAUTH_TOKEN);
    }
    
    @Test
    public void signInWithEmail() {
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        verify(cacheProvider).removeSessionByUserId(USER_ID);
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void unconsentedSignInWithEmail() {
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithEmail() {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        // Does not throw consent required exception, despite being unconsented, because user has DEVELOPER role.
        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        assertEquals(retrieved.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
    }
    
    @Test
    public void signInWithPhone() {
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
        
        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        verify(cacheProvider).removeSessionByUserId(USER_ID);
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void unconsentedSignInWithPhone() {
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithPhone() {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        // Does not throw consent required exception, despite being unconsented, because user has RESEARCHER role. 
        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);

        assertEquals(retrieved.getReauthToken(), REAUTH_TOKEN);
        assertEquals(retrieved.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
    }
    
    @Test
    public void signOut() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(STUDY_ID);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(studyIdentifier);
        session.setReauthToken(TOKEN);
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").withId(USER_ID).build());
        service.signOut(session);
        
        verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        verify(cacheProvider).removeSession(session);
    }
    
    @Test
    public void signOutNoSessionToken() {
        service.signOut(null);
        
        verify(accountDao, never()).deleteReauthToken(any());
        verify(cacheProvider, never()).removeSession(any());
    }

    @Test
    public void emailSignIn() {
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);

        assertNotNull(retSession);
        assertEquals(retSession.getReauthToken(), REAUTH_TOKEN);

        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        inOrder.verify(accountDao).verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        inOrder.verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(USER_ID);
        inOrder.verify(cacheProvider).setUserSession(retSession);
        inOrder.verify(cacheProvider).setExpiration(CACHE_KEY_EMAIL_SIGNIN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
        inOrder.verify(cacheProvider).setObject(CACHE_KEY_SIGNIN_TO_SESSION, SESSION_TOKEN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
    }

    // branch coverage
    @Test
    public void emailSignIn_CachedTokenWithNoSession() {
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(cacheProvider.getObject(CACHE_KEY_SIGNIN_TO_SESSION, String.class)).thenReturn(SESSION_TOKEN);
        when(cacheProvider.getUserSession(SESSION_TOKEN)).thenReturn(null);

        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);

        assertNotNull(retSession);
        assertEquals(retSession.getReauthToken(), REAUTH_TOKEN);

        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        inOrder.verify(accountDao).verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        inOrder.verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(USER_ID);
        inOrder.verify(cacheProvider).setUserSession(retSession);
        inOrder.verify(cacheProvider).setExpiration(CACHE_KEY_EMAIL_SIGNIN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
        inOrder.verify(cacheProvider).setObject(CACHE_KEY_SIGNIN_TO_SESSION, SESSION_TOKEN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
    }

    @Test
    public void emailSignIn_CachedSession() {
        account.setId(USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(cacheProvider.getObject(CACHE_KEY_SIGNIN_TO_SESSION, String.class)).thenReturn(SESSION_TOKEN);

        UserSession cachedSession = new UserSession();
        cachedSession.setSessionToken(SESSION_TOKEN);
        cachedSession.setConsentStatuses(CONSENTED_STATUS_MAP);
        when(cacheProvider.getUserSession(SESSION_TOKEN)).thenReturn(cachedSession);

        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
        assertNotNull(retSession);

        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        inOrder.verify(accountDao).verifyChannel(AuthenticationService.ChannelType.EMAIL, account);

        // Because we got the cached session, we don't do certain operations.
        verify(accountDao, never()).deleteReauthToken(any());
        verify(cacheProvider, never()).removeSessionByUserId(any());
        verify(cacheProvider, never()).setUserSession(any());
        verify(cacheProvider, never()).setExpiration(any(), anyInt());
        verify(cacheProvider, never()).setObject(any(), any(), anyInt());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void emailSignInNoAccount() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(null);
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void emailSignIn_NoCachedToken() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(null);
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void emailSignIn_WrongToken() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn("badtoken");
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void emailSignIn_WrongEmail() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);

        SignIn wrongEmailSignIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail("wrong-email@email.com")
                .withToken(TOKEN).build();
        service.emailSignIn(CONTEXT, wrongEmailSignIn);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void emailSignInInvalidEntity() {
        service.emailSignIn(CONTEXT, SIGN_IN_REQUEST_WITH_EMAIL);
    }

    @Test(expectedExceptions = AccountDisabledException.class)
    public void emailSignInThrowsAccountDisabled() {
        account.setStatus(AccountStatus.DISABLED);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());

        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }

    @Test
    public void emailSignInThrowsConsentRequired() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withStatus(AccountStatus.DISABLED)
                .build();

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        try {
            service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());
            assertEquals(e.getUserSession().getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        }
    }

    @Test
    public void emailSignInAdminOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withRoles(Sets.newHashSet(Roles.ADMIN)).build();

        doReturn(participant).when(participantService).getParticipant(study, account, false);
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Does not throw a consent required exception because the participant is an admin.
        UserSession retrieved = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
        assertEquals(retrieved.getConsentStatuses(), UNCONSENTED_STATUS_MAP);
    }

    @Test
    public void reauthentication() {
        study.setReauthenticationEnabled(true);
        account.setId(USER_ID);
        
        doReturn(REAUTH_TOKEN).when(service).generateReauthToken();

        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withEmail(RECIPIENT_EMAIL).build();
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        UserSession session = service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
        assertEquals(session.getParticipant().getEmail(), RECIPIENT_EMAIL);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        
        verify(accountDao).reauthenticate(study, REAUTH_REQUEST);
        verify(cacheProvider).setUserSession(sessionCaptor.capture());
        
        UserSession captured = sessionCaptor.getValue();
        assertEquals(captured.getParticipant().getEmail(), RECIPIENT_EMAIL);
        assertEquals(captured.getReauthToken(), REAUTH_TOKEN);
        
        verify(accountSecretDao).createSecret(REAUTH, USER_ID, REAUTH_TOKEN);
    }
    
    @Test(expectedExceptions = ConsentRequiredException.class)
    public void reauthenticateThrowsConsentRequiredException() {
        study.setReauthenticationEnabled(true);

        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withEmail(RECIPIENT_EMAIL).build();
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
    }
    
    @Test
    public void reauthenticateIgnoresConsentForAdmins() {
        study.setReauthenticationEnabled(true);

        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withRoles(ImmutableSet.of(Roles.DEVELOPER)).withEmail(RECIPIENT_EMAIL).build();
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
    }
    
    @Test
    public void reauthenticationPersistsExistingSessionTokens() {
        study.setReauthenticationEnabled(true);
        
        UserSession existing = new UserSession();
        existing.setSessionToken("existingToken");
        existing.setInternalSessionToken("existingInternalToken");
        doReturn(existing).when(cacheProvider).getUserSessionByUserId(USER_ID);

        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withEmail(RECIPIENT_EMAIL).build();
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        UserSession session = service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
        assertEquals(session.getSessionToken(), "existingToken");
        assertEquals(session.getInternalSessionToken(), "existingInternalToken");
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void reauthTokenRequired() {
        service.reauthenticate(study, CONTEXT, SIGN_IN_WITH_EMAIL); // doesn't have reauth token
    }
    
    @Test
    public void reauthThrowsUnconsentedException() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withStatus(AccountStatus.ENABLED).build();
        
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        try {
            service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            assertEquals(e.getUserSession().getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        }
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void requestResetInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(TestConstants.PHONE)
                .withEmail(RECIPIENT_EMAIL).build();
        service.requestResetPassword(study, false, signIn);
    }
    
    @Test
    public void requestResetPassword() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL).build();
        
        service.requestResetPassword(study, false, signIn);
        
        verify(accountWorkflowService).requestResetPassword(study, false, signIn.getAccountId());
    }
    
    @Test
    public void signUpWithEmailOK() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).withPassword(PASSWORD)
                .build();
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getEmail(), RECIPIENT_EMAIL);
        assertEquals(captured.getPassword(), PASSWORD);
    }

    @Test
    public void signUpWithPhoneOK() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE)
                .withPassword(PASSWORD).build();
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(captured.getPhone().getNumber(), TestConstants.PHONE.getNumber());
        assertEquals(captured.getPassword(), PASSWORD);
    }
    
    @Test
    public void signUpExistingAccount() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).withPassword(PASSWORD)
                .build();
        doThrow(new EntityAlreadyExistsException(Account.class, "userId", "user-id")).when(participantService)
                .createParticipant(study, participant, true);
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(study), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals(captured.getId(), "user-id");
        assertEquals(captured.getStudyId(), TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void signUpExistingExternalId() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        doThrow(new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", EXTERNAL_ID)).when(participantService)
                .createParticipant(study, participant, true);
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(study), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals(captured.getExternalId(), EXTERNAL_ID);
        assertEquals(captured.getStudyId(), TestConstants.TEST_STUDY_IDENTIFIER);
    }
    
    @Test
    public void signUpExistingUnknownEntity() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        doThrow(new EntityAlreadyExistsException(AppConfig.class, "identifier", EXTERNAL_ID)).when(participantService)
                .createParticipant(study, participant, true);
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), any(), eq(true));
        
        // We don't send a message. That's the logic... it's debatable.
        verify(accountWorkflowService, never()).notifyAccountExists(any(), any());
    }

    @Test
    public void phoneSignIn() {
        account.setId(USER_ID);

        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder().withDataGroups(DATA_GROUP_SET)
                .withEmail(RECIPIENT_EMAIL).withHealthCode(HEALTH_CODE).withId(USER_ID).withLanguages(LANGUAGES)
                .withFirstName("Test").withLastName("Tester").withPhone(TestConstants.PHONE).build();
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_PHONE.getAccountId());
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate.
        UserSession session = service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);

        assertEquals(session.getParticipant().getEmail(), RECIPIENT_EMAIL);
        assertEquals(session.getParticipant().getFirstName(), "Test");
        assertEquals(session.getParticipant().getLastName(), "Tester");

        // this doesn't pass if our mock calls above aren't executed, but verify these:
        InOrder inOrder = Mockito.inOrder(cacheProvider, accountDao);
        inOrder.verify(accountDao).getAccount(SIGN_IN_WITH_PHONE.getAccountId());
        inOrder.verify(accountDao).verifyChannel(ChannelType.PHONE, account);
        inOrder.verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        inOrder.verify(cacheProvider).removeSessionByUserId(USER_ID);
        inOrder.verify(cacheProvider).setUserSession(session);
        inOrder.verify(cacheProvider).setExpiration(CACHE_KEY_PHONE_SIGNIN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
        inOrder.verify(cacheProvider).setObject(CACHE_KEY_SIGNIN_TO_SESSION, SESSION_TOKEN,
                AuthenticationService.SIGNIN_GRACE_PERIOD_SECONDS);
    }

    @Test
    public void phoneSignIn_TokenFormattedWithSpace() {
        account.setId(USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_PHONE.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate. Just verify that it succeeds and doesn't throw. Details are tested in above tests.
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(TestConstants.PHONE).withToken("ABC DEF")
                .build();
        service.phoneSignIn(CONTEXT, signIn);
    }

    @Test
    public void phoneSignIn_UnformattedToken() {
        account.setId(USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_PHONE.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate. Just verify that it succeeds and doesn't throw. Details are tested in above tests.
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(TestConstants.PHONE)
                .withToken(TOKEN_UNFORMATTED).build();
        service.phoneSignIn(CONTEXT, signIn);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void phoneSignInNoAccount() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(null);
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void phoneSignIn_NoCachedToken() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(null);
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void phoneSignIn_WrongToken() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn("badtoken");
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }

    @Test(expectedExceptions = AuthenticationFailedException.class)
    public void phoneSignIn_WrongPhone() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);

        SignIn wrongPhoneSignIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withPhone(new Phone("4082588569", "US")).withToken(TOKEN).build();
        service.phoneSignIn(CONTEXT, wrongPhoneSignIn);
    }

    @Test
    public void phoneSignInThrowsConsentRequired() {
        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withEmail(RECIPIENT_EMAIL).withFirstName("Test").withLastName("Tester").build();
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).getAccount(SIGN_IN_WITH_PHONE.getAccountId());

        try {
            service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());
            assertEquals(e.getUserSession().getConsentStatuses(), UNCONSENTED_STATUS_MAP);
        }
    }

    @Test
    public void verifyEmail() {
        Verification ev = new Verification("sptoken");
        doReturn(account).when(accountWorkflowService).verifyChannel(ChannelType.EMAIL, ev);
        
        service.verifyChannel(ChannelType.EMAIL, ev);
        
        verify(accountWorkflowService).verifyChannel(ChannelType.EMAIL, ev);
        verify(accountDao).verifyChannel(ChannelType.EMAIL, account);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void verifyEmailInvalid() {
        Verification ev = new Verification(null);
        service.verifyChannel(ChannelType.EMAIL, ev);
    }
    
    @Test
    public void verifyPhone() {
        Verification ev = new Verification("sptoken");
        doReturn(account).when(accountWorkflowService).verifyChannel(ChannelType.PHONE, ev);
        
        service.verifyChannel(ChannelType.PHONE, ev);
        
        verify(accountWorkflowService).verifyChannel(ChannelType.PHONE, ev);
        verify(accountDao).verifyChannel(ChannelType.PHONE, account);
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

        CriteriaContext context = new CriteriaContext.Builder().withLanguages(LANGUAGES).withUserId(USER_ID)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        TestUtils.mockEditAccount(accountDao, mockAccount);
        doReturn(mockAccount).when(accountDao).getAccount(any());
        
        // No languages.
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode("healthCode").build();
        doReturn(participant).when(participantService).getParticipant(study, mockAccount, false);
        
        service.getSession(study, context);
        
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq("healthCode"), any());
        verify(mockAccount).setLanguages(ImmutableList.copyOf(LANGUAGES));
    }

    @Test
    public void resendEmailVerification() {
        AccountId accountId = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, RECIPIENT_EMAIL);
        service.resendVerification(ChannelType.EMAIL, accountId);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.EMAIL), accountIdCaptor.capture());
        
        assertEquals(accountIdCaptor.getValue().getStudyId(), TestConstants.TEST_STUDY_IDENTIFIER);
        assertEquals(accountIdCaptor.getValue().getEmail(), RECIPIENT_EMAIL);
    }

    @Test
    public void resendEmailVerificationNoAccount() {
        AccountId accountId = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.EMAIL);
        
        // Does not throw an EntityNotFoundException to hide this information from API uses
        doThrow(new EntityNotFoundException(Account.class))
            .when(accountWorkflowService).resendVerificationToken(ChannelType.EMAIL, accountId);
        
        service.resendVerification(ChannelType.EMAIL, accountId);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void resendEmailVerificationInvalid() throws Exception {
        AccountId accountId = BridgeObjectMapper.get().readValue("{}", AccountId.class);
        service.resendVerification(ChannelType.EMAIL, accountId);
    }
    
    @Test
    public void resendPhoneVerification() {
        AccountId accountId = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
        service.resendVerification(ChannelType.PHONE, accountId);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.PHONE), accountIdCaptor.capture());
        
        assertEquals(accountIdCaptor.getValue().getStudyId(), TestConstants.TEST_STUDY_IDENTIFIER);
        assertEquals(accountIdCaptor.getValue().getPhone(), TestConstants.PHONE);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void resendPhoneVerificationInvalid() throws Exception {
        AccountId accountId = BridgeObjectMapper.get().readValue("{}", AccountId.class);
        service.resendVerification(ChannelType.PHONE, accountId);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void generatePasswordExternalIdNotSubmitted() {
        service.generatePassword(study, null, true);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordExternalIdRecordMissing() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.empty());
        service.generatePassword(study, EXTERNAL_ID, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordNoAccountDoNotCreateAccount() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        service.generatePassword(study, EXTERNAL_ID, false);
    }
    
    @Test
    public void generatePasswordAndAccountOK() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        doReturn(PASSWORD).when(service).generatePassword(anyInt());
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        IdentifierHolder idHolder = new IdentifierHolder("userId");
        when(participantService.createParticipant(eq(study), participantCaptor.capture(), eq(false))).thenReturn(idHolder);
        
        GeneratedPassword password = service.generatePassword(study, EXTERNAL_ID, true);
        assertEquals(password.getExternalId(), EXTERNAL_ID);
        assertEquals(password.getPassword(), PASSWORD);
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(participant.getExternalId(), EXTERNAL_ID);
        assertEquals(participant.getPassword(), PASSWORD);
    }
    
    @Test
    public void generatePasswordAndAccountWhenExternalIdTaken() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setHealthCode("someoneElsesHealthCode");
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        when(participantService.createParticipant(eq(study), participantCaptor.capture(), eq(false)))
                        .thenThrow(new EntityAlreadyExistsException(Account.class, "id", "asdf"));
        
        try {
            service.generatePassword(study, EXTERNAL_ID, true);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            // expected exception
        }
        verify(accountDao).getAccount(AccountId.forExternalId(TestConstants.TEST_STUDY_IDENTIFIER, EXTERNAL_ID));
        verify(participantService).createParticipant(eq(study), any(), eq(false));
        verifyNoMoreInteractions(accountDao);
        verifyNoMoreInteractions(participantService);
    }
    
    
    @Test
    public void generatePasswordAndAccountWhenExternalIdMissing() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setHealthCode("someoneElsesHealthCode");
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
            .thenReturn(Optional.empty());
        
        try {
            service.generatePassword(study, EXTERNAL_ID, true);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(accountDao, never()).getAccount(any());
        verify(participantService, never()).createParticipant(any(), any(), anyBoolean());
        verify(accountDao, never()).changePassword(any(), any(), any());
    }
    
    @Test
    public void generatePasswordOK() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        doReturn(PASSWORD).when(service).generatePassword(anyInt());
        
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setHealthCode(HEALTH_CODE);
        
        GeneratedPassword password = service.generatePassword(study, EXTERNAL_ID, true);
        assertEquals(password.getExternalId(), EXTERNAL_ID);
        assertEquals(password.getPassword(), PASSWORD);
        
        verify(accountDao).changePassword(account, null, PASSWORD);
    }
    
    @Test
    public void generatedPasswordPassesValidation() {
        // This is a very large password, which you could set in a study like this
        String password = service.generatePassword(100);

        Errors errors = Validate.getErrorsFor(password);
        ValidatorUtils.validatePassword(errors, PasswordPolicy.DEFAULT_PASSWORD_POLICY, password);
        assertFalse(errors.hasErrors());
        assertEquals(password.length(), 100);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordExternalIdMismatchesCallerSubstudies() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyB")).build());
        
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        account.setAccountSubstudies(ImmutableSet.of(AccountSubstudy.create(study.getIdentifier(), "substudyA", "id")));
        
        service.generatePassword(study, EXTERNAL_ID, false);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void generatePasswordAccountMismatchesCallerSubstudies() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setAccountSubstudies(ImmutableSet.of(AccountSubstudy.create(study.getIdentifier(), "substudyB", "id")));
        
        service.generatePassword(study, EXTERNAL_ID, false);
    }

    @Test
    public void creatingExternalIdOnlyAccountSucceedsIfIdsManaged() {
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).withPhone(null).withExternalId("id").build();
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(study, participant, true);
    }
    
    @Test
    public void signInWithIntentToParticipate() {
        account.setId(USER_ID);
        Account consentedAccount = Account.create();

        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, account,
                false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        doReturn(consentedAccount).when(accountDao).getAccount(any());
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, consentedAccount,
                false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(consentedAccount));
        
        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(study, account)).thenReturn(true);
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }

    @Test
    public void emailSignInWithIntentToParticipate() {
        Account consentedAccount = Account.create();
        consentedAccount.setId(USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(account, consentedAccount);
        when(participantService.getParticipant(study, account, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(UNCONSENTED_STATUS_MAP);

        when(participantService.getParticipant(study, consentedAccount, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(consentedAccount))).thenReturn(CONSENTED_STATUS_MAP);

        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(study, account)).thenReturn(true);

        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }

    @Test
    public void phoneSignInWithIntentToParticipate() {
        Account consentedAccount = Account.create();
        consentedAccount.setId(USER_ID);

        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(account, consentedAccount);
        when(participantService.getParticipant(study, account, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(UNCONSENTED_STATUS_MAP);

        when(participantService.getParticipant(study, consentedAccount, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(consentedAccount))).thenReturn(CONSENTED_STATUS_MAP);

        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(study, account)).thenReturn(true);

        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }

    @Test
    public void consentedSignInDoesNotExecuteIntentToParticipate() {
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));

        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);

        verify(intentService, never()).registerIntentToParticipate(study, account);
    }

    @Test
    public void consentedEmailSignInDoesNotExecuteIntentToParticipate() {
        when(cacheProvider.getObject(CACHE_KEY_EMAIL_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(PARTICIPANT);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(CONSENTED_STATUS_MAP);

        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);

        verify(intentService, never()).registerIntentToParticipate(study, account);
    }

    @Test
    public void consentedPhoneSignInDoesNotExecuteIntentToParticipate() {
        when(cacheProvider.getObject(CACHE_KEY_PHONE_SIGNIN, String.class)).thenReturn(TOKEN_UNFORMATTED);
        when(accountDao.getAccount(any())).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(PARTICIPANT);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(CONSENTED_STATUS_MAP);

        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);

        verify(intentService, never()).registerIntentToParticipate(study, account);
    }

    // Most of the other behaviors are tested in other methods. This test specifically tests the session created has
    // the correct attributes.
    @Test
    public void getSessionFromAccount() {
        // Create inputs.
        Study study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setReauthenticationEnabled(true);
        
        setIpAddress(IP_ADDRESS);

        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).build();

        Account account = Account.create();
        account.setId(USER_ID);

        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(PARTICIPANT);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        when(service.generateReauthToken()).thenReturn(REAUTH_TOKEN);
        
        // Execute and validate.
        UserSession session = service.getSessionFromAccount(study, context, account);
        assertSame(session.getParticipant(), PARTICIPANT);
        assertNotNull(session.getSessionToken());
        assertNotNull(session.getInternalSessionToken());
        assertTrue(session.isAuthenticated());
        assertEquals(session.getEnvironment(), Environment.LOCAL);
        assertEquals(session.getIpAddress(), IP_ADDRESS);
        assertEquals(session.getStudyIdentifier(), TestConstants.TEST_STUDY);
        assertEquals(session.getReauthToken(), REAUTH_TOKEN);
        assertEquals(session.getConsentStatuses(), CONSENTED_STATUS_MAP);
        
        verify(accountSecretDao).createSecret(AccountSecretType.REAUTH, USER_ID, REAUTH_TOKEN);
    }
    
    @Test
    public void getSessionFromAccountWithoutReauthentication() {
        // Create inputs.
        Study study = Study.create();
        study.setReauthenticationEnabled(false);

        setIpAddress(IP_ADDRESS);

        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).build();

        Account account = Account.create();

        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(PARTICIPANT);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        
        // Execute and validate.
        UserSession session = service.getSessionFromAccount(study, context, account);
        assertNull(session.getReauthToken());
        
        verify(service, never()).generateReauthToken();
        verify(accountSecretDao, never()).createSecret(any(), any(), any());
    }

    // branch coverage
    @Test
    public void getSessionFromAccountReauthenticationFlagNull() {
        // Create inputs.
        Study study = Study.create();
        study.setReauthenticationEnabled(null);

        setIpAddress(IP_ADDRESS);

        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY).build();

        Account account = Account.create();

        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(PARTICIPANT);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);

        // Execute and validate.
        UserSession session = service.getSessionFromAccount(study, context, account);
        assertNull(session.getReauthToken());

        verify(service, never()).generateReauthToken();
        verify(accountSecretDao, never()).createSecret(any(), any(), any());
    }

    @Test
    public void getSession() {
        service.getSession(TOKEN);
        verify(cacheProvider).getUserSession(TOKEN);
    }
    
    @Test
    public void getSessionNoToken() {
        assertNull( service.getSession(null) );
        verify(cacheProvider, never()).getUserSession(TOKEN);
    }
    
    @Test
    public void requestResetPasswordNoAccount() {
        // should not throw this EntityNotFoundException
        doThrow(new EntityNotFoundException(Account.class))
            .when(accountWorkflowService).requestResetPassword(any(), anyBoolean(), any());
        
        service.requestResetPassword(study, true, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void resetPassword() {
        PasswordResetValidator validator = new PasswordResetValidator();
        validator.setStudyService(studyService);
        service.setPasswordResetValidator(validator);
        
        PasswordReset reset = new PasswordReset(PASSWORD, TOKEN, TestConstants.TEST_STUDY_IDENTIFIER);
        service.resetPassword(reset);
        verify(accountWorkflowService).resetPassword(reset);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void resetPasswordInvalid() {
        PasswordResetValidator validator = new PasswordResetValidator();
        validator.setStudyService(studyService);
        service.setPasswordResetValidator(validator);
        
        PasswordReset reset = new PasswordReset(PASSWORD, null, TestConstants.TEST_STUDY_IDENTIFIER);
        service.resetPassword(reset);
    }
    
    @Test
    public void existingLanguagePreferencesAreLoaded() {
        // Language prefs in the user object and the criteria context are different; the values from the 
        // database are taken. These cannot be picked up from the HTTP request once they are set.
        account.setLanguages(TestConstants.LANGUAGES);
        when(accountDao.authenticate(study, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withId(USER_ID).withLanguages(TestConstants.LANGUAGES).build();
        when(participantService.getParticipant(study, account, false)).thenReturn(participant);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withContext(CONTEXT)
                .withLanguages(ImmutableList.of("es")).build();
        
        UserSession session = service.signIn(study, context, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(session.getParticipant().getLanguages(), TestConstants.LANGUAGES);
        
        verify(accountDao, never()).editAccount(any(), any(), any());
   }
    
    @Test
    public void languagePreferencesArePersisted() {
        // Language prefs are not persisted, so the context should cause an update
        when(accountDao.authenticate(study, EMAIL_PASSWORD_SIGN_IN)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withHealthCode(HEALTH_CODE).build();
        
        when(participantService.getParticipant(study, account, false)).thenReturn(participant);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withContext(CONTEXT)
                .withLanguages(TestConstants.LANGUAGES).build();
        
        UserSession session = service.signIn(study, context, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(session.getParticipant().getLanguages(), TestConstants.LANGUAGES);
        
        // Note that the context does not have the healthCode, you must use the participant
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq(HEALTH_CODE), any());
   }    
}