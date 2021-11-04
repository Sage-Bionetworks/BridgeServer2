package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CANNOT_ACCESS_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.canAccessAccount;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.BridgeUtils.collectStudyIds;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.accounts.AccountSecretType.REAUTH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.time.DateUtils;

@Component
public class AccountService {
    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    static final int ROTATIONS = 3;
    
    private AccountDao accountDao;
    private AccountSecretDao accountSecretDao;
    private AppService appService;
    private ActivityEventService activityEventService;
    private StudyActivityEventService studyActivityEventService;

    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Autowired
    public final void setAccountSecretDao(AccountSecretDao accountSecretDao) {
        this.accountSecretDao = accountSecretDao;
    }
    
    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    public final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    
    @Autowired
    public final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
    }
    
    // Provided to override in tests
    protected String generateGUID() {
        return BridgeUtils.generateGuid();
    }
    
    /**
     * Search for all accounts across apps that have the same Synapse user ID in common, 
     * and return a list of the app IDs where these accounts are found.
     */
    public List<String> getAppIdsForUser(String synapseUserId) {
        if (StringUtils.isBlank(synapseUserId)) {
            throw new BadRequestException("Account does not have a Synapse user");
        }
        return accountDao.getAppIdForUser(synapseUserId);
    }
    
    /**
     * Set the verified flag for the channel (email or phone) to true, and enable the account (if needed).
     */
    public void verifyChannel(AuthenticationService.ChannelType channelType, Account account) {
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
            account.setModifiedOn(DateUtils.getCurrentDateTime());
            accountDao.updateAccount(account);    
        }        
    }
    
    /**
     * Call to change a password, possibly verifying the channel used to reset the password. The channel 
     * type (which is optional, and can be null) is the channel that has been verified through the act 
     * of successfully resetting the password (sometimes there is no channel that is verified). 
     */
    public void changePassword(Account account, ChannelType channelType, String newPassword) {
        checkNotNull(account);
        
        PasswordAlgorithm passwordAlgorithm = DEFAULT_PASSWORD_ALGORITHM;
        
        String passwordHash = hashCredential(passwordAlgorithm, "password", newPassword);

        // Update
        DateTime modifiedOn = DateUtils.getCurrentDateTime();
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
        return authenticateInternal(app, account, signIn);        
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
        return authenticateInternal(app, account, signIn);        
    }
    
    /**
     * This clears the user's reauthentication token.
     */
    public void deleteReauthToken(Account account) {
        checkNotNull(account);

        accountSecretDao.removeSecrets(REAUTH, account.getId());
    }
    
    /**
     * Create an account. If the optional consumer is passed to this method and it throws an 
     * exception, the account will not be persisted (the consumer is executed after the persist 
     * is executed in a transaction, however).
     */
    public void createAccount(App app, Account account) {
        checkNotNull(app);
        checkNotNull(account);
        
        account.setAppId(app.getIdentifier());
        DateTime timestamp = DateUtils.getCurrentDateTime();
        account.setCreatedOn(timestamp);
        account.setModifiedOn(timestamp);
        account.setPasswordModifiedOn(timestamp);
        account.setMigrationVersion(MIGRATION_VERSION);
        if (CANNOT_ACCESS_PARTICIPANTS.check()) {
            Set<String> newDataGroups = addToSet(account.getDataGroups(), TEST_USER_GROUP);
            account.setDataGroups(newDataGroups);
        }

        // Create account. We don't verify studies because this is handled by validation
        accountDao.createAccount(app, account);
        
        if (!account.getEnrollments().isEmpty()) {
            activityEventService.publishEnrollmentEvent(
                    app, account.getHealthCode(), account.getCreatedOn());
        }
        StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder()
                .withAppId(app.getIdentifier())
                .withUserId(account.getId())
                .withObjectType(ENROLLMENT)
                .withTimestamp(account.getCreatedOn());
        for (Enrollment en : account.getEnrollments()) {
            studyActivityEventService.publishEvent(builder.withStudyId(en.getStudyId()).build(), false);
        }
    }
    
    /**
     * Save account changes. 
     */
    public void updateAccount(Account account) {
        checkNotNull(account);
        
        AccountId accountId = AccountId.forId(account.getAppId(),  account.getId());

        Account persistedAccount = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        // The test_user flag taints an account; once set it cannot be unset.
        boolean testUser = persistedAccount.getDataGroups().contains(TEST_USER_GROUP);
        if (testUser) {
            Set<String> newDataGroups = addToSet(account.getDataGroups(), TEST_USER_GROUP);
            account.setDataGroups(newDataGroups);
        }
        // Participant accounts shouldn't be submitted to this endpoint; but if they are we check
        // access, and throw if the caller is an org admin or a developer trying to operate on a 
        // production account. These checks cannot currently be represented in the AuthEvaluator 
        // checks.
        boolean isParticipant = persistedAccount.getRoles().isEmpty();
        if (isParticipant && CANNOT_ACCESS_PARTICIPANTS.check(USER_ID, persistedAccount.getId())) {
            if (RequestContext.get().isInRole(ORG_ADMIN) || !testUser) {
                throw new UnauthorizedException();    
            }
        }
        
        // None of these values should be changeable by the user.
        account.setAppId(persistedAccount.getAppId());
        account.setCreatedOn(persistedAccount.getCreatedOn());
        account.setHealthCode(persistedAccount.getHealthCode());
        account.setPasswordAlgorithm(persistedAccount.getPasswordAlgorithm());
        account.setPasswordHash(persistedAccount.getPasswordHash());
        account.setPasswordModifiedOn(persistedAccount.getPasswordModifiedOn());
        // This has to be changed via the membership APIs.
        account.setOrgMembership(persistedAccount.getOrgMembership());
        // Update modifiedOn.
        account.setModifiedOn(DateUtils.getCurrentDateTime());
        // Only allow Admins to update notes
        if (!RequestContext.get().isAdministrator()) {
            account.setNote(persistedAccount.getNote());
        }
        // Update. We don't verify studies because this is handled by validation
        accountDao.updateAccount(account);
        
        // If any enrollments have been added, then create an enrollment event for that enrollment.
        // We want to create these events only after we're sure the account has been updated to 
        // reflect the enrollments.
        Set<String> newStudies = Sets.newHashSet(collectStudyIds(account));
        newStudies.removeAll(collectStudyIds(persistedAccount));
        
        if (!newStudies.isEmpty()) {
            App app = appService.getApp(account.getAppId());
            activityEventService.publishEnrollmentEvent(app, 
                    account.getHealthCode(), account.getModifiedOn());
            
            StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder()
                    .withAppId(app.getIdentifier())
                    .withUserId(account.getId())
                    .withObjectType(ENROLLMENT)
                    .withTimestamp(account.getModifiedOn());
                    
            for (String studyId : newStudies) {
                studyActivityEventService.publishEvent(builder.withStudyId(studyId).build(), false);
            }
        }
    }
    
    /**
     * Load, and if it exists, edit and save an account. This method is intended to be used 
     * internally to update the account table state, and should not be used to propagate changes 
     * that come from an API caller. It does not perform all security checks. Some operations 
     * like enrollment would fail because the user is not yet in a study that's visible to 
     * the caller. But it does enforce the fact that developers/study designers should only 
     * be operating on test users. 
     */
    public void editAccount(AccountId accountId, Consumer<Account> accountEdits) {
        checkNotNull(accountId);
        
        Account account = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
 
        if (CANNOT_ACCESS_PARTICIPANTS.check(USER_ID, account.getId()) && !account.getDataGroups().contains(TEST_USER_GROUP)) {
            throw new UnauthorizedException();
        }
        accountEdits.accept(account);
        accountDao.updateAccount(account);
    }
    
    /**
     * Get an account in the context of a app by the user's ID, email address, health code,
     * or phone number. Returns null if the account cannot be found, or the caller does not have 
     * the correct permissions to access the account. The account’s enrollments will be filtered
     * so the caller can only see the enrollments in studies they have access to.
     */
    public Optional<Account> getAccount(AccountId accountId) {
        checkNotNull(accountId);

        Optional<Account> optional = accountDao.getAccount(accountId);
        if (!optional.isPresent()) {
            return optional;
        }
        if (!canAccessAccount( optional.get() )) {
            return Optional.empty();
        }
        Account account = optional.get();
        if (CAN_READ_PARTICIPANTS.check(USER_ID, account.getId(), ORG_ID, account.getOrgMembership())) {
            return optional;
        }
        // This was accessed through study rights, so remove the other studies from what the caller
        // can see.
        RequestContext context = RequestContext.get();
        Set<String> callerStudies = context.getOrgSponsoredStudies();
        Set<Enrollment> removals = account.getEnrollments().stream()
                .filter(en -> !callerStudies.contains(en.getStudyId())).collect(toSet());
        account.getEnrollments().removeAll(removals);
        return optional;
    }
    
    /**
     * Delete an account along with the authentication credentials.
     */
    public void deleteAccount(AccountId accountId) {
        checkNotNull(accountId);
        
        Optional<Account> opt = accountDao.getAccount(accountId);
        if (opt.isPresent()) {
            accountDao.deleteAccount(opt.get().getId());
        }
    }
    
    /**
     * Get a page of lightweight account summaries (most importantly, the email addresses of 
     * participants which are required for the rest of the participant APIs). 
     * @param appId
     *      retrieve participants in this app
     * @param search
     *      all the parameters necessary to perform a filtered search of user account summaries, including
     *      paging parameters.
     */
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(String appId, AccountSummarySearch search) {
        checkNotNull(appId);
        checkNotNull(search);
        
        return accountDao.getPagedAccountSummaries(appId, search);
    }
    
    /**
     * Get the health code for an account.
     */
    public Optional<String> getAccountHealthCode(String appId, String userIdToken) {
        return getAccountField(appId, userIdToken, Account::getHealthCode);
    }
    
    /**
     * Get the ID for an account.
     */
    public Optional<String> getAccountId(String appId, String userIdToken) {
        return getAccountField(appId, userIdToken, Account::getId);
    }
    
    private Optional<String> getAccountField(String appId, String userIdToken, Function<Account,String> func) {
        if (appId != null && userIdToken != null) {
            AccountId accountId = BridgeUtils.parseAccountId(appId, userIdToken);
            Account account = accountDao.getAccount(accountId).orElse(null);
            if (account != null) {
                return Optional.ofNullable(func.apply(account));
            }
        }
        return Optional.empty();
    }
    
    public PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize) {
        checkNotNull(appId);
        checkNotNull(studyId);

        return accountDao.getPagedExternalIds(appId, studyId, idFilter, offsetBy, pageSize);
    }
    
    public void deleteAllAccounts(String appId) {
        checkNotNull(appId);
        
        accountDao.deleteAllAccounts(appId);
    }
    
    protected Account authenticateInternal(App app, Account account, SignIn signIn) {
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
        return account;
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
    
    protected String hashCredential(PasswordAlgorithm algorithm, String type, String value) {
        try {
            return algorithm.generateHash(value);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error creating "+type+": " + ex.getMessage(), ex);
        }
    }    
}
