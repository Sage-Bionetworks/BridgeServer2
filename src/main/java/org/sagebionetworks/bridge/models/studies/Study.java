package org.sagebionetworks.bridge.models.studies;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.hibernate.HibernateStudy;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=HibernateStudy.class)
public interface Study extends BridgeEntity {
    
    public static Study create() {
        return new HibernateStudy();
    }

    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getAppId();
    void setAppId(String appId);
    
    String getName();
    void setName(String name);
    
    String getStudyStartEventId();
    void setStudyStartEventId(String studyStartEventId);
    
    String getDetails();
    void setDetails(String details);
    
    StudyPhase getPhase();
    void setPhase(StudyPhase phrase);
    
    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);
    
    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);
    
    JsonNode getClientData();
    void setClientData(JsonNode clientData);
    
    void setIrbName(String irbName);
    String getIrbName();
    
    void setIrbDecisionOn(LocalDate irbDecisionOn);
    LocalDate getIrbDecisionOn();
    
    void setIrbExpiresOn(LocalDate irbExpiresOn);
    LocalDate getIrbExpiresOn();
    
    void setIrbDecisionType(IrbDecisionType irbDecisionType);
    IrbDecisionType  getIrbDecisionType();
    
    String getIrbProtocolName();
    void setIrbProtocolName(String irbProtocolName); 
    
    String getStudyLogoUrl();
    void setStudyLogoUrl(String studyLogoUrl);
    
    ColorScheme getColorScheme();
    void setColorScheme(ColorScheme colorScheme);
    
    String getInstitutionId();
    void setInstitutionId(String institutionId);
    
    String getIrbProtocolId();
    void setIrbProtocolId(String irbProtocolId);

    String getScheduleGuid();
    void setScheduleGuid(String scheduleGuid);
    
    String getDisease();
    void setDisease(String disease);
    
    String getStudyDesignType();
    void setStudyDesignType(String studyDesignType);
    
    List<Contact> getContacts();
    void setContacts(List<Contact> contacts);

    Long getVersion();
    void setVersion(Long version);
    
}
