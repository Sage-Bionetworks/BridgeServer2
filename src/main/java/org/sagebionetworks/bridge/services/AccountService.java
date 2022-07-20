package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CANNOT_ACCESS_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.canAccessAccount;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PREVIEW_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.BridgeUtils.collectStudyIds;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

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
    @Autowired
    private AccountDao accountDao;
    @Autowired
    private AppService appService;
    @Autowired
    private ActivityEventService activityEventService;
    @Autowired
    private ParticipantVersionService participantVersionService;
    @Autowired
    private StudyActivityEventService studyActivityEventService;
    @Autowired
    private CacheProvider cacheProvider;
    
    // For cleaning up when deleting a user
    @Autowired
    private NotificationsService notificationsService;
    @Autowired
    private HealthDataService healthDataService;
    @Autowired
    private HealthDataEx3Service healthDataEx3Service;
    @Autowired
    private ScheduledActivityService scheduledActivityService;
    @Autowired
    private UploadService uploadService;
    @Autowired
    private RequestInfoService requestInfoService;
    
    // Provided to override in tests
    protected String generateGUID() {
        return BridgeUtils.generateGuid();
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
        accountDao.createAccount(account);
        
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
        participantVersionService.createParticipantVersionFromAccount(app, account);
    }
    
    /**
     * Save account changes. 
     */
    public void updateAccount(Account account) {
        checkNotNull(account);
        
        AccountId accountId = AccountId.forId(account.getAppId(),  account.getId());

        Account persistedAccount = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        String oldTimeZone = persistedAccount.getClientTimeZone();
        
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

        App app = appService.getApp(account.getAppId());
        if (!newStudies.isEmpty()) {
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
        
        if (!ObjectUtils.nullSafeEquals(account.getClientTimeZone(), oldTimeZone)) {
            CacheKey cacheKey = CacheKey.etag(DateTimeZone.class, account.getId());
            cacheProvider.setObject(cacheKey, account.getModifiedOn());
        }
        // Create the corresponding Participant Version.
        participantVersionService.createParticipantVersionFromAccount(app, account);
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
            
            // remove this first so if account is partially deleted, re-authenticating will pick
            // up accurate information about the state of the account (as we can recover it)
            cacheProvider.removeSessionByUserId(account.getId());
            requestInfoService.removeRequestInfo(account.getId());
            
            String healthCode = account.getHealthCode();
            healthDataService.deleteRecordsForHealthCode(healthCode);
            healthDataEx3Service.deleteRecordsForHealthCode(healthCode);
            notificationsService.deleteAllRegistrations(account.getAppId(), healthCode);
            uploadService.deleteUploadsForHealthCode(healthCode);
            scheduledActivityService.deleteActivitiesForUser(healthCode);
            activityEventService.deleteActivityEvents(account.getAppId(), healthCode);
            // AccountSecret records and Enrollment records are are deleted on a 
            // cascading delete from Account
            accountDao.deleteAccount(account.getId());
            
            // Remove known etag cache keys for this user
            cacheProvider.removeObject( CacheKey.etag(DateTimeZone.class, account.getId()) );
            cacheProvider.removeObject( CacheKey.etag(StudyActivityEvent.class, account.getId()) );
        }
    }
    
    /**
     * Delete all accounts that are preview users in this study. We check and throw an exception 
     * if someone attempts to enroll a preview user in more than one study, so it's safe to 
     * just delete here.
     */
    public void deleteAllPreviewAccounts(String appId, String studyId) {
        checkNotNull(appId);
        checkNotNull(studyId);

        AccountSummarySearch.Builder searchBuilder = new AccountSummarySearch.Builder()
                .withPageSize(API_MAXIMUM_PAGE_SIZE)
                .withAllOfGroups(ImmutableSet.of(PREVIEW_USER_GROUP))
                .withEnrolledInStudyId(studyId);
        
        // Retrieve and delete pages from offset 0 until no items are returned. Currently
        // an error will halt the process, but there are not many (or any) ways for a 
        // participant to be unremovable in the database
        PagedResourceList<AccountSummary> page = null;
        do {
            page = getPagedAccountSummaries(appId, searchBuilder.build());
            for (AccountSummary summary : page.getItems()) {
                // It is too slow to use deleteAccount because it cleans up a ton of
                // DynamoDB resources. So... we leave all the non-relational data behind.
                accountDao.deleteAccount(summary.getId());
            }
        } while(!page.getItems().isEmpty());
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
