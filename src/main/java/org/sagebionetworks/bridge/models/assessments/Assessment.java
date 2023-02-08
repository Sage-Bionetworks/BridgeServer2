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
        dto.setAge(assessment.getAge());
        dto.setLongDescription(assessment.getLongDescription());
        dto.setScores(assessment.getScores());
        dto.setReliability(assessment.getReliability());
        dto.setLanguage(assessment.getLanguage());
        dto.setCategory(assessment.getCategory());
        dto.setTechnicalManualUrl(assessment.getTechnicalManualUrl());
        dto.setPublicationUrls(assessment.getPublicationUrls());
        dto.setCaption(assessment.getCaption());
        dto.setVideoUrl(assessment.getVideoUrl());
        dto.setPhoneOrientation(assessment.getPhoneOrientation());
        dto.setSoundRequired(assessment.getSoundRequired());
        dto.setMultiPart(assessment.getMultiPart());
        dto.setAssessmentType(assessment.getAssessmentType());
        dto.setMetadataJsonSchemaUrl(assessment.getMetadataJsonSchemaUrl());
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
    private String age;
    private String longDescription;
    private String scores;
    private String reliability;
    private String language;
    private String category;
    private String technicalManualUrl;
    private List<String> publicationUrls;
    private String caption;
    private String videoUrl;
    private String phoneOrientation;
    private Boolean soundRequired;
    private Boolean multiPart;
    private String assessmentType;
    private String metadataJsonSchemaUrl;

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

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getLongDescription() {
        return longDescription;
    }

    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }

    public String getScores() {
        return scores;
    }

    public void setScores(String scores) {
        this.scores = scores;
    }

    public String getReliability() {
        return reliability;
    }

    public void setReliability(String reliability) {
        this.reliability = reliability;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTechnicalManualUrl() {
        return technicalManualUrl;
    }

    public void setTechnicalManualUrl(String technicalManualUrl) {
        this.technicalManualUrl = technicalManualUrl;
    }

    public List<String> getPublicationUrls() {
        return publicationUrls;
    }

    public void setPublicationUrls(List<String> publicationUrls) {
        this.publicationUrls = publicationUrls;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getPhoneOrientation() {
        return phoneOrientation;
    }

    public void setPhoneOrientation(String phoneOrientation) {
        this.phoneOrientation = phoneOrientation;
    }

    public Boolean getSoundRequired() {
        return soundRequired;
    }

    public void setSoundRequired(Boolean soundRequired) {
        this.soundRequired = soundRequired;
    }

    public Boolean getMultiPart() {
        return multiPart;
    }

    public void setMultiPart(Boolean multiPart) {
        this.multiPart = multiPart;
    }

    public String getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(String assessmentType) {
        this.assessmentType = assessmentType;
    }

    public String getMetadataJsonSchemaUrl() {
        return metadataJsonSchemaUrl;
    }

    public void setMetadataJsonSchemaUrl(String metadataJsonSchemaUrl) {
        this.metadataJsonSchemaUrl = metadataJsonSchemaUrl;
    }
}
