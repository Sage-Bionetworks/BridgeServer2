package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CANNOT_ACCESS_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.canAccessAccount;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.BridgeUtils.collectStudyIds;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.time.DateUtils;

@Component
public class AccountService {
    private AccountDao accountDao;
    private AppService appService;
    private ActivityEventService activityEventService;
    private ParticipantVersionService participantVersionService;
    private StudyActivityEventService studyActivityEventService;
    private CacheProvider cacheProvider;

    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    
    @Autowired
    final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }

    @Autowired
    final void setParticipantVersionService(ParticipantVersionService participantVersionService) {
        this.participantVersionService = participantVersionService;
    }

    @Autowired
    final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
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
        account.setAdmin(!account.getRoles().isEmpty() || account.getOrgMembership() != null);
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
            studyActivityEventService.publishEvent(builder.withStudyId(en.getStudyId()).build(), false, true);
        }
        CacheKey cacheKey = CacheKey.etag(DateTimeZone.class, account.getId());
        cacheProvider.setObject(cacheKey, account.getCreatedOn());

        // Create the corresponding Participant Version.
        participantVersionService.createParticipantVersionFromAccount(account);
    }
    
    /**
     * Save account changes. 
     */
    public void updateAccount(Account account) {
        checkNotNull(account);
        
        AccountId accountId = AccountId.forId(account.getAppId(),  account.getId());

        Account persistedAccount = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        boolean timeZoneUpdated = !ObjectUtils.nullSafeEquals(
                account.getClientTimeZone(), persistedAccount.getClientTimeZone());
        
        // The test_user flag taints an account; once set it cannot be unset.
        boolean testUser = persistedAccount.getDataGroups().contains(TEST_USER_GROUP);
        if (testUser) {
            Set<String> newDataGroups = addToSet(account.getDataGroups(), TEST_USER_GROUP);
            account.setDataGroups(newDataGroups);
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
        account.setAdmin(!account.getRoles().isEmpty() || account.getOrgMembership() != null);
        
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
                studyActivityEventService.publishEvent(builder.withStudyId(studyId).build(), false, true);
            }
        }

        if (timeZoneUpdated) {
            CacheKey cacheKey = CacheKey.etag(DateTimeZone.class, account.getId());
            cacheProvider.setObject(cacheKey, account.getModifiedOn());
        }
        // Create the corresponding Participant Version.
        participantVersionService.createParticipantVersionFromAccount(account);
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
        
        String oldTimeZone = account.getClientTimeZone();
 
        if (CANNOT_ACCESS_PARTICIPANTS.check(USER_ID, account.getId()) && !account.getDataGroups().contains(TEST_USER_GROUP)) {
            throw new UnauthorizedException();
        }
        accountEdits.accept(account);
        
        String newTimeZone = account.getClientTimeZone();
        account.setModifiedOn(DateUtils.getCurrentDateTime());
        
        accountDao.updateAccount(account);
        
        if (!ObjectUtils.nullSafeEquals(oldTimeZone, newTimeZone)) {
            CacheKey cacheKey = CacheKey.etag(DateTimeZone.class, account.getId());
            cacheProvider.setObject(cacheKey, account.getModifiedOn());
        }
        // Create the corresponding Participant Version.
        participantVersionService.createParticipantVersionFromAccount(account);
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
            Account account = opt.get();
            accountDao.deleteAccount(account.getId());
            
            CacheKey cacheKey = CacheKey.etag(DateTimeZone.class, account.getId());
            cacheProvider.removeObject(cacheKey);
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
}
