package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.MAX_USERS_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.BridgeUtils.addToSet;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.CAN_BE_EDITED_BY;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.AccountSummarySearch.EMPTY_SEARCH;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.validators.AdminAccountValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.Validator;

import com.google.common.collect.Sets;

@Component
public class AdminAccountService {

    private AppService appService;
    private AccountWorkflowService accountWorkflowService;
    private SmsService smsService;
    private AccountDao accountDao;
    private CacheProvider cacheProvider;
    private RequestInfoService requestInfoService;
    
    @Autowired
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    @Autowired
    final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService) {
        this.accountWorkflowService = accountWorkflowService;
    }
    @Autowired
    final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }
    
    // accessor for mocking in tests
    protected DateTime getCreatedOn() {
        return DateTime.now();
    }
    // accessor for mocking in tests
    protected DateTime getModifiedOn() {
        return DateTime.now();
    }
    // accessor for mocking in tests
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
    
    public Optional<Account> getAccount(String appId, String userToken) {
        AccountId accountId = BridgeUtils.parseAccountId(appId, userToken);
        return accountDao.getAccount(accountId);
    }

    public Account createAccount(String appId, Account submittedAccount) {
        checkNotNull(appId);
        checkNotNull(submittedAccount);
        
        App app = appService.getApp(appId);
        if (app == null) {
            throw new EntityNotFoundException(App.class);
        }
        throwExceptionIfLimitMetOrExceeded(app);
        
        Validator validator = new AdminAccountValidator(app.getPasswordPolicy(), app.getUserProfileAttributes());
        Validate.entityThrowingException(validator, submittedAccount);
        
        DateTime timestamp = getCreatedOn();
        
        Account account = Account.create();
        account.setAdmin(true);
        account.setId(generateGUID());
        account.setAppId(appId);
        account.setEmailVerified(FALSE);
        account.setPhoneVerified(FALSE);
        // not relevant for an admin, but I'm not sure all APIs could deal with this being null
        account.setHealthCode(generateGUID()); 
        account.setStatus(submittedAccount.getSynapseUserId() != null ? ENABLED : UNVERIFIED);
        account.setCreatedOn(timestamp);
        account.setModifiedOn(timestamp);
        account.setPasswordModifiedOn(timestamp);
        account.setMigrationVersion(MIGRATION_VERSION);
        // Mark the account as test in case it’s ever used to submit data
        account.setDataGroups(addToSet(submittedAccount.getDataGroups(), TEST_USER_GROUP));
        // Mark the account as no sharing as well
        account.setSharingScope(NO_SHARING);
        account.setNotifyByEmail(FALSE);
        account.setFirstName(submittedAccount.getFirstName());
        account.setLastName(submittedAccount.getLastName());
        account.setEmail(submittedAccount.getEmail());
        account.setPhone(submittedAccount.getPhone());
        account.setSynapseUserId(submittedAccount.getSynapseUserId());
        account.setClientData(submittedAccount.getClientData());
        account.setLanguages(submittedAccount.getLanguages());
        account.setClientTimeZone(submittedAccount.getClientTimeZone());
        account.setNote(submittedAccount.getNote());

        RequestContext context = RequestContext.get();

        // Admins and superadmins can set any organization membership
        if (context.isInRole(Roles.ADMIN, Roles.SUPERADMIN)) {
            account.setOrgMembership(submittedAccount.getOrgMembership());
        }
        // If they choose not to, or it‘s any other kind of account, set org to caller‘s organization
        if (account.getOrgMembership() == null) {
            account.setOrgMembership(context.getCallerOrgMembership());
        }
        for (String attribute : app.getUserProfileAttributes()) {
            String value = submittedAccount.getAttributes().get(attribute);
            account.getAttributes().put(attribute, value);
        }
        // Hash password if it has been supplied.
        if (submittedAccount.getPassword() != null) {
            try {
                PasswordAlgorithm passwordAlgorithm = DEFAULT_PASSWORD_ALGORITHM;
                String passwordHash = passwordAlgorithm.generateHash(submittedAccount.getPassword());
                account.setPasswordAlgorithm(passwordAlgorithm);
                account.setPasswordHash(passwordHash);
                account.setPassword(null);
            } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
                throw new BridgeServiceException("Error creating password: " + ex.getMessage(), ex);
            }
        }
        Set<Roles> finalRoles = updateRoles(context, submittedAccount.getRoles(), account.getRoles());
        account.setRoles(finalRoles);

        accountDao.createAccount(account);
        
        sendVerificationMessages(app, account);
        return account;
    }
    
    public Account updateAccount(String appId, Account account) {
        checkNotNull(account);
        
        App app = appService.getApp(appId);
        if (app == null) {
            throw new EntityNotFoundException(App.class);
        }
        if (account.getId() == null) {
            throw new EntityNotFoundException(Account.class);
        }
        account.setPassword(null); // don’t validate this value
        
        Validator validator = new AdminAccountValidator(app.getPasswordPolicy(), app.getUserProfileAttributes());
        Validate.entityThrowingException(validator, account);
        
        AccountId accountId = AccountId.forId(appId,  account.getId());
        Account persistedAccount = accountDao.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));
        
        // None of these values should be changeable by the user.
        account.setAppId(persistedAccount.getAppId());
        account.setAdmin(persistedAccount.isAdmin());
        account.setDataGroups(persistedAccount.getDataGroups());
        account.setCreatedOn(persistedAccount.getCreatedOn());
        account.setHealthCode(persistedAccount.getHealthCode());
        account.setPasswordAlgorithm(persistedAccount.getPasswordAlgorithm());
        account.setPasswordHash(persistedAccount.getPasswordHash());
        account.setPasswordModifiedOn(persistedAccount.getPasswordModifiedOn());
        account.setOrgMembership(persistedAccount.getOrgMembership());
        account.setMigrationVersion(persistedAccount.getMigrationVersion());
        account.setDataGroups(addToSet(account.getDataGroups(), TEST_USER_GROUP));
        account.setModifiedOn(getModifiedOn());
        account.setPhoneVerified(persistedAccount.getPhoneVerified());
        account.setEmailVerified(persistedAccount.getEmailVerified());
        if (!RequestContext.get().isInRole(ADMIN, WORKER)) {
            account.setStatus(persistedAccount.getStatus());
        }
        if (!ObjectUtils.nullSafeEquals(account.getEmail(), persistedAccount.getEmail())) {
            account.setEmailVerified(FALSE);
        }
        if (!ObjectUtils.nullSafeEquals(account.getPhone(), persistedAccount.getPhone())) {
            account.setPhoneVerified(FALSE);
        }
        RequestContext context = RequestContext.get();
        Set<Roles> finalRoles = updateRoles(context, account.getRoles(), persistedAccount.getRoles());
        account.setRoles(finalRoles);

        accountDao.updateAccount(account);
        
        sendVerificationMessages(app, account);
        
        return account;
    }
    
    public void deleteAccount(String appId, String userId) {
        checkNotNull(appId);
        checkNotNull(userId);
        
        AccountId accountId = AccountId.forId(appId,  userId);
        Optional<Account> opt = accountDao.getAccount(accountId);
        if (opt.isPresent()) {
            Account account = opt.get();
            accountDao.deleteAccount(account.getId());
            
            cacheProvider.removeSessionByUserId(account.getId());
            requestInfoService.removeRequestInfo(account.getId());
        }
    }
    
    protected void sendVerificationMessages(App app, Account account) {
        // send verify email
        if (account.getEmail() != null && account.getEmailVerified() != TRUE) {
            accountWorkflowService.sendEmailVerificationToken(app, account.getId(), account.getEmail());
        }
        // If you create an account with a phone number, this opts the phone number in to receiving SMS. We do this
        // _before_ phone verification / sign-in, because we need to opt the user in to SMS in order to send phone
        // verification / sign-in.
        Phone phone = account.getPhone();
        if (phone != null && account.getPhoneVerified() != TRUE) {
            smsService.optInPhoneNumber(account.getId(), phone);
            accountWorkflowService.sendPhoneVerificationToken(app, account.getId(), phone);
        }
    }
    
    /**
     * For each role added, the caller must have the right to add the role. Then for every role currently assigned, we
     * check and if the caller doesn't have the right to remove that role, we'll add it back. Then we save those
     * results.
     */
    protected Set<Roles> updateRoles(RequestContext context, Set<Roles> submittedRoles, Set<Roles> existingRoles) {
        Set<Roles> newRoleSet = Sets.newHashSet();
        // Caller can only add roles they have the rights to edit
        for (Roles role : submittedRoles) {
            if (callerCanEditRole(context, role)) {
                newRoleSet.add(role);
            }
        }
        // Callers also can't remove roles they don't have the rights to edit
        for (Roles role : existingRoles) {
            if (!callerCanEditRole(context, role)) {
                newRoleSet.add(role);
            }
        }
        return newRoleSet;
    }
    
    private boolean callerCanEditRole(RequestContext requestContext, Roles targetRole) {
        return requestContext.isInRole(CAN_BE_EDITED_BY.get(targetRole));
    }
    
    private void throwExceptionIfLimitMetOrExceeded(App app) {
        checkNotNull(app);
        
        if (app.getAccountLimit() > 0) {
            // It's sufficient to get minimum number of records, we're looking only at the total of all accounts
            PagedResourceList<AccountSummary> summaries = accountDao.getPagedAccountSummaries(app.getIdentifier(), EMPTY_SEARCH);
            if (summaries.getTotal() >= app.getAccountLimit()) {
                throw new LimitExceededException(String.format(MAX_USERS_ERROR, app.getAccountLimit()));
            }
        }
    }
}
