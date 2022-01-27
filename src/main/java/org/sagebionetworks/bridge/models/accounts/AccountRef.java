package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.BridgeUtils.getElement;

import java.util.Optional;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;

import org.sagebionetworks.bridge.models.studies.Enrollment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Minimal information to communicate an actor in the system. Ideally, the information 
 * we'd make available for "actionBy" fields, which record the actor taking an action 
 * (right now this is a String ID which can be used to look up the account, but it
 * can get expensive for clients to look up many actors for some presentations). 
 */
@JsonPropertyOrder({ "identifier", "firstName", "lastName", "email", "phone", "synapseUserId", "externalId",
        "orgMembership", "type" })
@Embeddable
public final class AccountRef {
    
    private String firstName;
    private String lastName;
    private String email;
    @Embedded
    private Phone phone;
    private String synapseUserId;
    private String orgMembership;
    private String identifier;
    private String externalId;
    
    // Default constructor for Hibernate
    AccountRef() {
    }
    
    public AccountRef(Account account) {
        this.firstName = account.getFirstName();
        this.lastName = account.getLastName();
        this.email = account.getEmail();
        this.phone = account.getPhone();
        this.synapseUserId = account.getSynapseUserId();
        this.orgMembership = account.getOrgMembership();
        this.identifier = account.getId();
    }

    public AccountRef(Account account, String studyId) {
        this(account);
        Optional<Enrollment> optional = getElement(account.getActiveEnrollments(), Enrollment::getStudyId, studyId);
        this.externalId = (optional.isPresent()) ? optional.get().getExternalId() : null;
    }
    
    @JsonCreator
    public AccountRef(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
            @JsonProperty("email") String email, @JsonProperty("phone") Phone phone,
            @JsonProperty("synapseUserId") String synapseUserId, @JsonProperty("orgMembership") String orgMembership,
            @JsonProperty("identifier") String identifier, @JsonProperty("externalId") String externalId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.synapseUserId = synapseUserId;
        this.orgMembership = orgMembership;
        this.identifier = identifier;
        this.externalId = externalId;
    }
    
    public String getFirstName() {
        return firstName;
    }
    public String getLastName() {
        return lastName;
    };
    public String getEmail() {
        return email;
    }
    public Phone getPhone() {
        return phone;
    }
    public String getSynapseUserId() {
        return synapseUserId;
    };
    public String getOrgMembership() {
        return orgMembership;
    }
    public String getIdentifier() {
        return identifier;
    }
    public String getExternalId() {
        return externalId;
    }
}
