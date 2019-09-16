package org.sagebionetworks.bridge.models.files;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "Files")
public class File implements BridgeEntity {
    private String studyId;
    private String name;
    private String guid;
    private String description;
    private String mimeType;
    private boolean deleted;
    private int version;
    
    @JsonIgnore
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    @Id
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    /** Probably optional since S3 autodetects, but some files (e.g. without extensions) may need it. */
    public String getMimeType() {
        return mimeType; 
    }
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    /** Files support logical deletion. Revisions remain accessible on S3 for released clients and configurations. */
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @Version
    public int getVersion() {
        return version;
    }
    public void setVersion(int version) {
        this.version = version;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(studyId, deleted, description, guid, mimeType, name);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        File other = (File) obj;
        return (Objects.equals(studyId, other.studyId) &&
                Objects.equals(deleted, other.deleted) && 
                Objects.equals(description, other.description) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(mimeType, other.mimeType) &&
                Objects.equals(name, other.name));
    }
    @Override
    public String toString() {
        return "File [studyId=" + studyId + ", name=" + name + ", guid=" + guid + ", description=" + description
                + ", mimeType=" + mimeType + ", deleted=" + deleted + "]";
    }
}
