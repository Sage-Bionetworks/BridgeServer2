package org.sagebionetworks.bridge.models.studies;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.accounts.AccountRef;

/**
 * The basic enrollment record is difficult to display because it only contains the IDs of the 
 * actors involved in the enrollment. This object provides a simple reference object for accounts
 * so UIs have the information to display and link to the actor. (Ideally this would replace many
 * of the "actionBy" fields that are only the ID of the actor);
 */
public class EnrollmentDetail {
    
    private final Enrollment enrollment;
    private final AccountRef participant;
    private final AccountRef enrolledBy;
    private final AccountRef withdrawnBy;
    
    public EnrollmentDetail(Enrollment enrollment, AccountRef participant, AccountRef enrolledBy, AccountRef withdrawnBy) {
        this.enrollment = enrollment;
        this.participant = participant;
        this.enrolledBy = enrolledBy;
        this.withdrawnBy = withdrawnBy;
    }

    public String getExternalId() {
        return enrollment.getExternalId();
    }
    public boolean isConsentRequired() {
        return enrollment.isConsentRequired();
    }
    public DateTime getEnrolledOn() {
        return enrollment.getEnrolledOn();
    }
    public DateTime getWithdrawnOn() {
        return enrollment.getWithdrawnOn();
    }
    public String getWithdrawalNote() {
        return enrollment.getWithdrawalNote();
    }
    public AccountRef getParticipant() {
        return participant;
    }
    public AccountRef getEnrolledBy() {
        return enrolledBy;
    }
    public AccountRef getWithdrawnBy() {
        return withdrawnBy;
    }
}
