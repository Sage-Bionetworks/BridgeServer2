package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.BridgeUtils.getElement;

import java.util.Optional;

import org.sagebionetworks.bridge.models.studies.Enrollment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Minimal information to communicate an actor in the system. Ideally, the information 
 * we'd make available for "actionBy" fields, which record the actor taking an action 
 * (right now this is a String ID which can be used to look up the account, but it
 * can get expensive for clients to look up many actors for some presentations). 
 */
@JsonPropertyOrder({ "identifier", "firstName", "lastName", "email", "phone", "synapseUserId", "externalId",
        "orgMembership", "type" })
public final class AccountRef {
    
    private final Account account;
    private final String studyId;
    
    public AccountRef(Account account) {
        this.account = account;
        this.studyId = null;
    }
    public AccountRef(Account account, String studyId) {
        this.account = account;
        this.studyId = studyId;
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
    public String getExternalId() {
        if (studyId == null) {
            return null;
        }
        Optional<Enrollment> optional = getElement(account.getActiveEnrollments(), Enrollment::getStudyId, studyId);
        return (optional.isPresent()) ? optional.get().getExternalId() : null;
    }
}
