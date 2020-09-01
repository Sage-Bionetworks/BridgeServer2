package org.sagebionetworks.bridge.hibernate;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentId;

@Entity
@Table(name = "AccountsSubstudies")
@IdClass(EnrollmentId.class)
@BridgeTypeName("Enrollment")
public final class HibernateEnrollment implements Enrollment {

    @Id
    @Column(name = "studyId")
    @JsonIgnore
    private String appId;
    @Id
    @Column(name = "substudyId")
    @JsonIgnore
    private String studyId;
    @Id
    @JoinColumn(name = "account_id")
    private String accountId;
    private String externalId;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime enrolledOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime withdrawnOn;
    private String enrolledBy;
    private String withdrawnBy;
    private String withdrawalNote;
    private boolean consentRequired;
    
    // Needed for Hibernate, or else you have to create an instantiation helper class
    public HibernateEnrollment() {
    }
    
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
    public String getAccountId() {
        return accountId;
    }
    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    @Override
    public int hashCode() {
        return Objects.hash(accountId, externalId, appId, studyId, enrolledOn, 
                withdrawnOn, enrolledBy, withdrawnBy, withdrawalNote, consentRequired);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        HibernateEnrollment other = (HibernateEnrollment) obj;
        return Objects.equals(accountId, other.accountId) && 
               Objects.equals(externalId, other.externalId) && 
               Objects.equals(appId, other.appId) && 
               Objects.equals(studyId, other.studyId) &&
               Objects.equals(enrolledOn, other.enrolledOn) &&
               Objects.equals(withdrawnOn, other.withdrawnOn) &&
               Objects.equals(enrolledBy, other.enrolledBy) &&
               Objects.equals(withdrawnBy, other.withdrawnBy) &&
               Objects.equals(withdrawalNote, other.withdrawalNote) &&
               Objects.equals(consentRequired, other.consentRequired);
    }

    @Override
    public String toString() {
        return "HibernateEnrollment [appId=" + appId + ", studyId=" + studyId + ", accountId=" 
                + accountId + ", externalId=" + externalId + ", enrolledOn=" + enrolledOn 
                + ", withdrawnOn=" + withdrawnOn + ", enrolledBy=" + enrolledBy 
                + ", withdrawnBy=" + withdrawnBy + ", withdrawalNote=" + withdrawalNote 
                + ", consentRequired=" + consentRequired + "]";
    }
}
