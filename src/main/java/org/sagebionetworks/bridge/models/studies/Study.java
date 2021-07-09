package org.sagebionetworks.bridge.models.studies;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.hibernate.HibernateStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonDeserialize(as=HibernateStudy.class)
@JsonFilter("filter")
public interface Study extends BridgeEntity {
    
    // For the summary view, we do suppress many of these fields...
    ObjectWriter STUDY_SUMMARY_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter", 
            SimpleBeanPropertyFilter.serializeAllExcept("appId", "clientData", 
                    "logoGuid", "irbName", "irbDecisionOn", "irbExpiresOn", 
                    "irbDecisionType", "irbProtocolName", "institutionId", 
                    "irbProtocolId", "scheduleGuid", "keywords", "diseases", 
                    "studyDesignTypes", "contacts", "deleted", "version")));

    
    public static Study create() {
        return new HibernateStudy();
    }

    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getAppId();
    void setAppId(String appId);
    
    String getName();
    void setName(String name);
    
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
    
    String getKeywords();
    void setKeywords(String keywords);
    
    Set<String> getDiseases();
    void setDiseases(Set<String> diseases);
    
    Set<String> getStudyDesignTypes();
    void setStudyDesignTypes(Set<String> studyDesignTypes);
    
    List<Contact> getContacts();
    void setContacts(List<Contact> contacts);
    
    String getLogoGuid();
    void setLogoGuid(String logoGuid);

    List<SignInType> getSignInTypes();
    void setSignInTypes(List<SignInType> signInTypes);

    Long getVersion();
    void setVersion(Long version);
}
