package org.sagebionetworks.bridge.models.studies;

import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapKey;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents all demographics for a single user at either the app or study
 * level. Study-level demographics have studyId=null. Contains multiple
 * Demographics.
 */
@Entity
@Table(name = "DemographicsUsers")
public class DemographicUser implements BridgeEntity {
    @Id
    @JsonIgnore
    private String id;

    @JsonIgnore
    private String appId;

    @JsonIgnore
    private String studyId;

    private String userId;

    // use Map for easy JSON serialization
    @OneToMany(mappedBy = "demographicUser", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @MapKey(name = "categoryName")
    private Map<String, Demographic> demographics;

    public DemographicUser() {
    }

    public DemographicUser(String id, String appId, String studyId, String userId,
            Map<String, Demographic> demographics) {
        this.id = id;
        this.appId = appId;
        this.studyId = studyId;
        this.userId = userId;
        this.demographics = demographics;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, Demographic> getDemographics() {
        return demographics;
    }

    @JsonProperty("demographics")
    public void setDemographics(Map<String, Demographic> demographics) {
        if (demographics != null) {
            for (Map.Entry<String, Demographic> entry : demographics.entrySet()) {
                if (entry.getValue() != null) {
                    entry.getValue().setCategoryName(entry.getKey());
                }
            }
        }
        this.demographics = demographics;
    }

    @Override
    public String toString() {
        return "DemographicUser [appId=" + appId + ", demographics=" + demographics + ", id=" + id + ", studyId="
                + studyId + ", userId=" + userId + "]";
    }
}
