package org.sagebionetworks.bridge.models.substudies;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;

/**
 * Represents the enrollment of a participant in a study. If the record exists, the user 
 * is enrolled in the given study. (If not, the user is not enrolled in the study.)
 * Note that this is currently in transition (right now this record associates admin users
 * to studies, rather than looking at their organizational membership, and many existing
 * accounts do *not* have an enrollment record even though accounts are enrolled in the 
 * study. This will be migrated.
 */
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
