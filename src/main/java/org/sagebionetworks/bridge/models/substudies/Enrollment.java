package org.sagebionetworks.bridge.models.substudies;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;

public interface Enrollment {
    
    static Enrollment create(String appId, String substudyId, String accountId) {
        checkNotNull(appId);
        checkNotNull(substudyId);
        checkNotNull(accountId);
        return new HibernateEnrollment(appId, substudyId, accountId, null);
    }
    
    static Enrollment create(String appId, String substudyId, String accountId, String externalId) {
        checkNotNull(appId);
        checkNotNull(substudyId);
        checkNotNull(accountId);
        // it's ok for external ID to be null
        return new HibernateEnrollment(appId, substudyId, accountId, externalId);
    }
    
    String getAppId();
    String getSubstudyId();
    String getAccountId();
    String getExternalId();
}
