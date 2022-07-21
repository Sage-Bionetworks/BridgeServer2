package org.sagebionetworks.bridge.models.studies;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapKey;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.sagebionetworks.bridge.json.DemographicUserDeserializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Entity
@Table(name = "DemographicsUsers")
@JsonDeserialize(using = DemographicUserDeserializer.class)
public class DemographicUser implements BridgeEntity {
    @Nonnull
    @Id
    @JsonIgnore
    private String id;

    @Nonnull
    @JsonIgnore
    private String appId;

    @Nullable
    @JsonIgnore
    private String studyId;

    @Nonnull
    private String userId;

    // use Map for easy JSON serialization
    @Nonnull
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

    public void setDemographics(Map<String, Demographic> demographics) {
        this.demographics = demographics;
    }

    @Override
    public String toString() {
        return "DemographicUser [appId=" + appId + ", demographics=" + demographics + ", id=" + id + ", studyId="
                + studyId + ", userId=" + userId + "]";
    }
}
