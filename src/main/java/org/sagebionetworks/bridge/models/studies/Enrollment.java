package org.sagebionetworks.bridge.models.studies;

import static com.google.common.base.Preconditions.checkNotNull;

    import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.HibernateEnrollment;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Represents the enrollment of a participant in a study. Enrollment in the Bridge sense 
 * is a little broader than active enrollment in a study. An enrollment record can be 
 * created as soon as an account is being tracked in a study (e.g. an account is created 
 * with an external ID, which is always associated to a study), and the record will 
 * continue to exist if the user withdraws. However, it is not a complete history of 
 * consent activity; that continues to be recorded in the user's consent history and 
 * consent signatures. This record only records the current state of the account.
 */
@JsonDeserialize(as=HibernateEnrollment.class)
public interface Enrollment extends BridgeEntity {
    
    static Enrollment create(String appId, String studyId, String accountId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(accountId);
        
        HibernateEnrollment enrollment = new HibernateEnrollment();
        enrollment.setAppId(appId);
        enrollment.setStudyId(studyId);
        enrollment.setAccountId(accountId);
        return enrollment;
    }
    
    static Enrollment create(String appId, String studyId, String accountId, String externalId) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(accountId);

        HibernateEnrollment enrollment = new HibernateEnrollment();
        enrollment.setAppId(appId);
        enrollment.setStudyId(studyId);
        enrollment.setAccountId(accountId);
        // it's ok for external ID to be null
        enrollment.setExternalId(externalId);
        return enrollment;
    }
    
    String getAppId();
    void setAppId(String appId);
    
    String getStudyId();
    void setStudyId(String studyId);
    
    @JsonProperty("userId")
    String getAccountId();
    void setAccountId(String accountId);
    
    String getExternalId();
    void setExternalId(String externalId);
    
    /** Should the participant still be required to sign the study's required consent (if any)? 
     * If true, we will check the user's consent state during authentication; if false, consent
     * state will be ignored. Defaults to true.
     */
    boolean isConsentRequired();
    void setConsentRequired(boolean consentRequired);
    
    /** The signedOn timestamp of a consent signature, or the time of record creation. */
    DateTime getEnrolledOn();
    void setEnrolledOn(DateTime enrolledOn);
    
    /** The withdrewOn timestamp of a consent signature, or time of withdrawal of an enrollment record. */
    DateTime getWithdrawnOn();
    void setWithdrawnOn(DateTime withdrawnOn);
    
    /** If the enrollment is created by someone other than the participant, the user ID of the 
     * caller who enrolled this participant.
     */
    String getEnrolledBy();
    void setEnrolledBy(String accountId);

    /** If the enrollment is withdrawn by someone other than the participant, the user ID of the 
     * caller who withdrew the participant from the study.
     */
    String getWithdrawnBy();
    void setWithdrawnBy(String accountId);
    
    /** If withdrawn by a third party, a note can be recorded about why the participant was removed
     * from the study.
     */
    String getWithdrawalNote();
    void setWithdrawalNote(String note);
}
