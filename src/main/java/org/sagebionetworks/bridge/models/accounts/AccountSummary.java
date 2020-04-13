package org.sagebionetworks.bridge.models.accounts;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Iterables;

public final class AccountSummary {
    
    // This is the one class that exposes this object through the API. Because this 
    // may involve updating some downstream projects like BridgeWorkerPlatform, leave
    // it until the full refactor to getAppId() is done, then swap out. getStudyId has
    // been added and will be refactored to getAppId in the next step of migration.
    public final static class StudyIdentifier {
        private final String identifier;
        StudyIdentifier(String identifier) {
            this.identifier = identifier;
        }
        public String getIdentifier() {
            return identifier;
        }
    }
    
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String synapseUserId;
    private final Phone phone;
    private final Map<String,String> externalIds;
    private final String id;
    private final DateTime createdOn;
    private final AccountStatus status;
    private final String studyId;
    private final Set<String> substudyIds;
    
    @JsonCreator
    public AccountSummary(@JsonProperty("firstName") String firstName, @JsonProperty("lastName") String lastName,
            @JsonProperty("email") String email, @JsonProperty("synapseUserId") String synapseUserId,
            @JsonProperty("phone") Phone phone, @JsonProperty("externalIds") Map<String, String> externalIds,
            @JsonProperty("id") String id, @JsonProperty("createdOn") DateTime createdOn,
            @JsonProperty("status") AccountStatus status, @JsonProperty("studyIdentifier") String studyId,
            @JsonProperty("substudyIds") Set<String> substudyIds) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.synapseUserId = synapseUserId;
        this.phone = phone;
        this.externalIds = externalIds;
        this.id = id;
        this.createdOn = (createdOn == null) ? null : createdOn.withZone(DateTimeZone.UTC);
        this.status = status;
        this.studyId = studyId;
        this.substudyIds = substudyIds;
    }
    
    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public Phone getPhone() {
        return phone;
    }
    
    public String getSynapseUserId() {
        return synapseUserId;
    }
    
    public Map<String, String> getExternalIds() {
        return externalIds;
    }
    
    @Deprecated
    public String getExternalId() {
        // For backwards compatibility since we are no longer loading this in HibernateAccountDao,
        // do return a value (99.9% of the time, the only value). Some external consumers of the 
        // API might attempt to look up this value on the AccountSummary object.
        if (externalIds != null) {
            return Iterables.getFirst(externalIds.values(), null);    
        }
        return null;
    }
    
    public String getId() {
        return id;
    }

    public DateTime getCreatedOn() {
        return createdOn;
    }

    public AccountStatus getStatus() {
        return status;
    }
    
    public StudyIdentifier getStudyIdentifier() {
        return new StudyIdentifier(studyId);
    }
    
    public String getStudyId() { 
        return studyId;
    }
    
    public Set<String> getSubstudyIds() {
        return substudyIds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, email, synapseUserId, phone, externalIds, id, createdOn, status,
                studyId, substudyIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountSummary other = (AccountSummary) obj;
        return Objects.equals(firstName, other.firstName) && Objects.equals(lastName, other.lastName)
                && Objects.equals(email, other.email) && Objects.equals(phone, other.phone)
                && Objects.equals(externalIds, other.externalIds) && Objects.equals(synapseUserId, other.synapseUserId)
                && Objects.equals(createdOn, other.createdOn) && Objects.equals(status, other.status)
                && Objects.equals(id, other.id) && Objects.equals(studyId, other.studyId)
                && Objects.equals(substudyIds, other.substudyIds);
    }
    
    // no toString() method as the information is sensitive.
}
