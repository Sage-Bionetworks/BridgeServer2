package org.sagebionetworks.bridge.services;

import static com.amazonaws.HttpMethod.PUT;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.sagebionetworks.bridge.models.files.FileRevisionStatus.AVAILABLE;
import static org.sagebionetworks.bridge.models.files.FileRevisionStatus.PENDING;
import static org.sagebionetworks.bridge.validators.FileRevisionValidator.INSTANCE;

import java.net.URL;
import java.util.Date;
import java.util.Optional;

import javax.annotation.Resource;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.FileMetadataDao;
import org.sagebionetworks.bridge.dao.FileRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.FileMetadataValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class FileService {
    
    static final int EXPIRATION_IN_MINUTES = 10;
    
    private FileMetadataDao fileMetadataDao;
    
    private FileRevisionDao fileRevisionDao;
    
    private Environment env;
    
    private String revisionsBucket;
    
    private AmazonS3 s3Client;
    
    @Autowired
    final void setFileMetadataDao(FileMetadataDao fileMetadataDao) {
        this.fileMetadataDao = fileMetadataDao;
    }
    
    @Autowired
    final void setFileRevisionDao(FileRevisionDao fileRevisionDao) {
        this.fileRevisionDao = fileRevisionDao;
    }
    
    @Autowired
    final void setConfig(BridgeConfig config) {
        revisionsBucket = config.getHostnameWithPostfix("docs");
        env = config.getEnvironment();
    }
    
    @Resource(name = "fileUploadS3Client")
    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
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
    
    public Optional<FileRevision> getFileRevision(String guid, DateTime createdOn) {
        checkNotNull(guid);
        checkNotNull(createdOn);

        return fileRevisionDao.getFileRevision(guid, createdOn);
    }
    
    public PagedResourceList<FileRevision> getFileRevisions(StudyIdentifier studyId, String guid, int offset, int pageSize) {
        // Will throw if the file doesn't exist in the caller's study
        getFile(studyId, guid);
        
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }        
        PagedResourceList<FileRevision> revisions = fileRevisionDao.getFileRevisions(guid, offset, pageSize);
        for (FileRevision rev : revisions.getItems()) {
            String protocol = (env == LOCAL) ? "http" : "https";
            rev.setDownloadURL(protocol + "://" + revisionsBucket + "/" + getFileName(rev));
        }
        return revisions;
    }
    
    public FileRevision createFileRevision(StudyIdentifier studyId, FileRevision revision) {
        Validate.entityThrowingException(INSTANCE, revision);
        
        // Will throw if the file doesn't exist in the caller's study
        getFile(studyId, revision.getFileGuid());
        
        // Set system properties.
        revision.setCreatedOn(getDateTime());
        revision.setStatus(PENDING);

        String fileName = getFileName(revision);
        Date expiration = getDateTime().plusMinutes(EXPIRATION_IN_MINUTES).toDate();
        
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(revisionsBucket, fileName, PUT);
        request.setExpiration(expiration);
        request.setContentType(revision.getMimeType());
        ResponseHeaderOverrides headers = new ResponseHeaderOverrides()
                .withContentDisposition("attachment; filename=\""+revision.getName()+"\"");
        request.setResponseHeaders(headers);
        
        URL uploadURL = s3Client.generatePresignedUrl(request);
        revision.setUploadURL(uploadURL.toExternalForm());
        
        fileRevisionDao.createFileRevision(revision);
        
        String protocol = (env == LOCAL) ? "http" : "https";
        revision.setDownloadURL(protocol + "://" + revisionsBucket + "/" + getFileName(revision));

        return revision;
    }
    
    public void finishFileRevision(StudyIdentifier studyId, String fileGuid, DateTime createdOn) {
        FileRevision existing = fileRevisionDao.getFileRevision(fileGuid, createdOn)
                .orElseThrow(() -> new EntityNotFoundException(FileRevision.class));
        
        // examine if it is actually on S3
        String fileName = getFileName(existing);
        if (!s3Client.doesObjectExist(revisionsBucket, fileName)) {
            // If not, delete this record to clean up
            fileRevisionDao.deleteFileRevision(existing);
            throw new EntityNotFoundException(FileRevision.class);
        }
        // Otherwise, mark the record available
        existing.setUploadURL(null);
        existing.setStatus(AVAILABLE);
        fileRevisionDao.updateFileRevision(existing);
    }
    
    protected String getFileName(FileRevision revision) {
        return revision.getFileGuid() + "." + revision.getCreatedOn().getMillis();
    }
    
    protected DateTime getDateTime() {
        return new DateTime().withZone(UTC);
    }
    
    protected String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
