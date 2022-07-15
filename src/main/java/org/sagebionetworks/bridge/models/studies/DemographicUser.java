package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.sagebionetworks.bridge.json.DemographicCollectionDeserializer;
import org.sagebionetworks.bridge.json.DemographicCollectionSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Entity
@Table(name = "DemographicsUsers")
// @JsonSerialize(using = DemographicCollectionSerializer.class)
// @JsonDeserialize(using = DemographicCollectionDeserializer.class)
public class DemographicUser {
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
    @Nonnull
    // @OneToMany(mappedBy = "demographicUserIdFoo")
    // @OneToMany
    // @JoinColumn(name = "demographicUserId")
    private List<Demographic> demographics;

    // public DemographicUser(String id, String appId, String studyId, String userId, List<Demographic> demographics) {
    //     this.id = id;
    //     this.appId = appId;
    //     this.studyId = studyId;
    //     this.userId = userId;
    //     this.demographics = demographics;
    // }

    public DemographicUser() {
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

    // public List<Demographic> getDemographics() {
    //     return demographics;
    // }

    // public void setDemographics(List<Demographic> demographics) {
    //     this.demographics = demographics;
    // }

    @Override
    public String toString() {
        return "DemographicUser [appId=" + appId + ", demographics=" + demographics + ", id=" + id + ", studyId="
                + studyId + ", userId=" + userId + "]";
    }
}
