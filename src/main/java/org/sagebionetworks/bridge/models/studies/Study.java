package org.sagebionetworks.bridge.models.studies;

import org.joda.time.DateTime;
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
    
    boolean isDeleted();
    void setDeleted(boolean deleted);
    
    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);
    
    String getCreatedBy();
    void setCreatedBy(String createdBy);
    
    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);

    String getModifiedBy();
    void setModifiedBy(String modifiedBy);
    
    JsonNode getClientData();
    void setClientData(JsonNode clientData);
    
    String getDetails();
    void setDetails(String details);
    
    DateTime getLaunchedOn();
    void setLaunchedOn(DateTime launchedOn);

    String getLaunchedBy();
    void setLaunchedBy(String launchedBy);

    DateTime getCloseoutOn();
    void setCloseoutOn(DateTime closeoutOn);

    String getCloseoutBy();
    void setCloseoutBy(String closeoutBy);
    
    DateTime getIrbApprovedOn();
    void setIrbApprovedOn(DateTime irbApprovedOn);
    
    String getStudyLogoUrl();
    void setStudyLogoUrl(String studyLogoUrl);
    
    ColorScheme getColorScheme();
    void setColorScheme(ColorScheme colorScheme);
    
    String getExternalProtocolId();
    void setExternalProtocolId(String externalProtocolId);
    
    String getIrbProtocolId();
    void setIrbProtocolId(String irbProtocolId);

    String getScheduleGuid();
    void setScheduleGuid(String scheduleGuid);
    
    List<StudyContact> getContacts();
    void setContacts(List<StudyContact> contacts);
    
    Long getVersion();
    void setVersion(Long version);
}
