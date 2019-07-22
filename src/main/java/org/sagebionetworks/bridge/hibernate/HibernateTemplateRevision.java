package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateRevisionId;

@Entity
@IdClass(TemplateRevisionId.class)
@Table(name = "TemplateRevisions")
public class HibernateTemplateRevision implements TemplateRevision {

    @Id
    private String templateGuid;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Id
    private DateTime createdOn;
    private String createdBy;
    private String storagePath;
    @Transient
    private String documentContent;
    @Enumerated(EnumType.STRING)
    private MimeType mimeType;
    private String subject;
    
    @Override
    @JsonIgnore
    public String getTemplateGuid() {
        return templateGuid;
    }
    @Override
    public void setTemplateGuid(String templateGuid) {
        this.templateGuid = templateGuid;
    }
    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }
    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    @Override
    public String getCreatedBy() {
        return createdBy;
    }
    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    @JsonIgnore
    @Override
    public String getStoragePath() {
        return storagePath;
    }
    @Override
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
    @Override
    public MimeType getMimeType() {
        return mimeType;
    }
    @Override
    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }
    @Override
    public String getSubject() {
        return subject;
    }
    @Override
    public void setSubject(String subject) {
        this.subject = subject;
    }
    @Override
    public String getDocumentContent() {
        return documentContent;
    }
    @Override
    public void setDocumentContent(String documentContent) {
        this.documentContent = documentContent;
    }
}
