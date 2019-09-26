package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.FileMetadataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.FileMetadataValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class FileService {
    
    private FileMetadataDao fileMetadataDao;
    
    @Autowired
    final void setFileMetadataDao(FileMetadataDao fileMetadataDao) {
        this.fileMetadataDao = fileMetadataDao;
    }
    
    public PagedResourceList<FileMetadata> getFiles(StudyIdentifier studyId, int offset, int pageSize, boolean includeDeleted) {
        checkNotNull(studyId);
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return fileMetadataDao.getFiles(studyId, offset, pageSize, includeDeleted);
    }
    
    public FileMetadata getFile(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return fileMetadataDao.getFile(studyId, guid)
                .orElseThrow(() -> new EntityNotFoundException(FileMetadata.class));
    }
    
    public FileMetadata createFile(StudyIdentifier studyId, FileMetadata metadata) {
        checkNotNull(studyId);
        checkNotNull(metadata);
        
        Validate.entityThrowingException(FileMetadataValidator.INSTANCE, metadata);
        
        DateTime timestamp = getDateTime();
        
        metadata.setVersion(0);
        metadata.setDeleted(false);
        metadata.setGuid(generateGuid());
        metadata.setStudyId(studyId.getIdentifier());
        metadata.setCreatedOn(timestamp);
        metadata.setModifiedOn(timestamp);
        return fileMetadataDao.createFile(metadata);
    }
    
    public FileMetadata updateFile(StudyIdentifier studyId, FileMetadata metadata) {
        checkNotNull(studyId);
        checkNotNull(metadata);
        
        if (isBlank(metadata.getGuid())) {
            throw new BadRequestException("File has no guid");
        }
        FileMetadata existing = getFile(studyId, metadata.getGuid());
        if (existing.isDeleted() && metadata.isDeleted()) {
            throw new EntityNotFoundException(FileMetadata.class);
        }
        Validate.entityThrowingException(FileMetadataValidator.INSTANCE, metadata);
        
        metadata.setStudyId(studyId.getIdentifier());
        metadata.setModifiedOn(getDateTime());
        metadata.setCreatedOn(existing.getCreatedOn());
        return fileMetadataDao.updateFile(metadata);
    }
    
    public void deleteFile(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        FileMetadata existing = getFile(studyId, guid);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(FileMetadata.class);
        }        
        existing.setDeleted(true);
        existing.setModifiedOn(getDateTime());
        fileMetadataDao.updateFile(existing);
    }
    
    public void deleteFilePermanently(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        fileMetadataDao.deleteFilePermanently(studyId, guid);
    }
    
    public void deleteAllStudyFiles(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        fileMetadataDao.deleteAllStudyFiles(studyId);
    }
    
    protected DateTime getDateTime() {
        return new DateTime();
    }
    
    protected String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
