package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_PARTICIPANTS;
import static org.sagebionetworks.bridge.BridgeUtils.getElement;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.PasswordGenerator;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
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
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
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

@Component("authenticationService")
public class AuthenticationService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);
    
    static final String PASSWORD_RESET_TOKEN_EXPIRED = "Password reset token has expired (or already been used).";
    static final int SIGNIN_GRACE_PERIOD_SECONDS = 5*60; // 5 min
    static final int ROTATIONS = 3;

    public enum ChannelType {
        EMAIL,
        PHONE,
    }
    
    private CacheProvider cacheProvider;
    private BridgeConfig config;
    private ConsentService consentService;
    private AccountService accountService;
    private ParticipantService participantService;
    private AccountDao accountDao;
    private AppService appService;
    private PasswordResetValidator passwordResetValidator;
    private AccountWorkflowService accountWorkflowService;
    private IntentService intentService;
    private AccountSecretDao accountSecretDao;
    private OAuthProviderService oauthProviderService;
    private SponsorService sponsorService;
    private StudyService studyService;
    
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
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
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
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    // Provided to override in tests
    protected DateTime getModifiedOn() {
        return DateUtils.getCurrentDateTime();
    }
    
    // Provided to override in tests
    protected String getGuid() {
        return BridgeUtils.generateGuid();
    }
    
    // Provided to override in tests
    protected String generateReauthToken() {
        return SecureTokenGenerator.INSTANCE.nextToken();
    }
    
    // Provided to override in tests
    protected String generatePassword(int policyLength) {
        return PasswordGenerator.INSTANCE.nextPassword(Math.max(32, policyLength));
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
        UserSession session = cacheProvider.getUserSession(sessionToken);
        if (session != null) {
            RequestContext.updateFromSession(session, sponsorService);    
        }
        return session;
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

        Account account = accountDao.getAccount(context.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        return getSessionFromAccount(app, context, account);
    }

    public UserSession signIn(App app, CriteriaContext context, SignIn signIn) throws EntityNotFoundException {
        checkNotNull(app);
        checkNotNull(context);
        checkNotNull(signIn);
        
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
        
        Account account = accountDao.getAccount(signIn.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        verifyPassword(account, signIn.getPassword());
        checkStatusFlags(app, account, signIn);        
        
        clearSession(app.getIdentifier(), account);
        UserSession session = getSessionFromAccount(app, context, account);

        // Do not call sessionUpdateService as we assume system is in sync with the session on sign in
        if (!session.doesConsent() && intentService.registerIntentToParticipate(app, account)) {
            AccountId accountId = AccountId.forId(app.getIdentifier(), account.getId());
            account = accountDao.getAccount(accountId)
                    .orElseThrow(() -> new EntityNotFoundException(Account.class));
            session = getSessionFromAccount(app, context, account);
        }
        cacheProvider.setUserSession(session);
        
        if (!session.doesConsent() && !session.isInRole(ADMINISTRATIVE_ROLES)) {
            throw new ConsentRequiredException(session);
        }        
        return session;
    }
    
    /**
     * Authenticate a user with the supplied credentials, returning that user's account record
     * if successful. 
     */
    public Account authenticate(App app, SignIn signIn) {
        checkNotNull(app);
        checkNotNull(signIn);
        
        Account account = accountDao.getAccount(signIn.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        verifyPassword(account, signIn.getPassword());
        checkStatusFlags(app, account, signIn);
        return account;
    }

    /**
     * Re-acquire a valid session using a special token passed back on an
     * authenticate request. Allows the client to re-authenticate without prompting
     * for a password.
     */
    public Account reauthenticate(App app, SignIn signIn) {
        checkNotNull(app);
        checkNotNull(signIn);
        
        if (!TRUE.equals(app.isReauthenticationEnabled())) {
            throw new UnauthorizedException("Reauthentication is not enabled for app: " + app.getName());    
        }
        Account account = accountDao.getAccount(signIn.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        accountSecretDao.verifySecret(REAUTH, account.getId(), signIn.getReauthToken(), ROTATIONS)
            .orElseThrow(() -> new EntityNotFoundException(Account.class));
        checkStatusFlags(app, account, signIn);
        return account;
    }

    public UserSession reauthenticate(App app, CriteriaContext context, SignIn signIn)
            throws EntityNotFoundException {
        checkNotNull(app);
        checkNotNull(context);
        checkNotNull(signIn);
        
        if (!TRUE.equals(app.isReauthenticationEnabled())) {
            throw new UnauthorizedException("Reauthentication is not enabled for app: " + app.getName());    
        }
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

        Account account = accountDao.getAccount(signIn.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        accountSecretDao.verifySecret(REAUTH, account.getId(), signIn.getReauthToken(), ROTATIONS)
            .orElseThrow(() -> new EntityNotFoundException(Account.class));
        checkStatusFlags(app, account, signIn);        
        
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
    
    public void signUserOut(App app, String userId, boolean deleteReauthToken) {
        checkNotNull(app);
        checkArgument(isNotBlank(userId));

        AccountId accountId = AccountId.forId(app.getIdentifier(), userId);
        Account account = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        if (deleteReauthToken) {
            deleteReauthToken(account.getId());
        }
        cacheProvider.removeSessionByUserId(account.getId());
    }

    public void signOut(final UserSession session) {
        if (session != null) {
            AccountId accountId = AccountId.forId(session.getAppId(), session.getId());
            Account account = accountDao.getAccount(accountId).orElse(null);
            if (account != null) {
                deleteReauthToken(account.getId());
                // session does not have the reauth token so the reauthToken-->sessionToken Redis entry cannot be 
                // removed, but once the reauth token is removed from the user table, the reauth token will no 
                // longer work (and is short-lived in the cache).
                cacheProvider.removeSession(session);
            }
        } 
    }
    
    public void deleteReauthToken(String userId) {
        accountSecretDao.removeSecrets(REAUTH, userId);
    }

    public IdentifierHolder signUp(App app, StudyParticipant participant) {
        checkNotNull(app);
        checkNotNull(participant);
        
        // Code to maintain apps in production. External IDs must now be associated to a study 
        // because they enroll you, but older apps continue to submit a payload with an externalId 
        // field (not the externalIds map).
        if (participant.getExternalId() != null && participant.getExternalIds().isEmpty()) {
            // For apps that create accounts prior to calling sign up from the app (which happens), check and if 
            // the account with this external ID already exists, return quietly.
            AccountId accountId = AccountId.forExternalId(app.getIdentifier(), participant.getExternalId());
            Account account = accountDao.getAccount(accountId).orElse(null); 
            if (account != null) {
                return new IdentifierHolder(account.getId());
            }
            // Or, they are probably calling signup with an external ID and a password, but no study. Try to 
            // guess a reasonable default. If we can't the participant is returned as is and will fail 
            // to be created by ParticipantService.
            participant = findDefaultStudyForExternalId(app, participant);
        }
        
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
    
    /**
     * The StudyParticipant has been submitted to signUp using the externalId field (only). The issue is that 
     * we must know what study this external ID enrolls them in. Attempt to infer it for several studies that 
     * are submitting production accounts in this manner.
     */
    protected StudyParticipant findDefaultStudyForExternalId(App app, StudyParticipant participant) {
        Set<String> studyIds = studyService.getStudyIds(app.getIdentifier());
        String studyId = null;
        
        // Psorcast Validation has one study named "test", so use it if it’s the only study there is 
        if (studyIds.size() == 1) {
            studyId = Iterables.getFirst(studyIds, null);
        } else {
            // PKU Study and FPHS Lab want to use the *other* of two studies that's not the test study. Remove 
            // "test" and in these cases, they have one remaining study. That’s all the studies using this 
            // format, but if someone adds a further study, we will not break...but the study we take is no 
            // longer determinant. So log it for follow-up.
            studyIds = Sets.difference(studyIds, ImmutableSet.of("test"));
            studyId = Iterables.getFirst(studyIds, null);
            if (studyIds.size() != 1) {
                // There's a client that is not using the new API, but is adding studies to their app.
                LOG.info("StudyParticipant created in app '" + app.getIdentifier() + "' with an externalId that "
                        + "has no study, assigning externalId " + participant.getExternalId() + " to study " + studyId);
            }
        }
        // Fix the participant record so they are property enrolled if we found a studyId. For an app with no 
        // studies, the studyId could still be null. It’s impossible to add an external ID in that case 
        // (a study has been added to every app in an earlier migration).
        if (studyId != null) {
            return new StudyParticipant.Builder().copyOf(participant)
                    .withExternalIds(ImmutableMap.of(studyId, participant.getExternalId()))
                    .build();
        }
        return participant;
    }

    public void verifyChannel(ChannelType type, Verification verification) {
        checkNotNull(verification);

        Validate.entityThrowingException(VerificationValidator.INSTANCE, verification);
        Account account = accountWorkflowService.verifyChannel(type, verification);

        verifyChannel(type, account);
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
    
    /**
     * Use a supplied password reset token to change the password on an account. If the supplied 
     * token is not valid, this method throws an exception. If the token is valid but the account 
     * does not exist, an exception is also thrown (this would be unusual).
     */
    public void resetPassword(PasswordReset passwordReset) {
        checkNotNull(passwordReset);

        Validate.entityThrowingException(passwordResetValidator, passwordReset);

        // This pathway is unusual as the token may have been sent via email or phone, so test for both.
        CacheKey emailCacheKey = CacheKey.passwordResetForEmail(passwordReset.getSptoken(), passwordReset.getAppId());
        CacheKey phoneCacheKey = CacheKey.passwordResetForPhone(passwordReset.getSptoken(), passwordReset.getAppId());
        
        String email = cacheProvider.getObject(emailCacheKey, String.class);
        Phone phone = cacheProvider.getObject(phoneCacheKey, Phone.class);
        if (email == null && phone == null) {
            throw new BadRequestException(PASSWORD_RESET_TOKEN_EXPIRED);
        }
        cacheProvider.removeObject(emailCacheKey);
        cacheProvider.removeObject(phoneCacheKey);
        
        App app = appService.getApp(passwordReset.getAppId());
        ChannelType channelType = null;
        AccountId accountId = null;
        if (email != null) {
            accountId = AccountId.forEmail(app.getIdentifier(), email);
            channelType = ChannelType.EMAIL;
        } else if (phone != null) {
            accountId = AccountId.forPhone(app.getIdentifier(), phone);
            channelType = ChannelType.PHONE;
        } else {
            throw new BridgeServiceException("Could not reset password");
        }
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        changePassword(account, channelType, passwordReset.getPassword());
    }

    /**
     * Call to change a password, possibly verifying the channel used to reset the password. The channel 
     * type (which is optional, and can be null) is the channel that has been verified through the act 
     * of successfully resetting the password (sometimes there is no channel that is verified). 
     */
    protected void changePassword(Account account, ChannelType channelType, String newPassword) {
        checkNotNull(account);
        
        PasswordAlgorithm passwordAlgorithm = DEFAULT_PASSWORD_ALGORITHM;
        
        String passwordHash = BridgeUtils.hashCredential(passwordAlgorithm, "password", newPassword);

        // Update
        DateTime modifiedOn = getModifiedOn();
        account.setModifiedOn(modifiedOn);
        account.setPasswordAlgorithm(passwordAlgorithm);
        account.setPasswordHash(passwordHash);
        account.setPasswordModifiedOn(modifiedOn);
        // One of these (the channel used to reset the password) is also verified by resetting the password.
        if (channelType == EMAIL) {
            account.setEmailVerified(true);    
        } else if (channelType == PHONE) {
            account.setPhoneVerified(true);    
        }
        accountDao.updateAccount(account);
    }
    
    public GeneratedPassword generatePassword(App app, String externalId) {
        checkNotNull(app);
        
        if (StringUtils.isBlank(externalId)) {
            throw new BadRequestException("External ID is required");
        }

        AccountId accountId = AccountId.forExternalId(app.getIdentifier(), externalId);
        Account account = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
                
        Enrollment en = getElement(account.getEnrollments(), Enrollment::getExternalId, externalId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));

        CAN_EDIT_PARTICIPANTS.checkAndThrow(STUDY_ID, en.getStudyId());

        String password = generatePassword(app.getPasswordPolicy().getMinLength());
        
        PasswordAlgorithm passwordAlgorithm = DEFAULT_PASSWORD_ALGORITHM;
        
        String passwordHash = BridgeUtils.hashCredential(passwordAlgorithm, "password", password);

        // Update
        DateTime modifiedOn = getModifiedOn();
        account.setModifiedOn(modifiedOn);
        account.setPasswordAlgorithm(passwordAlgorithm);
        account.setPasswordHash(passwordHash);
        account.setPasswordModifiedOn(modifiedOn);
        accountDao.updateAccount(account);

        // Return the password and the user ID in case the account was just created.
        return new GeneratedPassword(externalId, account.getId(), password);
    }
    
    public UserSession channelSignIn(ChannelType channelType, CriteriaContext context, SignIn signIn) {
        if (channelType == ChannelType.EMAIL) {
            Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN, signIn);    
        } else if (channelType == ChannelType.PHONE) {
            Validate.entityThrowingException(SignInValidator.PHONE_SIGNIN, signIn);
        }
        // Verify sign-in token.
        CacheKey cacheKey = getCacheKeyForChannelSignIn(channelType, signIn);
        String storedToken = cacheProvider.getObject(cacheKey, String.class);
        String unformattedSubmittedToken = signIn.getToken().replaceAll("[-\\s]", "");
        if (storedToken == null || !storedToken.equals(unformattedSubmittedToken)) {
            throw new AuthenticationFailedException();
        }

        AccountId accountId = signIn.getAccountId();
        Account account = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        if (account.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        verifyChannel(channelType, account);

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
            clearSession(context.getAppId(), account);
            App app = appService.getApp(signIn.getAppId());
            session = getSessionFromAccount(app, context, account);

            // Check intent to participate.
            if (!session.doesConsent() && intentService.registerIntentToParticipate(app, account)) {
                account = accountDao.getAccount(accountId)
                        .orElseThrow(() -> new EntityNotFoundException(Account.class));
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
    
    protected void verifyChannel(AuthenticationService.ChannelType channelType, Account account) {
        checkNotNull(channelType);
        checkNotNull(account);
        
        // Do not modify the account if it is disabled (all email verification workflows are 
        // user triggered, and disabled means that a user cannot use or change an account).
        if (account.getStatus() == DISABLED) {
            return;
        }
        
        // Avoid updating on every sign in by examining object state first. We do update the status 
        // flag so we can see the value in the database, but it is now derived in memory from other fields 
        // of the account.
        boolean shouldUpdateEmailVerified = (channelType == EMAIL && !TRUE.equals(account.getEmailVerified()));
        boolean shouldUpdatePhoneVerified = (channelType == PHONE && !TRUE.equals(account.getPhoneVerified()));
        
        if (shouldUpdatePhoneVerified || shouldUpdateEmailVerified) {
            if (shouldUpdateEmailVerified) {
                account.setEmailVerified(TRUE);
            }
            if (shouldUpdatePhoneVerified) {
                account.setPhoneVerified(TRUE);
            }
            account.setModifiedOn(getModifiedOn());
            accountDao.updateAccount(account);    
        }        
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
    protected UserSession getSessionFromAccount(App app, CriteriaContext context, Account account) {
        StudyParticipant participant = participantService.getParticipant(app, account, false);

        // If the user does not have a language persisted yet, now that we have a session, we can retrieve it 
        // from the context, add it to the user/session, and persist it.
        if (participant.getLanguages().isEmpty() && !context.getLanguages().isEmpty()) {
            participant = new StudyParticipant.Builder().copyOf(participant)
                    .withLanguages(context.getLanguages()).build();
            
            // Note that the context does not have the healthCode, you must use the participant
            AccountId accountId = AccountId.forHealthCode(app.getIdentifier(), participant.getHealthCode());
            accountService.editAccount(accountId,
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
    
    public UserSession oauthSignIn(CriteriaContext context, OAuthAuthorizationToken authToken) {
        AccountId accountId = oauthProviderService.oauthSignIn(authToken);
        
        // This has not been observed to happen but in theory the user could deny access to their
        // Open Connect ID information, preventing this from being returned despite a successful 
        // OAuth exchange.
        if (accountId == null) {
            throw new EntityNotFoundException(Account.class);
        }
        Account account = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        if (account.getRoles().isEmpty()) {
            throw new UnauthorizedException("Only administrative accounts can sign in via OAuth.");
        }
        clearSession(authToken.getAppId(), account);
        App app = appService.getApp(authToken.getAppId());
        UserSession session = getSessionFromAccount(app, context, account);
        
        if (accountId.getUnguardedAccountId().getSynapseUserId() != null) {
            session.setSynapseAuthenticated(true);    
        }
        cacheProvider.setUserSession(session);
        
        return session;        
    }

    // As per https://sagebionetworks.jira.com/browse/BRIDGE-2127, signing in should invalidate any old sessions
    // (old session tokens should not be usable to retrieve the session) and we are deleting all outstanding 
    // reauthentication tokens. Call this after successfully authenticating, but before creating a session which 
    // also includes creating a new (valid) reauth token.
    private void clearSession(String appId, Account account) {
        deleteReauthToken(account.getId());
        cacheProvider.removeSessionByUserId(account.getId());
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
    
    protected void checkStatusFlags(App app, Account account, SignIn signIn) {
        // Auth successful, you can now leak further information about the account through other exceptions.
        // For email/phone sign ins, the specific credential must have been verified (unless we've disabled
        // email verification for older apps that didn't have full external ID support).
        if (account.getStatus() == UNVERIFIED && app.isEmailVerificationEnabled()) {
            throw new UnauthorizedException("Email or phone number have not been verified");
        } else if (account.getStatus() == DISABLED) {
            throw new AccountDisabledException();
        } else if (app.isVerifyChannelOnSignInEnabled()) {
            if (signIn.getPhone() != null && !TRUE.equals(account.getPhoneVerified())) {
                throw new UnauthorizedException("Phone number has not been verified");
            } else if (app.isEmailVerificationEnabled() && 
                    signIn.getEmail() != null && !TRUE.equals(account.getEmailVerified())) {
                throw new UnauthorizedException("Email has not been verified");
            }
        }
    }
    
    protected void verifyPassword(Account account, String plaintext) {
        // Verify password
        if (account.getPasswordAlgorithm() == null || StringUtils.isBlank(account.getPasswordHash())) {
            LOG.warn("Account " + account.getId() + " is enabled but has no password.");
            throw new EntityNotFoundException(Account.class);
        }
        try {
            if (!account.getPasswordAlgorithm().checkHash(account.getPasswordHash(), plaintext)) {
                // To prevent enumeration attacks, if the credential doesn't match, throw 404 account not found.
                throw new EntityNotFoundException(Account.class);
            }
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error validating password: " + ex.getMessage(), ex);
        }        
    }
}
