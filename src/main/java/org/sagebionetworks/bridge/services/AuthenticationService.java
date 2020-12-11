package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.AuthUtils.IS_STUDY_TEAM_OR_WORKER;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.PasswordGenerator;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.validators.AccountIdValidator;
import org.sagebionetworks.bridge.validators.VerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.Validate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;

@Component("authenticationService")
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);

    static final int SIGNIN_GRACE_PERIOD_SECONDS = 5*60; // 5 min

    public enum ChannelType {
        EMAIL,
        PHONE,
    }
    
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private AccountService accountService;
    private ParticipantService participantService;
    private AppService appService;
    private PasswordResetValidator passwordResetValidator;
    private AccountWorkflowService accountWorkflowService;
    private IntentService intentService;
    private AccountSecretDao accountSecretDao;
    private OAuthProviderService oauthProviderService;
    private SponsorService sponsorService;
    
    @Autowired
    final void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    final void setBridgeConfig(BridgeConfig config) {
        this.config = config;
    }
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Autowired
    final void setPasswordResetValidator(PasswordResetValidator validator) {
        this.passwordResetValidator = validator;
    }
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    @Autowired
    final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    @Autowired
    final void setIntentToParticipateService(IntentService intentService) {
        this.intentService = intentService;
    }
    @Autowired
    final void setAccountSecretDao(AccountSecretDao accountSecretDao) {
        this.accountSecretDao = accountSecretDao;
    }
    @Autowired
    final void setOAuthProviderService(OAuthProviderService oauthProviderService) {
        this.oauthProviderService = oauthProviderService;
    }
    @Autowired
    final void setSponsorService(SponsorService sponsorService) {
        this.sponsorService = sponsorService;
    }
    
    /**
     * Sign in using a phone number and a token that was sent to that phone number via SMS. 
     */
    public UserSession phoneSignIn(CriteriaContext context, final SignIn signIn) {
        return channelSignIn(ChannelType.PHONE, context, signIn, SignInValidator.PHONE_SIGNIN);
    }
    
    /**
     * Sign in using an email address and a token that was supplied via a message to that email address. 
     */
    public UserSession emailSignIn(CriteriaContext context, final SignIn signIn) {
        return channelSignIn(ChannelType.EMAIL, context, signIn, SignInValidator.EMAIL_SIGNIN);
    }
    
    /**
     * This method returns the cached session for the user. A CriteriaContext object is not provided to the method, 
     * and the user's consent status is not re-calculated based on participation in one more more subpopulations. 
     * This only happens when calling session-constructing service methods (signIn and verifyEmail, both of which 
     * return newly constructed sessions).
     * @return session
     *      the persisted user session calculated on sign in or during verify email workflow
     */
    public UserSession getSession(String sessionToken) {
        if (sessionToken == null) {
            return null;
        }
        return cacheProvider.getUserSession(sessionToken);
    }
    
    /**
     * This method re-constructs the session based on potential changes to the user. It is called after a user 
     * account is updated, and takes the updated CriteriaContext to calculate the current state of the user. We 
     * do not rotate the reauthentication token just because the user updates their session.
     * @param app
     *      the user's app
     * @param context
     *      an updated set of criteria for calculating the user's consent status
     * @return
     *      newly created session object (not persisted)
     */
    public UserSession getSession(App app, CriteriaContext context) {
        checkNotNull(app);
        checkNotNull(context);

        Account account = accountService.getAccount(context.getAccountId());
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return getSessionFromAccount(app, context, account);
    }

    public UserSession signIn(App app, CriteriaContext context, SignIn signIn) throws EntityNotFoundException {
        checkNotNull(app);
        checkNotNull(context);
        checkNotNull(signIn);
        
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
        
        Account account = accountService.authenticate(app, signIn);
        
        clearSession(app.getIdentifier(), account.getId());
        UserSession session = getSessionFromAccount(app, context, account);

        // Do not call sessionUpdateService as we assume system is in sync with the session on sign in
        if (!session.doesConsent() && intentService.registerIntentToParticipate(app, account)) {
            AccountId accountId = AccountId.forId(app.getIdentifier(), account.getId());
            account = accountService.getAccount(accountId);
            session = getSessionFromAccount(app, context, account);
        }
        cacheProvider.setUserSession(session);
        
        if (!session.doesConsent() && !session.isInRole(ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }        
        return session;
    }
    
    public UserSession reauthenticate(App app, CriteriaContext context, SignIn signIn)
            throws EntityNotFoundException {
        checkNotNull(app);
        checkNotNull(context);
        checkNotNull(signIn);
        
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn); 

        // Reauth token is a 21-character alphanumeric (upper, lower, numbers), generated by a SecureRandom. This is
        // 125 bits of entropy. To see if apps are sending the old reauth token after successfully reauthenticating, we
        // will call .hashCode() on the reauth token, mod 1000, and log it (about 10 bits). Even if attackers steal
        // this hash-mod from the logs, they still need to determine the remaining 115 bits of the reauth token (about
        // 19 alphanumeric characters worth), and they have 5 minutes to do it before the grace period expires.
        //
        // This is effectively equivalent to the app submitting an token identification token and a 19-character reauth
        // token, which is still reasonably secure.
        int reauthHashMod = signIn.getReauthToken().hashCode() % 1000;
        LOG.debug("Reauth token hash-mod " + reauthHashMod + " submitted in request " + RequestContext.get().getId());

        Account account = accountService.reauthenticate(app, signIn);
        
        UserSession session = getSessionFromAccount(app, context, account);
        // If session exists, preserve the session token. Reauthenticating before the session times out will not
        // refresh the token or change the timeout, but it is harmless. At the time the session is set to
        // time out, it will still time out and the client will need to reauthenticate again.
        UserSession existing = cacheProvider.getUserSessionByUserId(account.getId());
        if (existing != null) {
            session.setSessionToken(existing.getSessionToken());
            session.setInternalSessionToken(existing.getInternalSessionToken());
        }
        cacheProvider.setUserSession(session);

        if (!session.doesConsent() && !session.isInRole(ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }

    public void signOut(final UserSession session) {
        if (session != null) {
            AccountId accountId = AccountId.forId(session.getAppId(), session.getId());
            accountService.deleteReauthToken(accountId);
            // session does not have the reauth token so the reauthToken-->sessionToken Redis entry cannot be 
            // removed, but once the reauth token is removed from the user table, the reauth token will no 
            // longer work (and is short-lived in the cache).
            cacheProvider.removeSession(session);
        } 
    }

    public IdentifierHolder signUp(App app, StudyParticipant participant) {
        checkNotNull(app);
        checkNotNull(participant);
        
        try {
            // This call is exposed without authentication, so the request context has no roles, and no roles 
            // can be assigned to this user.
            return participantService.createParticipant(app, participant, true);
        } catch(EntityAlreadyExistsException e) {
            AccountId accountId = null;
            // Clashes in the reassignment of external identifiers will now roll back account creation and 
            // throw a different exception EAEE that we must catch and address here.
            if ("ExternalIdentifier".equals(e.getEntityClass())) {
                String identifier = (String)e.getEntityKeys().get("identifier");
                accountId = AccountId.forExternalId(app.getIdentifier(), identifier);
                LOG.info("Sign up attempt using assigned external ID '"+identifier+"'");
            } else if ("Account".equals(e.getEntityClass())) {
                String userId = (String)e.getEntityKeys().get("userId");
                accountId = AccountId.forId(app.getIdentifier(), userId);    
                LOG.info("Sign up attempt using credential that exists in account '"+userId+"'");
            } else {
                LOG.error("Sign up attempt threw unanticipated EntityAlreadyExistsException: " + e.getMessage());
                return null;
            }
            // Suppress this and send an email to notify the user that the account already exists. From 
            // this call, we simply return a 200 the same as any other sign up. Otherwise the response 
            // reveals that the credential has been taken.
            accountWorkflowService.notifyAccountExists(app, accountId);
            return null;
        }
    }

    public void verifyChannel(ChannelType type, Verification verification) {
        checkNotNull(verification);

        Validate.entityThrowingException(VerificationValidator.INSTANCE, verification);
        Account account = accountWorkflowService.verifyChannel(type, verification);
        accountService.verifyChannel(type, account);
    }
    
    public void resendVerification(ChannelType type, AccountId accountId) {
        checkNotNull(accountId);

        Validate.entityThrowingException(AccountIdValidator.getInstance(type), accountId);
        try {
            accountWorkflowService.resendVerificationToken(type, accountId);    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            LOG.info("Resend " + type.name() + " verification for unregistered email in app '"
                    + accountId.getAppId() + "'");
        }
    }
    
    public void requestResetPassword(App app, boolean isAppAdmin, SignIn signIn) throws BridgeServiceException {
        checkNotNull(app);
        checkNotNull(signIn);
        
        // validate the data in signIn, then convert it to an account ID which we know will be valid.
        Validate.entityThrowingException(SignInValidator.REQUEST_RESET_PASSWORD, signIn);
        try {
            accountWorkflowService.requestResetPassword(app, isAppAdmin, signIn.getAccountId());    
        } catch(EntityNotFoundException e) {
            // Suppress this. Otherwise it reveals if the account does not exist
            LOG.info("Request reset password request for unregistered email in app '"+signIn.getAppId()+"'");
        }
    }

    public void resetPassword(PasswordReset passwordReset) throws BridgeServiceException {
        checkNotNull(passwordReset);

        Validate.entityThrowingException(passwordResetValidator, passwordReset);
        
        accountWorkflowService.resetPassword(passwordReset);
    }
    
    public GeneratedPassword generatePassword(App app, String externalId) {
        checkNotNull(app);
        
        if (StringUtils.isBlank(externalId)) {
            throw new BadRequestException("External ID is required");
        }

        AccountId accountId = AccountId.forExternalId(app.getIdentifier(), externalId);
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        
        Enrollment en = account.getEnrollments().stream()
                .filter(enrollment -> externalId.equals(enrollment.getExternalId()))
                .findAny()
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        IS_STUDY_TEAM_OR_WORKER.checkAndThrow("studyId", en.getStudyId());

        String password = generatePassword(app.getPasswordPolicy().getMinLength());
        accountService.changePassword(account, null, password);

        // Return the password and the user ID in case the account was just created.
        return new GeneratedPassword(externalId, account.getId(), password);
    }
    
    public String generatePassword(int policyLength) {
        return PasswordGenerator.INSTANCE.nextPassword(Math.max(32, policyLength));
    }
    
    private UserSession channelSignIn(ChannelType channelType, CriteriaContext context, SignIn signIn,
            Validator validator) {
        Validate.entityThrowingException(validator, signIn);

        // Verify sign-in token.
        CacheKey cacheKey = getCacheKeyForChannelSignIn(channelType, signIn);
        String storedToken = cacheProvider.getObject(cacheKey, String.class);
        String unformattedSubmittedToken = signIn.getToken().replaceAll("[-\\s]", "");
        if (storedToken == null || !storedToken.equals(unformattedSubmittedToken)) {
            throw new AuthenticationFailedException();
        }

        AccountId accountId = signIn.getAccountId();
        Account account = accountService.getAccount(accountId);
        // This should be unlikely, but if someone deleted the account while the token was outstanding
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        // Update account state before we create the session, so it's accurate...
        accountService.verifyChannel(channelType, account);

        // Check if we have a cached session for this sign-in token.
        UserSession cachedSession = null;
        CacheKey sessionCacheKey = CacheKey.channelSignInToSessionToken(storedToken);
        String cachedSessionToken = cacheProvider.getObject(sessionCacheKey, String.class);
        if (cachedSessionToken != null) {
            cachedSession = cacheProvider.getUserSession(cachedSessionToken);
        }

        UserSession session;
        if (cachedSession != null) {
            // If we have a cached session, then just use that session.
            session = cachedSession;
        } else {
            // We don't have a cached session. This is a new sign-in. Clear all old sessions for security reasons.
            // Then, create a new session.
            clearSession(context.getAppId(), account.getId());
            App app = appService.getApp(signIn.getAppId());
            session = getSessionFromAccount(app, context, account);

            // Check intent to participate.
            if (!session.doesConsent() && intentService.registerIntentToParticipate(app, account)) {
                account = accountService.getAccount(accountId);
                session = getSessionFromAccount(app, context, account);
            }
            cacheProvider.setUserSession(session);

            // Set the sign-in token cache key to the 5 minute grace period. This means that if the app successfully
            // signs in, but there's a network glitch and they don't get the session token, they can try again with the
            // same token.
            cacheProvider.setExpiration(cacheKey, SIGNIN_GRACE_PERIOD_SECONDS);

            // Cache the session token under the sign-in token, so that if the same token comes in during the grace
            // period, we can return the same session with the same token.
            cacheProvider.setObject(sessionCacheKey, session.getSessionToken(), SIGNIN_GRACE_PERIOD_SECONDS);
        }

        if (!session.doesConsent() && !session.isInRole(ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }
        return session;
    }

    private static CacheKey getCacheKeyForChannelSignIn(ChannelType channelType, SignIn signIn) {
        if (channelType == EMAIL) {
            return CacheKey.emailSignInRequest(signIn);
        } else if (channelType == PHONE) {
            return CacheKey.phoneSignInRequest(signIn);
        } else {
            throw new UnsupportedOperationException("Channel type not implemented");
        }
    }

    /**
     * Constructs a session based on the user's account, participant, and request context. This is called by sign-in
     * APIs, which creates the session. Package-scoped for unit tests.
     */
    public UserSession getSessionFromAccount(App app, CriteriaContext context, Account account) {
        StudyParticipant participant = participantService.getParticipant(app, account, false);

        // If the user does not have a language persisted yet, now that we have a session, we can retrieve it 
        // from the context, add it to the user/session, and persist it.
        if (participant.getLanguages().isEmpty() && !context.getLanguages().isEmpty()) {
            participant = new StudyParticipant.Builder().copyOf(participant)
                    .withLanguages(context.getLanguages()).build();
            
            // Note that the context does not have the healthCode, you must use the participant
            accountService.editAccount(app.getIdentifier(), participant.getHealthCode(),
                    accountToEdit -> accountToEdit.setLanguages(context.getLanguages()));
        }

        RequestContext reqContext = RequestContext.get();
        
        // Create new session.
        UserSession session = new UserSession(participant);
        session.setSessionToken(getGuid());
        session.setInternalSessionToken(getGuid());
        session.setAuthenticated(true);
        session.setEnvironment(config.getEnvironment());
        session.setIpAddress(reqContext.getCallerIpAddress());
        session.setAppId(app.getIdentifier());
        session.setReauthToken(account.getReauthToken());
        
        CriteriaContext newContext = updateContextFromSession(context, session);
        session.setConsentStatuses(consentService.getConsentStatuses(newContext, account));
        
        if (!Boolean.TRUE.equals(app.isReauthenticationEnabled())) {
            account.setReauthToken(null);
        } else {
            String reauthToken = generateReauthToken();
            accountSecretDao.createSecret(REAUTH, account.getId(), reauthToken);
            session.setReauthToken(reauthToken);
        }
        
        RequestContext.updateFromSession(session, sponsorService);
        
        return session;
    }
    
    // Provided to override in tests
    protected String generateReauthToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }
    
    public UserSession oauthSignIn(CriteriaContext context, OAuthAuthorizationToken authToken) {
        AccountId accountId = oauthProviderService.oauthSignIn(authToken);
        
        // This has not been observed to happen but in theory the user could deny access to their
        // Open Connect ID information, preventing this from being returned despite a successful 
        // OAuth exchange.
        if (accountId == null) {
            throw new EntityNotFoundException(Account.class);
        }
        Account account = accountService.getAccount(accountId);
        if (account == null) {
            throw new EntityNotFoundException(Account.class);
        }
        if (account.getRoles().isEmpty()) {
            throw new UnauthorizedException("Only administrative accounts can sign in via OAuth.");
        }
        
        clearSession(authToken.getAppId(), account.getId());
        App app = appService.getApp(authToken.getAppId());
        UserSession session = getSessionFromAccount(app, context, account);
        cacheProvider.setUserSession(session);
        
        return session;        
    }
    
    // As per https://sagebionetworks.jira.com/browse/BRIDGE-2127, signing in should invalidate any old sessions
    // (old session tokens should not be usable to retrieve the session) and we are deleting all outstanding 
    // reauthentication tokens. Call this after successfully authenticating, but before creating a session which 
    // also includes creating a new (valid) reauth token.
    private void clearSession(String appId, String userId) {
        AccountId accountId = AccountId.forId(appId, userId);
        accountService.deleteReauthToken(accountId);
        cacheProvider.removeSessionByUserId(userId);
    }

    // Sign-in methods contain a criteria context that includes no user information. After signing in, we need to
    // create an updated context with user info.
    private static CriteriaContext updateContextFromSession(CriteriaContext originalContext, UserSession session) {
        return new CriteriaContext.Builder()
                .withContext(originalContext)
                .withHealthCode(session.getHealthCode())
                .withLanguages(session.getParticipant().getLanguages())
                .withUserDataGroups(session.getParticipant().getDataGroups())
                .withUserStudyIds(session.getParticipant().getStudyIds())
                .withUserId(session.getId())
                .build();
    }
    
    protected String getGuid() {
        return BridgeUtils.generateGuid();
    }
}
