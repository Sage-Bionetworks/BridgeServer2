package org.sagebionetworks.bridge.models.studies;

import static java.lang.Boolean.TRUE;

import java.util.Objects;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about an enrollment that we care to expose through the participant API.
 */
public final class EnrollmentInfo {
    
    public static final EnrollmentInfo create(Enrollment enrollment) {
        boolean ebs = enrollment.getAccountId().equals(enrollment.getEnrolledBy());
        boolean wbs = enrollment.getAccountId().equals(enrollment.getWithdrawnBy());
        return new EnrollmentInfo(enrollment.getExternalId(), 
                enrollment.getEnrolledOn(), 
                enrollment.getWithdrawnOn(), 
                (ebs == true) ? TRUE : null, 
                (wbs == true) ? TRUE : null, 
                enrollment.isConsentRequired());
    }

    private final String externalId;
    private final DateTime enrolledOn;
    private final DateTime withdrawnOn;
    private final Boolean enrolledBySelf;
    private final Boolean withdrawnBySelf;
    private final boolean consentRequired;
    
    @JsonCreator
    public EnrollmentInfo(@JsonProperty("externalId") String externalId,
            @JsonProperty("enrolledOn") DateTime enrolledOn,
            @JsonProperty("withdrawnOn") DateTime withdrawnOn,
            @JsonProperty("enrolledBySelf") Boolean enrolledBySelf,
            @JsonProperty("withdrawnBySelf") Boolean withdrawnBySelf,
            @JsonProperty("consentRequired") boolean consentRequired) {
        this.externalId = externalId;
        this.enrolledOn = enrolledOn;
        this.withdrawnOn = withdrawnOn;
        this.enrolledBySelf = enrolledBySelf;
        this.withdrawnBySelf = withdrawnBySelf;
        this.consentRequired = consentRequired;
    }
    public String getExternalId() {
        return externalId;
    }
    public DateTime getEnrolledOn() {
        return enrolledOn;
    }
    public Boolean isEnrolledBySelf() {
        return enrolledBySelf;
    }
    public DateTime getWithdrawnOn() {
        return withdrawnOn;
    }
    public Boolean isWithdrawnBySelf() {
        return withdrawnBySelf;
    }
    public boolean isConsentRequired() {
        return consentRequired;
    }
    @Override
    public int hashCode() {
        return Objects.hash(consentRequired, enrolledBySelf, enrolledOn, externalId, withdrawnBySelf, withdrawnOn);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EnrollmentInfo other = (EnrollmentInfo) obj;
        return consentRequired == other.consentRequired && Objects.equals(enrolledBySelf, other.enrolledBySelf)
                && Objects.equals(enrolledOn, other.enrolledOn) && Objects.equals(externalId, other.externalId)
                && Objects.equals(withdrawnBySelf, other.withdrawnBySelf)
                && Objects.equals(withdrawnOn, other.withdrawnOn);
    }
    
}