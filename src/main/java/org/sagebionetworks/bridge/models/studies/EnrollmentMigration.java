package org.sagebionetworks.bridge.models.studies;

import org.joda.time.DateTime;

/**
 * A (temporary?) model object so supeadmins can migrate user enrollments. 
 */
public class EnrollmentMigration {
    
    public static final EnrollmentMigration create(Enrollment enrollment) {
        EnrollmentMigration m = new EnrollmentMigration();
        m.setAppId(enrollment.getAppId());
        m.setStudyId(enrollment.getStudyId());
        m.setUserId(enrollment.getAccountId());
        m.setExternalId(enrollment.getExternalId());
        m.setEnrolledOn(enrollment.getEnrolledOn());
        m.setWithdrawnOn(enrollment.getWithdrawnOn());
        m.setEnrolledBy(enrollment.getEnrolledBy());
        m.setWithdrawnBy(enrollment.getWithdrawnBy());
        m.setWithdrawalNote(enrollment.getWithdrawalNote());
        m.setConsentRequired(enrollment.isConsentRequired());
        return m;
    }
    
    private String appId;
    private String studyId;
    private String userId;
    private String externalId;
    private DateTime enrolledOn;
    private DateTime withdrawnOn;
    private String enrolledBy;
    private String withdrawnBy;
    private String withdrawalNote;
    private boolean consentRequired;

    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getExternalId() {
        return externalId;
    }
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
    public DateTime getEnrolledOn() {
        return enrolledOn;
    }
    public void setEnrolledOn(DateTime enrolledOn) {
        this.enrolledOn = enrolledOn;
    }
    public DateTime getWithdrawnOn() {
        return withdrawnOn;
    }
    public void setWithdrawnOn(DateTime withdrawnOn) {
        this.withdrawnOn = withdrawnOn;
    }
    public String getEnrolledBy() {
        return enrolledBy;
    }
    public void setEnrolledBy(String enrolledBy) {
        this.enrolledBy = enrolledBy;
    }
    public String getWithdrawnBy() {
        return withdrawnBy;
    }
    public void setWithdrawnBy(String withdrawnBy) {
        this.withdrawnBy = withdrawnBy;
    }
    public String getWithdrawalNote() {
        return withdrawalNote;
    }
    public void setWithdrawalNote(String withdrawalNote) {
        this.withdrawalNote = withdrawalNote;
    }
    public boolean isConsentRequired() {
        return consentRequired;
    }
    public void setConsentRequired(boolean consentRequired) {
        this.consentRequired = consentRequired;
    }
    
    public Enrollment asEnrollment() {
        Enrollment enrollment = Enrollment.create(appId, studyId, userId, externalId);
        enrollment.setEnrolledBy(enrolledBy);
        enrollment.setEnrolledOn(enrolledOn);
        enrollment.setWithdrawnBy(withdrawnBy);
        enrollment.setWithdrawnOn(withdrawnOn);
        enrollment.setWithdrawalNote(withdrawalNote);
        enrollment.setConsentRequired(consentRequired);
        return enrollment;
    }
}
