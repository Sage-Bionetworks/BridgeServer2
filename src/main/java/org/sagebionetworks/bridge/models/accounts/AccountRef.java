package org.sagebionetworks.bridge.models.accounts;

/**
 * Minimal information to communicate an actor in the system. Ideally, the information 
 * we'd make available for "actionBy" fields, which record the actor taking an action 
 * (right now this is a String ID which can be used to look up the account, but it
 * can get expensive for clients to look up many actors for some presentations). 
 */
public final class AccountRef {
    
    private final Account account;
    
    public AccountRef(Account account) {
        this.account = account;
    }
    
    public String getFirstName() {
        return account.getFirstName();
    }
    public String getLastName() {
        return account.getLastName();
    };
    public String getEmail() {
        return account.getEmail();
    }
    public Phone getPhone() {
        return account.getPhone();
    }
    public String getSynapseUserId() {
        return account.getSynapseUserId();
    };
    public String getOrgMembership() {
        return account.getOrgMembership();
    }
    public String getIdentifier() {
        return account.getId();
    }
}
