package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.studies.Demographic;

@DynamoDBTable(tableName = "ParticipantVersion")
public class DynamoParticipantVersion implements ParticipantVersion {
    private String appId;
    private String healthCode;
    private int participantVersion;
    private long createdOn;
    private long modifiedOn;
    private Set<String> dataGroups;
    private List<String> languages;
    private SharingScope sharingScope;
    private Map<String, String> studyMemberships;
    private String timeZone;
    private Map<String, Demographic> appDemographics;
    private Map<String, Map<String, Demographic>> studyDemographics;
    private Long version;

    /**
     * This is the DynamoDB key. It is used by the DynamoDB mapper. This should not be used directly. The key format is
     * "[appID]:[healthCode]".
     */
    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() {
        if (StringUtils.isBlank(appId)) {
            // No appId means we can't generate a key. However, we should still return null, because this case might
            // still come up (such as querying by secondary index), and we don't want to crash.
            return null;
        }
        if (StringUtils.isBlank(healthCode)) {
            // Similarly here.
            return null;
        }
        return appId + ':' + healthCode;
    }

    /**
     * Sets the DynamoDB key. This is generally only called by the DynamoDB mapper. If the key is null, empty, or
     * malformatted, this will do nothing.
     */
    public void setKey(String key) {
        if (key != null) {
            String[] parts = key.split(":", 2);
            if (parts.length == 2) {
                this.appId = parts[0];
                this.healthCode = parts[1];
            }
        }
    }

    @DynamoDBIndexHashKey(attributeName = "appId", globalSecondaryIndexName = "appId-index")
    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @Override
    public String getHealthCode() {
        return healthCode;
    }

    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @DynamoDBRangeKey
    @Override
    public int getParticipantVersion() {
        return participantVersion;
    }

    @Override
    public void setParticipantVersion(int participantVersion) {
        this.participantVersion = participantVersion;
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getCreatedOn() {
        return createdOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }

    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getModifiedOn() {
        return modifiedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    @Override
    public Set<String> getDataGroups() {
        return dataGroups;
    }

    @Override
    public void setDataGroups(Set<String> dataGroups) {
        // DDB doesn't support empty collections, use null for empty collections.
        this.dataGroups = dataGroups != null && !dataGroups.isEmpty() ? dataGroups : null;
    }

    @Override
    public List<String> getLanguages() {
        return languages;
    }

    @Override
    public void setLanguages(List<String> languages) {
        // DDB doesn't support empty collections, use null for empty collections.
        this.languages = languages != null && !languages.isEmpty() ? languages : null;
    }

    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public SharingScope getSharingScope() {
        return sharingScope;
    }

    @Override
    public void setSharingScope(SharingScope sharingScope) {
        this.sharingScope = sharingScope;
    }

    @Override
    public Map<String, String> getStudyMemberships() {
        return studyMemberships;
    }

    @Override
    public void setStudyMemberships(Map<String, String> studyMemberships) {
        // DDB doesn't support empty collections, use null for empty collections.
        this.studyMemberships = studyMemberships != null && !studyMemberships.isEmpty() ? studyMemberships : null;
    }

    @Override
    public String getTimeZone() {
        return timeZone;
    }

    @Override
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    @Override
    @DynamoDBTypeConverted(converter = AppDemographicsMapMarshaller.class)
    public Map<String, Demographic> getAppDemographics() {
        return appDemographics;
    }

    @Override
    public void setAppDemographics(Map<String, Demographic> appDemographics) {
        this.appDemographics = appDemographics;
        if (appDemographics == null || appDemographics.isEmpty()) {
            // DDB doesn't support empty collections, use null for empty collections.
            this.appDemographics = null;
        }
    }

    @Override
    @DynamoDBTypeConverted(converter = StudyDemographicsMapMarshaller.class)
    public Map<String, Map<String, Demographic>> getStudyDemographics() {
        return studyDemographics;
    }

    @Override
    public void setStudyDemographics(Map<String, Map<String, Demographic>> studyDemographics) {
        this.studyDemographics = studyDemographics;
        if (studyDemographics == null || studyDemographics.isEmpty()) {
            // DDB doesn't support empty collections, use null for empty collections.
            this.studyDemographics = null;
        }
    }

    /**
     * DynamoDB version. Since this table is append only, this will always be 1. This is mostly here to protect against
     * concurrent modification.
     */
    @DynamoDBVersionAttribute
    @JsonIgnore
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
