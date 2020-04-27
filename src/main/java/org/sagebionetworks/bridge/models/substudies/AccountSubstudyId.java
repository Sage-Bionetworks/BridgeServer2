package org.sagebionetworks.bridge.models.substudies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class AccountSubstudyId implements Serializable {

    @Column(name = "studyId")
    private String appId;

    @Column(name = "substudyId")
    private String substudyId;
    
    @Column(name = "accountId")
    private String accountId;

    public AccountSubstudyId() {
    }
    public AccountSubstudyId(String appId, String substudyId, String accountId) {
        this.appId = appId;
        this.substudyId = substudyId;
        this.accountId = accountId;
    }
    
    public String getAppId() {
        return appId;
    }
    public String getSubstudyId() {
        return substudyId;
    }
    public String getAccountId() {
        return accountId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, substudyId, accountId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountSubstudyId other = (AccountSubstudyId) obj;
        return Objects.equals(appId, other.appId) &&
                Objects.equals(substudyId, other.substudyId) &&
                Objects.equals(accountId, other.accountId);
    }    
}
