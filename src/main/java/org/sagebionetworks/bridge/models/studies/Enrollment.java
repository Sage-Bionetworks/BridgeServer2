package org.sagebionetworks.bridge.models.studies;

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
    
    static Enrollment create(String appId, String studyId, String accountId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(accountId);
        return new HibernateEnrollment(appId, studyId, accountId, null);
    }
    
    static Enrollment create(String appId, String studyId, String accountId, String externalId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(accountId);
        // it's ok for external ID to be null
        return new HibernateEnrollment(appId, studyId, accountId, externalId);
    }
    
    String getAppId();
    String getStudyId();
    String getAccountId();
    String getExternalId();
}
