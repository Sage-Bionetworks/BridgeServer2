package org.sagebionetworks.bridge.models.files;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "FileRevisions")
@IdClass(FileRevisionId.class)
public class FileRevision implements BridgeEntity {
    @Id
    @JoinColumn(name = "fileGuid")
    private String fileGuid;
    @Id
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    private String name;
    private String description;
    private Long size;
    private String uploadURL;
    @Enumerated(EnumType.STRING)
    private FileRevisionStatus status;
    private String mimeType;
    @Transient
    private String downloadURL;
    
    public String getFileGuid() {
        return fileGuid;
    }
    public void setFileGuid(String fileGuid) {
        this.fileGuid = fileGuid;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getUploadURL() {
        return uploadURL;
    }
    public void setUploadURL(String uploadURL) {
        this.uploadURL = uploadURL;
    }
    public FileRevisionStatus getStatus() {
        return status;
    }
    public void setStatus(FileRevisionStatus status) {
        this.status = status;
    }    
    public String getMimeType() {
        return mimeType;
    }
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }    
    public Long getSize() {
        return size;
    }
    public void setSize(Long size) {
        this.size = size;
    }    
    public String getDownloadURL() {
        return downloadURL;
    }
    public void setDownloadURL(String downloadURL) {
        this.downloadURL = downloadURL;
    }
}
