package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.FileDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.File;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

@Component
public class FileService {
    
    private FileDao fileDao;
    
    @Autowired
    final void setFileDao(FileDao fileDao) {
        this.fileDao = fileDao;
    }
    
    public PagedResourceList<File> getFiles(StudyIdentifier studyId, Integer offset, Integer pageSize, boolean includeDeleted) {
        checkNotNull(studyId);
        
        if (pageSize == null) {
            pageSize = API_DEFAULT_PAGE_SIZE;
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }        
        return fileDao.getFiles(studyId, offset, pageSize, includeDeleted);
    }
    
    public File getFile(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return fileDao.getFile(studyId, guid)
                .orElseThrow(() -> new EntityNotFoundException(File.class));
    }
    
    public File createFile(StudyIdentifier studyId, File file) {
        checkNotNull(studyId);
        checkNotNull(file);
        
        file.setVersion(0);
        file.setDeleted(false);
        file.setGuid(generateGuid());
        file.setStudyId(studyId.getIdentifier());
        return fileDao.createFile(file);
    }
    
    public File updateFile(StudyIdentifier studyId, File file) {
        checkNotNull(studyId);
        checkNotNull(file);
        
        file.setStudyId(studyId.getIdentifier());
        
        File existing = getFile(studyId, file.getGuid());
        if (existing.isDeleted() && file.isDeleted()) {
            throw new EntityNotFoundException(File.class);
        }
        return fileDao.updateFile(file);
    }
    
    public void deleteFile(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        File existing = getFile(studyId, guid);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(File.class);
        }        
        existing.setDeleted(true);
        updateFile(studyId, existing);
    }
    
    public void deleteFilePermanently(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        fileDao.deleteFilePermanently(studyId, guid);
    }
    
    public void deleteAllStudyFiles(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        fileDao.deleteAllStudyFiles(studyId);
    }
    
    protected String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
