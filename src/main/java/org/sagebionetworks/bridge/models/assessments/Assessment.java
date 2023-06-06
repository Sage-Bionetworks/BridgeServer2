package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.models.TagUtils.toStringSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;

import com.fasterxml.jackson.databind.JsonNode;

public class Assessment implements BridgeEntity {
    
    public static Assessment create(HibernateAssessment assessment) {
        Assessment dto = new Assessment();
        dto.setAppId(assessment.getAppId());
        dto.setGuid(assessment.getGuid());
        dto.setIdentifier(assessment.getIdentifier());
        dto.setRevision(assessment.getRevision());
        dto.setTitle(assessment.getTitle());
        dto.setSummary(assessment.getSummary());
        dto.setValidationStatus(assessment.getValidationStatus());
        dto.setNormingStatus(assessment.getNormingStatus());
        dto.setMinutesToComplete(assessment.getMinutesToComplete());
        dto.setOsName(assessment.getOsName());
        dto.setOriginGuid(assessment.getOriginGuid());
        dto.setOwnerId(assessment.getOwnerId());
        dto.setTags(toStringSet(assessment.getTags()));    
        dto.setCustomizationFields(assessment.getCustomizationFields());
        dto.setColorScheme(assessment.getColorScheme());
        dto.setLabels(assessment.getLabels());
        dto.setCreatedOn(assessment.getCreatedOn());
        dto.setModifiedOn(assessment.getModifiedOn());
        dto.setDeleted(assessment.isDeleted());
        dto.setVersion(assessment.getVersion());
        dto.setImageResource(assessment.getImageResource());
        dto.setFrameworkIdentifier(assessment.getFrameworkIdentifier());
        dto.setJsonSchemaUrl(assessment.getJsonSchemaUrl());
        dto.setCategory(assessment.getCategory());
        dto.setMinAge(assessment.getMinAge());
        dto.setMaxAge(assessment.getMaxAge());
        dto.setAdditionalMetadata(assessment.getAdditionalMetadata());
        return dto;
    }
    
    public static Assessment copy(Assessment assessment) {
        return create(HibernateAssessment.create(null, assessment));
    }
    
    private String appId;
    private String guid;
    private String identifier;
    private int revision = 1;
    private String ownerId;
    private String title;
    private String summary;
    private String osName;
    private String originGuid;
    private String validationStatus;
    private String normingStatus;
    private Integer minutesToComplete;
    private Set<String> tags;
    private Map<String, Set<PropertyInfo>> customizationFields;
    private ColorScheme colorScheme;
    private List<Label> labels;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private boolean deleted;
    private long version;
    private ImageResource imageResource;
    private String frameworkIdentifier;
    private String jsonSchemaUrl;
    private String category;
    private Integer minAge;
    private Integer maxAge;
    private JsonNode additionalMetadata;

    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public int getRevision() {
        return revision;
    }
    public void setRevision(int revision) {
        this.revision = revision;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getSummary() {
        return summary;
    }
    public void setSummary(String summary) {
        this.summary = summary;
    }
    public String getOsName() {
        return osName;
    }
    public void setOsName(String os) {
        this.osName = os;
    }
    public String getOriginGuid() {
        return originGuid;
    }
    public void setOriginGuid(String originGuid) {
        this.originGuid = originGuid;
    }
    public String getValidationStatus() {
        return validationStatus;
    }
    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }
    public String getNormingStatus() {
        return normingStatus;
    }
    public void setNormingStatus(String normingStatus) {
        this.normingStatus = normingStatus;
    }
    public Integer getMinutesToComplete() { 
        return minutesToComplete;
    }
    public void setMinutesToComplete(Integer minutesToComplete) {
        this.minutesToComplete = minutesToComplete;
    }
    public Set<String> getTags() {
        return tags;
    }
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
    public Map<String, Set<PropertyInfo>> getCustomizationFields() {
        if (customizationFields == null) {
            customizationFields = new HashMap<>();
        }
        return customizationFields;
    }
    public void setCustomizationFields(Map<String, Set<PropertyInfo>> customizationFields) {
        this.customizationFields = customizationFields;
    }
    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }
    public List<Label> getLabels() {
        if (labels == null) {
            labels = new ArrayList<>();
        }
        return labels;
    }
    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
    public ImageResource getImageResource() {
        return imageResource;
    }
    public void setImageResource(ImageResource imageResource) {
        this.imageResource = imageResource;
    }

    public String getFrameworkIdentifier() {
        return frameworkIdentifier;
    }

    public void setFrameworkIdentifier(String frameworkIdentifier) {
        this.frameworkIdentifier = frameworkIdentifier;
    }

    public String getJsonSchemaUrl() {
        return jsonSchemaUrl;
    }

    public void setJsonSchemaUrl(String jsonSchemaUrl) {
        this.jsonSchemaUrl = jsonSchemaUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public JsonNode getAdditionalMetadata() {
        return additionalMetadata;
    }

    public void setAdditionalMetadata(JsonNode additionalMetadata) {
        this.additionalMetadata = additionalMetadata;
    }
}
