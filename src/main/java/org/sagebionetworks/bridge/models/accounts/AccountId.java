package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * An identifier that can be used to find an account (a study identifier with an ID, email, or phone number).
 * Note that AccountId inequality does not indicate the objects represent two different accounts! 
 */
public final class AccountId implements BridgeEntity {
    
    public final static AccountId forId(String appId, String id) {
        checkNotNull(appId);
        checkNotNull(id);
        return new AccountId(appId, id, null, null, null, null, null, true);
    }
    public final static AccountId forEmail(String appId, String email) {
        checkNotNull(appId);
        checkNotNull(email);
        return new AccountId(appId, null, email, null, null, null, null, true);
    }
    public final static AccountId forPhone(String appId, Phone phone) {
        checkNotNull(appId);
        checkNotNull(phone);
        return new AccountId(appId, null, null, phone, null, null, null, true);
    }
    public final static AccountId forHealthCode(String appId, String healthCode) {
        checkNotNull(appId);
        checkNotNull(healthCode);
        return new AccountId(appId, null, null, null, healthCode, null, null, true);
    }
    public final static AccountId forExternalId(String appId, String externalId) {
        checkNotNull(appId);
        checkNotNull(externalId);
        return new AccountId(appId, null, null, null, null, externalId, null, true);
    }
    public final static AccountId forSynapseUserId(String appId, String synapseUserId) {
        checkNotNull(appId);
        checkNotNull(synapseUserId);
        return new AccountId(appId, null, null, null, null, null, synapseUserId, true);
    }

    private final String appId;
    private final String id;
    private final String email;
    private final Phone phone;
    private final String healthCode;
    private final String externalId;
    private final String synapseUserId;
    private final boolean usePreconditions;

    @JsonCreator
    private AccountId(@JsonAlias("study") @JsonProperty("appId") String appId,
            @JsonProperty("id") String id, @JsonProperty("email") String email, 
            @JsonProperty("phone") Phone phone, @JsonProperty("healthCode") String healthCode, 
            @JsonProperty("externalId") String externalId, @JsonProperty("synapseUserId") String synapseUserId) {
        this(appId, id, email, phone, healthCode, externalId, synapseUserId, true);
    }
    
    private AccountId(String appId, String id, String email, Phone phone, String healthCode,
            String externalId, String synapseUserId, boolean usePreconditions) {
        this.appId = appId;
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.healthCode = healthCode;
        this.externalId = externalId;
        this.synapseUserId = synapseUserId;
        this.usePreconditions = usePreconditions;
    }
    
    // It's important to guard against constructing AccountId with one of the identifying values, 
    // then trying to retrieve a different value. Force a failure in this case until you get to 
    // the DAO where these values are checked to determine the type of database query.
    
    public String getAppId() {
        if (usePreconditions && appId == null) {
            throw new NullPointerException("AccountId.appId is null");
        }
        return appId;
    }
    public String getId() {
        if (usePreconditions && id == null) {
            throw new NullPointerException("AccountId.id is null");
        }
        return id;
    }
    public String getEmail() {
        if (usePreconditions && email == null) {
            throw new NullPointerException("AccountId.email is null");
        }
        return email;
    }
    public Phone getPhone() {
        if (usePreconditions && phone == null) {
            throw new NullPointerException("AccountId.phone is null");
        }
        return phone;
    }
    public String getHealthCode() {
        if (usePreconditions && healthCode == null) {
            throw new NullPointerException("AccountId.healthCode is null");
        }
        return healthCode;
    }
    public String getExternalId() {
        if (usePreconditions && externalId == null) {
            throw new NullPointerException("AccountId.externalId is null");
        }
        return externalId;
    }
    public String getSynapseUserId() { 
        if (usePreconditions && synapseUserId == null) {
            throw new NullPointerException("AccountId.synapseUserId is null");
        }
        return synapseUserId;
    }
    public AccountId getUnguardedAccountId() {
        return new AccountId(this.appId, this.id, this.email, this.phone, this.healthCode, this.externalId,
                this.synapseUserId, false);
    }
    @Override
    public int hashCode() {
        return Objects.hash(appId, email, id, phone, healthCode, externalId, synapseUserId, usePreconditions);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountId other = (AccountId) obj;
        return Objects.equals(appId, other.appId) &&
                Objects.equals(email, other.email) && 
                Objects.equals(id, other.id) &&
                Objects.equals(phone, other.phone) &&
                Objects.equals(healthCode, other.healthCode) &&
                Objects.equals(externalId, other.externalId) &&
                Objects.equals(synapseUserId, other.synapseUserId) && 
                Objects.equals(usePreconditions, other.usePreconditions);
    }
    @Override
    public String toString() {
        Set<Object> keys = Sets.newHashSet(id, email, phone, externalId, synapseUserId, (healthCode==null) ? null : "HEALTH_CODE");
        return "AccountId [appId=" + appId + ", credential=" + Joiner.on(", ").skipNulls().join(keys) + "]";
    }
    
}
