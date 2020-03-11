package org.sagebionetworks.bridge.models.assessments;

import java.util.List;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.StringListConverter;

/**
 * The table name here is generic because we can add an additional FK column and use it for another 
 * model that has documentation links. Eventually, that might include protocols, for example, 
 * if they are commonly shared in an app with multiple studies.
 */
@Entity
@Table(name = "ExternalResources")
public class HibernateAssessmentResource {
    
    public static HibernateAssessmentResource create(AssessmentResource resource, String appId, String assessmentId) {
        HibernateAssessmentResource model = new HibernateAssessmentResource();
        model.setAssessmentId(assessmentId);
        model.setGuid(resource.getGuid());
        model.setAppId(appId);
        model.setTitle(resource.getTitle());
        model.setCategory(resource.getCategory());
        model.setUrl(resource.getUrl());
        model.setFormat(resource.getFormat());
        model.setDate(resource.getDate());
        model.setDescription(resource.getDescription());
        if (resource.getContributors() != null) {
            model.setContributors(resource.getContributors());    
        }
        if (resource.getCreators() != null) {
            model.setCreators(resource.getCreators());    
        }
        if (resource.getPublishers() != null) {
            model.setPublishers(resource.getPublishers());    
        }
        model.setLanguage(resource.getLanguage());
        model.setMinRevision(resource.getMinRevision());
        model.setMaxRevision(resource.getMaxRevision());
        model.setCreatedAtRevision(resource.getCreatedAtRevision());
        model.setCreatedOn(resource.getCreatedOn());
        model.setModifiedOn(resource.getModifiedOn());
        model.setDeleted(resource.isDeleted());
        model.setVersion(resource.getVersion());
        return model;
    }
    
    @Id
    private String guid;
    private String appId;
    private String assessmentId;
    private String title;
    @Enumerated(EnumType.STRING)
    private ResourceCategory category;
    private String url;
    private String format;
    private String date;
    private String description;
    @Convert(converter = StringListConverter.class)
    private List<String> contributors;
    @Convert(converter = StringListConverter.class)
    private List<String> creators;
    @Convert(converter = StringListConverter.class)
    private List<String> publishers;
    private String language;
    private Integer minRevision;
    private Integer maxRevision;
    private int createdAtRevision;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
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
    public String getAssessmentId() {
        return assessmentId;
    }
    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public ResourceCategory getCategory() {
        return category;
    }
    public void setCategory(ResourceCategory category) {
        this.category = category;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getFormat() {
        return format;
    }
    public void setFormat(String format) {
        this.format = format;
    }
    public String getDate() {
        return date;
    }
    public void setDate(String date) {
        this.date = date;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public List<String> getContributors() {
        return contributors;
    }
    public void setContributors(List<String> contributors) {
        this.contributors = contributors;
    }
    public List<String> getCreators() {
        return creators;
    }
    public void setCreators(List<String> creators) {
        this.creators = creators;
    }
    public List<String> getPublishers() {
        return publishers;
    }
    public void setPublishers(List<String> publishers) {
        this.publishers = publishers;
    }
    public String getLanguage() {
        return language;
    }
    public void setLanguage(String language) {
        this.language = language;
    }
    public Integer getMinRevision() {
        return minRevision;
    }
    public void setMinRevision(Integer minRevision) {
        this.minRevision = minRevision;
    }
    public Integer getMaxRevision() {
        return maxRevision;
    }
    public void setMaxRevision(Integer maxRevision) {
        this.maxRevision = maxRevision;
    }
    public int getCreatedAtRevision() {
        return createdAtRevision;
    }
    public void setCreatedAtRevision(int createdAtRevision) {
        this.createdAtRevision = createdAtRevision;
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
