package org.sagebionetworks.bridge.models.templates;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;

@SuppressWarnings("serial")
@Embeddable
public final class TemplateRevisionId implements Serializable {
    
    @Column(name = "templateGuid")
    private String templateGuid;

    @Column(name = "createdOn")
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;

    public TemplateRevisionId() {
    }
 
    public TemplateRevisionId(String templateGuid, DateTime createdOn) {
        this.templateGuid = templateGuid;
        this.createdOn = createdOn;
    }
    
    public String getTemplateGuid() {
        return templateGuid;
    }
    
    public DateTime getCreatedOn() {
        return createdOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateGuid, createdOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        TemplateRevisionId other = (TemplateRevisionId) obj;
        return Objects.equals(templateGuid, other.templateGuid) && Objects.equals(createdOn, other.createdOn);
    }
}

/*
package org.sagebionetworks.bridge.models.substudies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class SubstudyId implements Serializable {
    
    @Column(name = "studyId")
    private String studyId;

    @Column(name = "id")
    private String id;

    public SubstudyId() {
    }
 
    public SubstudyId(String studyId, String id) {
        this.studyId = studyId;
        this.id = id;
    }
    
    public String getStudyId() {
        return studyId;
    }
    
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SubstudyId other = (SubstudyId) obj;
        return Objects.equals(studyId, other.studyId) && Objects.equals(id, other.id);
    }
}
*/