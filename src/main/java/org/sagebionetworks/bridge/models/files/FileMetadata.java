package org.sagebionetworks.bridge.models.files;

import java.util.Objects;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "FileMetadata")
public final class FileMetadata implements BridgeEntity {
    @JsonIgnore
    private String studyId;
    private String name;
    @Id
    private String guid;
    private String description;
    private String mimeType;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    private boolean deleted;
    @Version
    private long version;
    
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
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(studyId, deleted, description, guid, mimeType, name, createdOn, modifiedOn, version);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        FileMetadata other = (FileMetadata) obj;
        return (Objects.equals(studyId, other.studyId) &&
                Objects.equals(deleted, other.deleted) && 
                Objects.equals(description, other.description) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(mimeType, other.mimeType) &&
                Objects.equals(name, other.name) &&
                Objects.equals(createdOn, other.createdOn) &&
                Objects.equals(modifiedOn, other.modifiedOn) &&
                Objects.equals(version, other.version));
    }
    @Override
    public String toString() {
        return "FileMetadata [studyId=" + studyId + ", name=" + name + ", guid=" + guid + ", description=" + description
                + ", mimeType=" + mimeType + ", createdOn=" + createdOn + ", modifiedOn=" + modifiedOn + ", deleted="
                + deleted + ", version=" + version + "]";
    }
}
