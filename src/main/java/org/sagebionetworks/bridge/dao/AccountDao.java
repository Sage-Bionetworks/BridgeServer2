package org.sagebionetworks.bridge.dao;

import java.util.List;
import java.util.Optional;

import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;

/**
 * DAO to retrieve personally identifiable account information, including authentication 
 * credentials.
 */
public interface AccountDao {
    
    int MIGRATION_VERSION = 1;
    
    /**
     * Search for all accounts across apps that have the same Synapse user ID in common, 
     * and return a list of the appIds where these accounts are found.
     * @param synapseUserId
     * @return list of appIds
     */
    List<String> getAppIdForUser(String synapseUserId);
    
    /**
     * Create an account. If the optional consumer is passed to this method and it throws an 
     * exception, the account will not be persisted.
     */
    void createAccount(Account account);
    
    /**
     * Save account changes. If the optional consumer is passed to this method and 
     * it throws an exception, the account will not be persisted.
     */
    void updateAccount(Account account);
    
    /**
     * Get an account in the context of an app by the user's ID, email address, health code,
     * phone number, or Synapse user ID. 
     */
    Optional<Account> getAccount(AccountId accountId);
    
    /**
     * Delete an account along with the authentication credentials.
     */
    void deleteAccount(String userId);
    
    /**
     * Get a page of lightweight account summaries. 
     * @param appId
     *      retrieve participants in this app
     * @param search
     *      all the parameters necessary to perform a filtered search of user account summaries, including
     *      paging parameters.
     */
    PagedResourceList<AccountSummary> getPagedAccountSummaries(String appId, AccountSummarySearch search);
    
    /**
     * Get a page of external IDs associated to the indicated study. These are really accounts, but 
     * older APIs continue to view these as paged external ID records.  
     */
    PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize);
    
    void deleteAllAccounts(String appId);
}    
