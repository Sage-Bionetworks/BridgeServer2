package org.sagebionetworks.bridge.hibernate;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudyId;

@Entity
@Table(name = "AccountsSubstudies")
@IdClass(AccountSubstudyId.class)
public final class HibernateAccountSubstudy implements AccountSubstudy {

    @Id
    private String appId;
    @Id
    private String substudyId;
    @Id
    @JoinColumn(name = "account_id")
    private String accountId;
    private String externalId;
    
    // Needed for Hibernate, or else you have to create an instantiation helper class
    public HibernateAccountSubstudy() {
    }
    
    public HibernateAccountSubstudy(String appId, String substudyId, String accountId) {
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
    public String getExternalId() {
        return externalId;
    }
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
    public void setSubstudyId(String substudyId) {
        this.substudyId = substudyId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, externalId, appId, substudyId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        HibernateAccountSubstudy other = (HibernateAccountSubstudy) obj;
        return Objects.equals(accountId, other.accountId) && 
               Objects.equals(externalId, other.externalId) && 
               Objects.equals(appId, other.appId) && 
               Objects.equals(substudyId, other.substudyId);
    }

    @Override
    public String toString() {
        return "HibernateAccountSubstudy [appId=" + appId + ", substudyId=" + substudyId + ", accountId="
                + accountId + ", externalId=" + externalId + "]";
    }
}
