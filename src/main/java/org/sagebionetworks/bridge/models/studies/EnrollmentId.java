package org.sagebionetworks.bridge.models.studies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class EnrollmentId implements Serializable {

    @Column(name = "studyId")
    private String appId;

    @Column(name = "substudyId")
    private String studyId;
    
    @Column(name = "accountId")
    private String accountId;

    public EnrollmentId() {
    }
    public EnrollmentId(String appId, String studyId, String accountId) {
        this.appId = appId;
        this.studyId = studyId;
        this.accountId = accountId;
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

    @Override
    public int hashCode() {
        return Objects.hash(appId, studyId, accountId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        EnrollmentId other = (EnrollmentId) obj;
        return Objects.equals(appId, other.appId) &&
                Objects.equals(studyId, other.studyId) &&
                Objects.equals(accountId, other.accountId);
    }    
}
