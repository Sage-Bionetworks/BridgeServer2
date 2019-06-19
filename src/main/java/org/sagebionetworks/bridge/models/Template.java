package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.HibernateTemplate;
import org.sagebionetworks.bridge.json.BridgeTypeName;

@BridgeTypeName("Template")
@JsonDeserialize(as=HibernateTemplate.class)
public interface Template extends BridgeEntity {
    
    public static Template create() {
        return new HibernateTemplate();
    }
    
    String getStudyId();
    void setStudyId(String studyId);

    String getGuid();
    void setGuid(String guid);
    
    TemplateType getTemplateType();
    void setTemplateType(TemplateType type);

    String getName();
    void setName(String name);
    
    String getDescription();
    void setDescription(String description);

    Criteria getCriteria();
    void setCriteria(Criteria criteria);

    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);

    DateTime getModifiedOn();
    void setModifiedOn(DateTime modifiedOn);

    DateTime getPublishedCreatedOn();
    void setPublishedCreatedOn(DateTime publishedCreatedOn);

    boolean isDeleted();
    void setDeleted(boolean deleted);

    Long getVersion();
    void setVersion(Long version);
}
