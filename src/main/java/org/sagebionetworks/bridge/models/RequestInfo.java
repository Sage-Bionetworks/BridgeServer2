package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.hibernate.ClientInfoConverter;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.DateTimeZoneAttributeConverter;
import org.sagebionetworks.bridge.hibernate.StringListConverter;
import org.sagebionetworks.bridge.hibernate.StringSetConverter;
import org.sagebionetworks.bridge.hibernate.StudyIdentifierConverter;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;


/**
 * Information about the criteria and access times of requests from a specific user. Useful for 
 * support and troubleshooting, and potentially useful to show the filtering that is occurring on 
 * the server to researchers in the researcher UI.
 */
@Entity
@Table(name = "RequestInfos")
@JsonDeserialize(builder = RequestInfo.Builder.class)
public final class RequestInfo {

    private String userId;
    private ClientInfo clientInfo;
    private String userAgent;
    private List<String> languages;
    private Set<String> userDataGroups;
    private Set<String> userSubstudyIds;
    private DateTime activitiesAccessedOn;
    private DateTime signedInOn;
    private DateTime uploadedOn;
    private DateTimeZone timeZone;
    private StudyIdentifier studyIdentifier;

    @Id
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Convert(converter = ClientInfoConverter.class)
    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
    public void setClientInfo(ClientInfo clientInfo) {
        this.clientInfo = clientInfo;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Convert(converter = StringListConverter.class)
    public List<String> getLanguages() {
        return languages;
    }
    
    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    @Convert(converter = StringSetConverter.class)
    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }
    
    public void setUserDataGroups(Set<String> userDataGroups) {
        this.userDataGroups = userDataGroups;
    }

    @Convert(converter = StringSetConverter.class)
    public Set<String> getUserSubstudyIds() {
        return userSubstudyIds;
    }
    
    public void setUserSubstudyIds(Set<String> userSubstudyIds) {
        this.userSubstudyIds = userSubstudyIds;
    }
    
    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    public DateTime getActivitiesAccessedOn() {
        return (activitiesAccessedOn == null) ? null : activitiesAccessedOn.withZone(timeZone);
    }
    
    public void setActivitiesAccessedOn(DateTime activitiesAccessedOn) {
        this.activitiesAccessedOn = activitiesAccessedOn;
    }

    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    public DateTime getSignedInOn() {
        return (signedInOn == null) ? null : signedInOn.withZone(timeZone);
    }
    
    public void setSignedInOn(DateTime signedInOn) {
        this.signedInOn = signedInOn;
    }
    
    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    public DateTime getUploadedOn() {
        return (uploadedOn == null) ? null : uploadedOn.withZone(timeZone);
    }
    
    public void setUploadedOn(DateTime uploadedOn) {
        this.uploadedOn = uploadedOn;
    }
    
    @Convert(converter = DateTimeZoneAttributeConverter.class)
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
    
    public void setTimeZone(DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }
    
    @Convert(converter = StudyIdentifierConverter.class)
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }
    
    public void setStudyIdentifier(StudyIdentifier studyIdentifier) {
        this.studyIdentifier = studyIdentifier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getActivitiesAccessedOn(), clientInfo, userAgent, languages, getSignedInOn(),
                userDataGroups, userSubstudyIds, userId, timeZone, uploadedOn, studyIdentifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        RequestInfo other = (RequestInfo) obj;
        return Objects.equals(getActivitiesAccessedOn(), other.getActivitiesAccessedOn()) &&
               Objects.equals(clientInfo, other.clientInfo) &&
               Objects.equals(userAgent, other.userAgent) &&
               Objects.equals(languages, other.languages) &&
               Objects.equals(getSignedInOn(), other.getSignedInOn()) && 
               Objects.equals(getUploadedOn(), other.getUploadedOn()) &&
               Objects.equals(userDataGroups, other.userDataGroups) && 
               Objects.equals(userSubstudyIds, other.userSubstudyIds) && 
               Objects.equals(userId, other.userId) && 
               Objects.equals(timeZone, other.timeZone) && 
               Objects.equals(studyIdentifier, other.studyIdentifier);
    }
    
    @Override
    public String toString() {
        return "RequestInfo [userId=" + userId + ", userAgent=" + userAgent + ", languages=" + languages
                + ", userDataGroups=" + userDataGroups + ", userSubstudyIds=" + userSubstudyIds 
                + ", activitiesAccessedOn=" + getActivitiesAccessedOn() + ", signedInOn=" + getSignedInOn() 
                + ", uploadedOn=" + getUploadedOn() + ", timeZone=" + timeZone + ", studyIdentifier=" 
                + studyIdentifier + "]";
    }

    public static class Builder {
        private String userId;
        private ClientInfo clientInfo;
        private String userAgent;
        private List<String> languages;
        private Set<String> userDataGroups;
        private Set<String> userSubstudyIds;
        private DateTime activitiesAccessedOn;
        private DateTime signedInOn;
        private DateTime uploadedOn;
        private DateTimeZone timeZone = DateTimeZone.UTC;
        private StudyIdentifier studyIdentifier;

        public Builder copyOf(RequestInfo requestInfo) {
            if (requestInfo != null) {
                withUserId(requestInfo.getUserId());
                withClientInfo(requestInfo.getClientInfo());
                withUserAgent(requestInfo.getUserAgent());
                withLanguages(requestInfo.getLanguages());
                withUserDataGroups(requestInfo.getUserDataGroups());
                withUserSubstudyIds(requestInfo.getUserSubstudyIds());
                withActivitiesAccessedOn(requestInfo.getActivitiesAccessedOn());
                withSignedInOn(requestInfo.getSignedInOn());
                withUploadedOn(requestInfo.getUploadedOn());
                withTimeZone(requestInfo.getTimeZone());
                withStudyIdentifier(requestInfo.getStudyIdentifier());
            }
            return this;
        }
        public Builder withUserId(String userId) {
            if (userId != null) {
                this.userId = userId;
            }
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            if (clientInfo != null) {
                this.clientInfo = clientInfo;
            }
            return this;
        }
        public Builder withUserAgent(String userAgent) {
            if (userAgent != null) {
                this.userAgent = userAgent;
            }
            return this;
        }
        public Builder withLanguages(List<String> languages) {
            if (languages != null) {
                this.languages = languages;
            }
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            if (userDataGroups != null) {
                this.userDataGroups = userDataGroups;
            }
            return this;
        }
        public Builder withUserSubstudyIds(Set<String> userSubstudyIds) {
            if (userSubstudyIds != null) {
                this.userSubstudyIds = userSubstudyIds;
            }
            return this;
        }
        public Builder withActivitiesAccessedOn(DateTime activitiesAccessedOn) {
            if (activitiesAccessedOn != null) {
                this.activitiesAccessedOn = activitiesAccessedOn;
            }
            return this;
        }
        public Builder withSignedInOn(DateTime signedInOn) {
            if (signedInOn != null) {
                this.signedInOn = signedInOn;
            }
            return this;
        }
        public Builder withUploadedOn(DateTime uploadedOn) {
            if (uploadedOn != null) {
                this.uploadedOn = uploadedOn;
            }
            return this;
        }
        public Builder withTimeZone(DateTimeZone timeZone) {
            if (timeZone != null) {
                this.timeZone = timeZone;
            }
            return this;
        }
        public Builder withStudyIdentifier(StudyIdentifier studyIdentifier) {
            if (studyIdentifier != null) {
                this.studyIdentifier = studyIdentifier;
            }
            return this;
        }
        
        public RequestInfo build() {
            RequestInfo info = new RequestInfo();
            info.setUserId(userId);
            info.setClientInfo(clientInfo);
            info.setUserAgent(userAgent);
            info.setLanguages(languages);
            info.setUserDataGroups(userDataGroups);
            info.setUserSubstudyIds(userSubstudyIds);
            info.setActivitiesAccessedOn(activitiesAccessedOn);
            info.setSignedInOn(signedInOn);
            info.setUploadedOn(uploadedOn);
            info.setTimeZone(timeZone);
            info.setStudyIdentifier(studyIdentifier);
            return info;
        }
    }
    
}
