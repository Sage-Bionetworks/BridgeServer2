package org.sagebionetworks.bridge.models.assessments;

import static javax.persistence.CascadeType.MERGE;
import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.FetchType.EAGER;
import static org.sagebionetworks.bridge.models.TagUtils.toTagSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import javax.persistence.Version;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.LabelListConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.hibernate.ColorSchemeConverter;
import org.sagebionetworks.bridge.hibernate.CustomizationFieldsConverter;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;

/**
 * Persistence object for a record about an assessment (task, survey, measure) in 
 * the Bridge system.
 */
@Entity
// This annotation is necessary so a constraint violation exception involving an 
// assessment displays the correct message without exposing the Hibernate implementation.
@BridgeTypeName("Assessment")
@Table(name = "Assessments")
public class HibernateAssessment {
    
    public static HibernateAssessment create(String appId, Assessment dto) {
        HibernateAssessment assessment = new HibernateAssessment();
        assessment.setAppId(appId);
        assessment.setGuid(dto.getGuid());
        assessment.setAppId(appId);
        assessment.setIdentifier(dto.getIdentifier());
        assessment.setRevision(dto.getRevision());
        assessment.setTitle(dto.getTitle());
        assessment.setSummary(dto.getSummary());
        assessment.setValidationStatus(dto.getValidationStatus());
        assessment.setNormingStatus(dto.getNormingStatus());
        assessment.setMinutesToComplete(dto.getMinutesToComplete());
        assessment.setOsName(dto.getOsName());
        assessment.setOriginGuid(dto.getOriginGuid());
        assessment.setOwnerId(dto.getOwnerId());
        assessment.setTags(toTagSet(dto.getTags()));
        assessment.setCustomizationFields(dto.getCustomizationFields());
        assessment.setColorScheme(dto.getColorScheme());
        assessment.getLabels().addAll(dto.getLabels());
        assessment.setCreatedOn(dto.getCreatedOn());
        assessment.setModifiedOn(dto.getModifiedOn());
        assessment.setDeleted(dto.isDeleted());
        assessment.setVersion(dto.getVersion());
        return assessment;
    }

    @Id
    private String guid;
    private String appId;
    private String identifier;
    private int revision = 1;
    private String title;
    private String summary;
    private String validationStatus;
    private String normingStatus;
    private Integer minutesToComplete;
    // same constants used in OperatingSystem
    private String osName;
    
    // For imported assessments, they retain a link to the assessment they
    // were copied from. The origin assessment will be in the shared 
    // assessment library. Every revision has a unique GUID to make it 
    // easier to query for these.
    private String originGuid;
    
    // In local apps this is the ID of an organization. In the shared context, 
    // this is an appId, ":", and an organization ID (e.g. "appId:orgId").
    private String ownerId;
    
    @ManyToMany(cascade = { MERGE, PERSIST }, fetch = EAGER)
    @JoinTable(name = "AssessmentTags",
        joinColumns = { @JoinColumn(name = "assessmentGuid") }, 
        inverseJoinColumns = { @JoinColumn(name = "tagValue")}
    )
    private Set<Tag> tags;
    
    @Convert(converter = CustomizationFieldsConverter.class)
    private Map<String,Set<PropertyInfo>> customizationFields;
    
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    
    @Convert(converter = ColorSchemeConverter.class)
    private ColorScheme colorScheme;
    
    @Column(columnDefinition = "text", name = "labels", nullable = true)
    @Convert(converter = LabelListConverter.class)
    private List<Label> labels;
    
    private boolean deleted;
    
    @Version
    private long version;

    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
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
        return this.minutesToComplete;
    }
    public void setMinutesToComplete(Integer minutesToComplete) {
        this.minutesToComplete = minutesToComplete;
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
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    public Set<Tag> getTags() {
        return tags;
    }
    public void setTags(Set<Tag> tags) {
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
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public int getRevision() {
        return revision;
    }
    public void setRevision(int revision) {
        this.revision = revision;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }    
}

