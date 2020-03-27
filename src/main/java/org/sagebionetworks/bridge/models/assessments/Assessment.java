package org.sagebionetworks.bridge.models.assessments;

import static org.sagebionetworks.bridge.models.TagUtils.toStringSet;

import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;

public class Assessment implements BridgeEntity {
    
    public static Assessment create(HibernateAssessment assessment) {
        Assessment dto = new Assessment();
        dto.setGuid(assessment.getGuid());
        dto.setIdentifier(assessment.getIdentifier());
        dto.setRevision(assessment.getRevision());
        dto.setTitle(assessment.getTitle());
        dto.setSummary(assessment.getSummary());
        dto.setValidationStatus(assessment.getValidationStatus());
        dto.setNormingStatus(assessment.getNormingStatus());
        dto.setOsName(assessment.getOsName());
        dto.setOriginGuid(assessment.getOriginGuid());
        dto.setOwnerId(assessment.getOwnerId());
        dto.setTags(toStringSet(assessment.getTags()));    
        dto.setCustomizationFields(assessment.getCustomizationFields());
        dto.setCreatedOn(assessment.getCreatedOn());
        dto.setModifiedOn(assessment.getModifiedOn());
        dto.setDeleted(assessment.isDeleted());
        dto.setVersion(assessment.getVersion());
        return dto;
    }
    
    public static Assessment copy(Assessment assessment) {
        return create(HibernateAssessment.create(assessment, null));
    }
    
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
    private Set<String> tags;
    private Map<String, Set<PropertyInfo>> customizationFields;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private boolean deleted;
    private long version;
    
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
    public Set<String> getTags() {
        return tags;
    }
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
    public Map<String, Set<PropertyInfo>> getCustomizationFields() {
        return customizationFields;
    }
    public void setCustomizationFields(Map<String, Set<PropertyInfo>> customizationFields) {
        this.customizationFields = customizationFields;
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
}
