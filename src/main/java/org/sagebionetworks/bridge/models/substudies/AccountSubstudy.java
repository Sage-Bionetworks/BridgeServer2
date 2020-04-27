package org.sagebionetworks.bridge.models.substudies;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.hibernate.HibernateAccountSubstudy;

public interface AccountSubstudy {
    
    static AccountSubstudy create(String appId, String substudyId, String accountId) {
        checkNotNull(appId);
        checkNotNull(substudyId);
        checkNotNull(accountId);
        return new HibernateAccountSubstudy(appId, substudyId, accountId);
    }
    
    String getAppId();
    String getSubstudyId();
    String getAccountId();
    String getExternalId();
    void setExternalId(String externalId);
    void setSubstudyId(String substudyId); // for migration, then can be removed
}
