package org.sagebionetworks.bridge.models.files;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;

@SuppressWarnings("serial")
@Embeddable
public class FileRevisionId implements Serializable {
    @Column(name = "fileGuid")
    private String fileGuid;

    @Column(name = "createdOn")
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    
    public FileRevisionId() {
    }
    public FileRevisionId(String fileGuid, DateTime createdOn) {
        this.fileGuid = fileGuid;
        this.createdOn = createdOn;
    }
    
    public String getFileGuid() {
        return fileGuid;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fileGuid, createdOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        FileRevisionId other = (FileRevisionId) obj;
        return Objects.equals(fileGuid, other.fileGuid) &&
                Objects.equals(createdOn, other.createdOn);
    }      
}
