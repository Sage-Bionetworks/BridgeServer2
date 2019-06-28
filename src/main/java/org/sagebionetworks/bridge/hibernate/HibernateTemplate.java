package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.Template;
import org.sagebionetworks.bridge.models.TemplateType;

@Entity
@Table(name = "Templates")
public final class HibernateTemplate implements Template {

    private String guid;
    private String studyId;
    private TemplateType type;
    private String name;
    private String description;
    private Criteria criteria;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private DateTime publishedCreatedOn;
    private boolean deleted;
    private int version;
    
    @JsonIgnore
    @Override
    public String getStudyId() { 
        return studyId;
    }
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    @Id
    @Override
    public String getGuid() {
        return guid;
    }
    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @Override
    @Enumerated(EnumType.STRING)
    public TemplateType getTemplateType() {
        return type;
    }
    @Override
    public void setTemplateType(TemplateType type) {
        this.type = type;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getDescription() {
        return description;
    }
    @Override
    public void setDescription(String description) {
        this.description = description;
    }
    @Transient
    @Override
    public Criteria getCriteria() {
        return criteria;
    }
    @Override
    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }
    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    @Override
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getPublishedCreatedOn() {
        return publishedCreatedOn;
    }
    @Override
    public void setPublishedCreatedOn(DateTime publishedCreatedOn) {
        this.publishedCreatedOn = publishedCreatedOn;
    }
    @Override
    public boolean isDeleted() {
        return deleted;
    }
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @Version
    @Override
    public int getVersion() {
        return version;
    }
    @Override
    public void setVersion(int version) {
        this.version = version;
    }
}
