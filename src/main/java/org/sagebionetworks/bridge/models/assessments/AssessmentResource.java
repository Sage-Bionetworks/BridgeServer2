package org.sagebionetworks.bridge.models.assessments;

import java.util.List;

import com.google.common.collect.Lists;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.BridgeEntity;

public class AssessmentResource implements BridgeEntity {
    
    public static AssessmentResource create(HibernateAssessmentResource resource) {
        AssessmentResource dto = new AssessmentResource();
        dto.setGuid(resource.getGuid());
        dto.setTitle(resource.getTitle());
        dto.setCategory(resource.getCategory());
        dto.setUrl(resource.getUrl());
        dto.setFormat(resource.getFormat());
        dto.setDate(resource.getDate());
        dto.setDescription(resource.getDescription());
        if (resource.getContributors() != null) {
            dto.setContributors(Lists.newArrayList(resource.getContributors()));    
        }
        if (resource.getCreators() != null) {
            dto.setCreators(Lists.newArrayList(resource.getCreators()));    
        }
        if (resource.getPublishers() != null) {
            dto.setPublishers(Lists.newArrayList(resource.getPublishers()));    
        }
        dto.setLanguage(resource.getLanguage());
        dto.setMinRevision(resource.getMinRevision());
        dto.setMaxRevision(resource.getMaxRevision());
        dto.setCreatedAtRevision(resource.getCreatedAtRevision());
        dto.setCreatedOn(resource.getCreatedOn());
        dto.setModifiedOn(resource.getModifiedOn());
        dto.setDeleted(resource.isDeleted());
        dto.setVersion(resource.getVersion());
        return dto;
    }

    private String guid;
    private String title;
    private ResourceCategory category;
    private String url;
    private String format;
    private String date;
    private String description;
    private List<String> contributors;
    private List<String> creators;
    private List<String> publishers;
    private String language;
    private Integer minRevision;
    private Integer maxRevision;
    private int createdAtRevision;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private boolean deleted;
    private long version;
    boolean upToDate;
    
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
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
    public boolean isUpToDate() {
        return upToDate;
    }
    public void setUpToDate(boolean upToDate) {
        this.upToDate = upToDate;
    }
}
