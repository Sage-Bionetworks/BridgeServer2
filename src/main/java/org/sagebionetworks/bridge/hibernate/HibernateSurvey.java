package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyElementConstants;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;

import com.google.common.collect.ImmutableList;

/** MySQL implementation of surveys via Hibernate. */
@Entity
@Table(name = "Surveys")
public class HibernateSurvey implements Survey {
    
    private String studyKey;
    private String guid;
    private long createdOn;
    private long modifiedOn;
    private String copyrightNotice;
    private String moduleId;
    private Integer moduleVersion;
    private Long version;
    private String name;
    private String identifier;
    private boolean published;
    private boolean deleted;
    private Integer schemaRevision;
    private List<SurveyElement> elements;
    
    /**
     * No args constructor, required and used by Hibernate for full object initialization.
     */
    public HibernateSurvey() {}
    
    /** Study ID the survey lives in. */
    public String getStudyIdentifier() {
        return studyKey;
    }
    
    /** @see #getStudyIdentifier */
    public void setStudyIdentifier(String studyIdentifier) {
        this.studyKey = studyIdentifier;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public void setGuid(String guid) {
        this.guid = guid;
    }
    
    /** The ISO 8601 date on which this version of this survey was created. */
    public long getCreatedOn() {
        return createdOn;
    }
    
    /** @see #getCreatedOn */
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
    
    /** The date and time when this version of the survey was last modified. */
    public long getModifiedOn() {
        return modifiedOn;
    }
    
    /** @see #getModifiedOn */
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    /** A copyright notice identifying the owners of the published work. */
    public String getCopyrightNotice() {
        return copyrightNotice;
    }
    
    /** @see #getCopyrightNotice */
    public void setCopyrightNotice(String copyrightNotice) {
        this.copyrightNotice = copyrightNotice;
    }
    
    /** Module ID, if this survey was imported from a shared module. */
    public String getModuleId() {
        return moduleId;
    }
    
    /** @see #getModuleId */
    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }
    
    /** Module version, if this survey was imported from a shared module. */
    public Integer getModuleVersion() {
        return moduleVersion;
    }
    
    /** @see #getModuleVersion */
    public void setModuleVersion(Integer moduleVersion) {
        this.moduleVersion = moduleVersion;
    }
    
    /** The version of this survey as used to implement optimistic locking. */
    public Long getVersion() {
        return version;
    }
    
    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }
    
    /** The name of this survey. The name can be changed after creation, 
     * and should not appear in the UI. */
    public String getName() {
        return name;
    }
    
    /** @see #getName */
    public void setName(String name) {
        this.name = name;
    }
    
    /** */
    public String getIdentifier() {
        return identifier;
    }
    
    /** @see #getIdentifier */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    /** True if this survey revision has been published, and is accessible to 
     * users through scheduling. */
    public boolean isPublished() {
        return published;
    }
    
    /** @see #isPublished */
    public void setPublished(boolean published) {
        this.published = published;
    }
    
    /** Has this survey been logically deleted (an admin can restore it)? */
    public boolean isDeleted() {
        return deleted;
    }
    
    /** @see #isDeleted */
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    
    /**
     * Gets the upload schema revision that corresponds to this survey. See
     * {@link org.sagebionetworks.bridge.models.upload.UploadSchema#getRevision} for more details.
     */
    public Integer getSchemaRevision() {
        return schemaRevision;
    }
    
    /** @see #getSchemaRevision */
    public void setSchemaRevision(Integer schemaRevision) {
        this.schemaRevision = schemaRevision;
    }
    
    /** An ordered collection of SurveyElement sub-types (in the order they will 
     * appear in the survey). */
    @CollectionTable(name = "SurveyElements", joinColumns = @JoinColumn(name = "surveyGuid", referencedColumnName = "guid"))
    @Column(name = "elements")
    @ElementCollection(fetch = FetchType.EAGER)
    public List<SurveyElement> getElements() {
        return elements;
    }
    
    /** @see #getElements */
    public void setElements(List<SurveyElement> elements) {
        this.elements = elements;
    }
    
    /** */
    public List<SurveyQuestion> getUnmodifiableQuestionList() {
        ImmutableList.Builder<SurveyQuestion> builder = new ImmutableList.Builder<>();
        for (SurveyElement element : elements) {
            if (SurveyElementConstants.SURVEY_QUESTION_TYPE.equals(element.getType())) {
                builder.add((SurveyQuestion) element);
            }
        }
        return builder.build();
    }
    
    public boolean keysEqual(GuidCreatedOnVersionHolder keys) {
        return (keys != null && keys.getGuid().equals(guid) && keys.getCreatedOn() == createdOn);
    }
}
