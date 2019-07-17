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
