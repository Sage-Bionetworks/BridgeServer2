package org.sagebionetworks.bridge.models.surveys;

import java.util.List;

import org.sagebionetworks.bridge.hibernate.HibernateSurvey;
import org.sagebionetworks.bridge.hibernate.HibernateSurveyElement;
import org.sagebionetworks.bridge.hibernate.HibernateSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;

@BridgeTypeName("Survey")
@JsonDeserialize(as=HibernateSurvey.class)
public interface SurveySQL extends GuidCreatedOnVersionHolder, BridgeEntity  {
    static HibernateSurvey create() {
        return new HibernateSurvey();
    }

    String getStudyIdentifier();
    void setStudyIdentifier(String studyIdentifier);
    
    String getGuid();
    void setGuid(String guid);
    
    long getCreatedOn();
    void setCreatedOn(long createdOn);
    
    long getModifiedOn();
    void setModifiedOn(long modifiedOn);

    String getCopyrightNotice();
    void setCopyrightNotice(String copyrightNotice);

    /** Module ID, if this survey was imported from a shared module. */
    String getModuleId();

    /** @see #getModuleId */
    void setModuleId(String moduleId);

    /** Module version, if this survey was imported from a shared module. */
    Integer getModuleVersion();

    /** @see #getModuleVersion */
    void setModuleVersion(Integer moduleVersion);

    Long getVersion();
    void setVersion(Long version);
    
    String getName();
    void setName(String name);

    String getIdentifier();
    void setIdentifier(String identifier);

    boolean isPublished();
    void setPublished(boolean published);

    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    /**
     * Gets the upload schema revision that corresponds to this survey. See
     * {@link org.sagebionetworks.bridge.models.upload.UploadSchema#getRevision} for more details.
     */
    Integer getSchemaRevision();

    /** @see #getSchemaRevision */
    void setSchemaRevision(Integer schemaRevision);
    
    List<HibernateSurveyElement> getElements();
    void setElements(List<HibernateSurveyElement> elements);
    
    default List<HibernateSurveyQuestion> getUnmodifiableQuestionList() {
        ImmutableList.Builder<HibernateSurveyQuestion> builder = new ImmutableList.Builder<>();
        for (SurveyElementSQL element : getElements()) {
            if (SurveyElementConstants.SURVEY_QUESTION_TYPE.equals(element.getType())) {
                builder.add((HibernateSurveyQuestion) element);
            }
        }
        
        return builder.build();
    }
}
