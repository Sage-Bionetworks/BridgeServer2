package org.sagebionetworks.bridge.models.accounts;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Iterables;

@JsonDeserialize(builder = AccountSummary.Builder.class)
public final class AccountSummary {
    
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String synapseUserId;
    private final Phone phone;
    private final Map<String,String> externalIds;
    private final String id;
    private final DateTime createdOn;
    private final AccountStatus status;
    private final String appId;
    private final Set<String> studyIds;
    private final Map<String, String> attributes;
    private final String orgMembership;
    private final String note;
    private final DateTimeZone clientTimeZone;
    
    private AccountSummary(String firstName, String lastName, String email, String synapseUserId, Phone phone,
            Map<String, String> externalIds, String id, DateTime createdOn, AccountStatus status, String appId,
            Set<String> studyIds, Map<String, String> attributes, String orgMembership, String note,
            DateTimeZone clientTimeZone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.synapseUserId = synapseUserId;
        this.phone = phone;
        this.externalIds = externalIds;
        this.id = id;
        this.createdOn = (createdOn == null) ? null : createdOn.withZone(DateTimeZone.UTC);
        this.status = status;
        this.appId = appId;
        this.studyIds = studyIds;
        this.attributes = attributes;
        this.orgMembership = orgMembership;
        this.note = note;
        this.clientTimeZone = clientTimeZone;
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
    
    public String getAppId() { 
        return appId;
    }
    
    public Set<String> getStudyIds() {
        return studyIds;
    }
    
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    public String getOrgMembership() {
        return orgMembership;
    }

    public String getNote() {
        return note;
    }

    public DateTimeZone getClientTimeZone() {
        return clientTimeZone;
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, email, synapseUserId, phone, externalIds, id, createdOn, status,
                appId, studyIds, attributes, orgMembership, note, clientTimeZone);
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
                && Objects.equals(id, other.id) && Objects.equals(appId, other.appId)
                && Objects.equals(studyIds, other.studyIds)
                && Objects.equals(attributes, other.attributes)
                && Objects.equals(orgMembership, other.orgMembership)
                && Objects.equals(note, other.note)
                && Objects.equals(clientTimeZone, other.clientTimeZone);
    }
    
    // no toString() method as the information is sensitive.
    public static class Builder {
        private String firstName;
        private String lastName;
        private String email;
        private String synapseUserId;
        private Phone phone;
        private String id;
        private DateTime createdOn;
        private AccountStatus status;
        private String appId;
        private Map<String,String> externalIds;
        private Set<String> studyIds;
        private Map<String, String> attributes;
        private String orgMembership;
        private String note;
        private DateTimeZone clientTimeZone;
        
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }
        public Builder withLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }
        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }
        public Builder withSynapseUserId(String synapseUserId) {
            this.synapseUserId = synapseUserId;
            return this;
        }
        public Builder withPhone(Phone phone) {
            this.phone = phone;
            return this;
        }
        public Builder withId(String id) {
            this.id = id;
            return this;
        }
        public Builder withCreatedOn(DateTime createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        public Builder withStatus(AccountStatus status) {
            this.status = status;
            return this;
        }
        public Builder withExternalIds(Map<String, String> externalIds) {
            this.externalIds = externalIds;
            return this;
        }
        public Builder withStudyIds(Set<String> studyIds) {
            this.studyIds = studyIds;
            return this;
        }
        public Builder withAttributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }
        public Builder withOrgMembership(String orgMembership) {
            this.orgMembership = orgMembership;
            return this;
        }
        public Builder withNote(String note) {
            this.note = note;
            return this;
        }
        public Builder withClientTimeZone(DateTimeZone clientTimeZone) {
            this.clientTimeZone = clientTimeZone;
            return this;
        }
        public AccountSummary build() {
            return new AccountSummary(firstName, lastName, email, synapseUserId, phone, externalIds, id, createdOn,
                    status, appId, studyIds, attributes, orgMembership, note, clientTimeZone);
        }
    }
}
