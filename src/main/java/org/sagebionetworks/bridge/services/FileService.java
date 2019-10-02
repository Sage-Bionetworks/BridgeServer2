package org.sagebionetworks.bridge.services;

import static com.amazonaws.HttpMethod.PUT;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.models.files.FileRevisionStatus.PENDING;

import java.net.URL;
import java.util.Date;

import javax.annotation.Resource;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.FileMetadataDao;
import org.sagebionetworks.bridge.dao.FileRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.FileMetadataValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class FileService {
    
    static final String CONFIG_KEY_REVISIONS_BUCKET = "revisions.bucket";
    static final long EXPIRATION = 24 * 60 * 60 * 1000; // 24 hours
    
    private FileMetadataDao fileMetadataDao;
    
    private FileRevisionDao fileRevisionDao;
    
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
        revisionsBucket = config.getProperty(CONFIG_KEY_REVISIONS_BUCKET);
    }
    
    // Note: this client has too many permissions. We'll need to substitute.
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
    
    public PagedResourceList<FileRevision> getFileRevisions(StudyIdentifier studyId, String guid, int offset, int pageSize) {
        // Will throw if the file doesn't exist in the caller's study
        getFile(studyId, guid);
        
        PagedResourceList<FileRevision> revisions = fileRevisionDao.getFileRevisions(guid, offset, pageSize);
        for (FileRevision rev : revisions.getItems()) {
            rev.setDownloadURL("http://" + revisionsBucket + "/" + guid + "." + rev.getCreatedOn().getMillis());
        }
        return revisions;
    }
    
    public FileRevision createFileRevision(StudyIdentifier studyId, FileRevision revision) {
        // Will throw if the file doesn't exist in the caller's study
        getFile(studyId, revision.getFileGuid());
        
        // Set system properties.
        revision.setCreatedOn(getDateTime());
        revision.setStatus(PENDING);

        String fileName = revision.getFileGuid() + "." + revision.getCreatedOn().getMillis();
        Date expiration = DateTime.now(UTC).plusMinutes(10).toDate();
        
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(revisionsBucket, fileName, PUT);
        request.setExpiration(expiration);
        request.setContentType(revision.getMimeType());
        
        // request.setContentMd5(uploadMd5); I sure hope this isn't necessary
        URL uploadURL = s3Client.generatePresignedUrl(request);
        revision.setUploadURL(uploadURL.toExternalForm());
        
        fileRevisionDao.createFileRevision(revision);
        return revision;
    }
    
    public FileRevision updateFileRevision(StudyIdentifier studyId, FileRevision revision) {
        FileRevision existing = fileRevisionDao.getFileRevision(revision.getFileGuid(), revision.getCreatedOn())
                .orElseThrow(() -> new EntityNotFoundException(FileRevision.class));
        // Currently the only thing that can really be updated is the status of the file revision.
        existing.setStatus(revision.getStatus());
        fileRevisionDao.updateFileRevision(existing);
        return existing;
    }
    
    protected DateTime getDateTime() {
        return new DateTime();
    }
    
    protected String generateGuid() {
        return BridgeUtils.generateGuid();
    }
}
