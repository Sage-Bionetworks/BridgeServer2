package org.sagebionetworks.bridge.hibernate;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentId;

@Entity
@Table(name = "AccountsSubstudies")
@IdClass(EnrollmentId.class)
public final class HibernateEnrollment implements Enrollment {

    @Id
    private String appId;
    @Id
    private String studyId;
    @Id
    @JoinColumn(name = "account_id")
    private String accountId;
    private String externalId;
    
    // Needed for Hibernate, or else you have to create an instantiation helper class
    public HibernateEnrollment() {
    }
    
    public HibernateEnrollment(String appId, String studyId, String accountId, String externalId) {
        this.appId = appId;
        this.studyId = studyId;
        this.accountId = accountId;
        this.externalId = externalId;
    }
    
    public String getAppId() {
        return appId;
    }
    public String getStudyId() {
        return studyId;
    }
    public String getAccountId() {
        return accountId;
    }
    public String getExternalId() {
        return externalId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, externalId, appId, studyId);
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
               Objects.equals(studyId, other.studyId);
    }

    @Override
    public String toString() {
        return "HibernateEnrollment [appId=" + appId + ", studyId=" + studyId + ", accountId="
                + accountId + ", externalId=" + externalId + "]";
    }
}
