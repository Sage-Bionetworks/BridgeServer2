package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

@Component
public class AccountService {
    
    private AccountDao accountDao;
    
    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    
    /**
     * Search for all accounts across studies that have the same Synapse user ID in common, 
     * and return a list of the study IDs where these accounts are found.
     * @param synapseUserId
     * @return list of study identifiers
     */
    public List<String> getStudyIdsForUser(String synapseUserId) {
        return accountDao.getStudyIdsForUser(synapseUserId);
    }
    
    /**
     * Set the verified flag for the channel (email or phone) to true, and enable the account (if needed).
     */
    public void verifyChannel(AuthenticationService.ChannelType channelType, Account account) {
        accountDao.verifyChannel(channelType, account);
    }
    
    /**
     * Call to change a password, possibly verifying the channel used to reset the password. The channel 
     * type (which is optional, and can be null) is the channel that has been verified through the act 
     * of successfully resetting the password (sometimes there is no channel that is verified). 
     */
    public void changePassword(Account account, ChannelType channelType, String newPassword) {
        accountDao.changePassword(account, channelType, newPassword);
    }
    
    /**
     * Authenticate a user with the supplied credentials, returning that user's account record
     * if successful. 
     */
    public Account authenticate(Study study, SignIn signIn) {
        return accountDao.authenticate(study, signIn);
    }

    /**
     * Re-acquire a valid session using a special token passed back on an
     * authenticate request. Allows the client to re-authenticate without prompting
     * for a password.
     */
    public Account reauthenticate(Study study, SignIn signIn) {
        return accountDao.reauthenticate(study, signIn);
    }
    
    /**
     * This clears the user's reauthentication token.
     */
    public void deleteReauthToken(AccountId accountId) {
        accountDao.deleteReauthToken(accountId);
    }
    
    /**
     * Create an account. If the optional consumer is passed to this method and it throws an 
     * exception, the account will not be persisted (the consumer is executed after the persist 
     * is executed in a transaction, however).
     */
    public void createAccount(Study study, Account account, Consumer<Account> afterPersistConsumer) {
        accountDao.createAccount(study, account, afterPersistConsumer);
    }
    
    /**
     * Save account changes. Account should have been retrieved from the getAccount() method 
     * (constructAccount() is not sufficient). If the optional consumer is passed to this method and 
     * it throws an exception, the account will not be persisted (the consumer is executed after 
     * the persist is executed in a transaction, however).
     */
    public void updateAccount(Account account, Consumer<Account> afterPersistConsumer) {
        accountDao.updateAccount(account, afterPersistConsumer);
    }
    
    /**
     * Load, and if it exists, edit and save an account. 
     */
    public void editAccount(StudyIdentifier studyId, String healthCode, Consumer<Account> accountEdits) {
        accountDao.editAccount(studyId, healthCode, accountEdits);
    }
    
    /**
     * Get an account in the context of a study by the user's ID, email address, health code,
     * or phone number. Returns null if there is no account, it is up to callers to translate 
     * this into the appropriate exception, if any. 
     */
    public Account getAccount(AccountId accountId) {
        return accountDao.getAccount(accountId);
    }
    
    /**
     * Delete an account along with the authentication credentials.
     */
    public void deleteAccount(AccountId accountId) {
        accountDao.deleteAccount(accountId);
    }
    
    /**
     * Get a page of lightweight account summaries (most importantly, the email addresses of 
     * participants which are required for the rest of the participant APIs). 
     * @param study
     *      retrieve participants in this study
     * @param search
     *      all the parameters necessary to perform a filtered search of user account summaries, including
     *      paging parameters.
     */
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
        return accountDao.getPagedAccountSummaries(study, search);
    }
    
    /**
     * For MailChimp, and other external systems, we need a way to get a healthCode for a given email.
     */
    public String getHealthCodeForAccount(AccountId accountId) {
        Account account = getAccount(accountId);
        if (account != null) {
            return account.getHealthCode();
        } else {
            return null;
        }
    }
}
