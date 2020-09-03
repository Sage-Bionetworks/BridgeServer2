package org.sagebionetworks.bridge.services;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.HttpMethod.GET;
import static com.amazonaws.HttpMethod.PUT;
import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.validators.ParticipantFileValidator.INSTANCE;

@Component
public class ParticipantFileService {

    static final int EXPIRATION_IN_MINUTES = 1440;

    static final String PARTICIPANT_FILE_BUCKET = "participant-file.bucket";

    private ParticipantFileDao participantFileDao;

    private AmazonS3 s3Client;

    private String bucketName;

    @Autowired
    final void setParticipantFileDao(ParticipantFileDao dao) {
        this.participantFileDao = dao;
    }

    @Autowired
    final void setConfig(BridgeConfig config) {
        bucketName = config.get(PARTICIPANT_FILE_BUCKET);
    }

    @Resource(name = "s3Client")
    final void setS3client(AmazonS3 s3) {
        this.s3Client = s3;
    }

    /**
     * Get a ForwardCursorPagedResourceList of ParticipantFiles from the given userId, with nextPageOffsetKey set.
     * If nextPageOffsetKey is null, then the list reached the end and there does not exist next page.
     *
     * @param userId the id of the StudyParticipant
     * @param offsetKey the nextPageOffsetKey.
     *                  (the exclusive starting offset of the query, if null, then query from the start)
     * @param pageSize the number of items in the result page
     * @return a ForwardCursorPagedResourceList of ParticipantFiles
     * @throws BadRequestException if pageSize is less than API_MINIMUM_PAGE_SIZE or greater
     *         than API_MAXIMUM_PAGE_SIZE
     */
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(String userId, String offsetKey, int pageSize) {
        checkArgument(isNotBlank(userId));

        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return participantFileDao.getParticipantFiles(userId, offsetKey, pageSize);
    }

    /**
     * Returns this ParticipantFile metadata for download. If this file does not exist,
     * throws EntityNotFoundException.
     *
     * @param userId the userId to be queried
     * @param fileId the fileId of the file
     * @return the ParticipantFile with the pre-signed S3 download URL if this file exists
     * @throws EntityNotFoundException if the file does not exist.
     */
    public ParticipantFile getParticipantFile(String userId, String fileId) {
        checkArgument(isNotBlank(userId));
        checkArgument(isNotBlank(fileId));

        ParticipantFile file = participantFileDao.getParticipantFile(userId, fileId)
                .orElseThrow(() -> new EntityNotFoundException(ParticipantFile.class));

        file.setDownloadUrl(generatePresignedRequest(file, GET).toExternalForm());
        return file;
    }

    /**
     * Sets the appId and the userId of this file, logs the metadata in the database,
     * and then returns the passed-in ParticipantFile with pre-signed URL for S3 file upload.
     * If the file or the file metadata already exists, throws EntityAlreadyExistsException.
     *
     * @param appId the appId of this file
     * @param userId the userId of this file
     * @param file the file metadata to be upload. The file's appId and userId will be set by given parameters.
     * @return the ParticipantFile with pre-signed S3 URL for file upload.
     * @throws org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException
     *         if the file already exists.
     */
    public ParticipantFile createParticipantFile(String appId, String userId, ParticipantFile file) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(userId));
        file.setUserId(userId);
        file.setAppId(appId);
        file.setCreatedOn(DateTime.now());
        Validate.entityThrowingException(INSTANCE, file);

        participantFileDao.getParticipantFile(file.getUserId(), file.getFileId()).ifPresent(
                oldFile -> {
                    Map<String, Object> entityKey = new HashMap<>();
                    entityKey.put("oldFile", oldFile);
                    throw new EntityAlreadyExistsException(ParticipantFile.class, entityKey);
                }
        );

        file.setUploadUrl(generatePresignedRequest(file, PUT).toExternalForm());
        return file;
    }

    /**
     * Delete the record and the actual file on the server physically. If the file metadata does not exist,
     * throws EntityNotFoundException.
     *
     * @param userId the userId of the file
     * @param fileId the fileId of the file
     * @throws EntityNotFoundException if the file does not exist
     */
    public void deleteParticipantFile(String userId, String fileId) {
        participantFileDao.getParticipantFile(userId, fileId)
                .orElseThrow(() -> new EntityNotFoundException(ParticipantFile.class));

        participantFileDao.deleteParticipantFile(userId, fileId);
        // If the file does not exist on S3, the s3Client will actually return success
        // instead of an error message.
        s3Client.deleteObject(bucketName, userId + "/" + fileId);
    }

    /**
     * Returns the url path of the given file.
     * @param file the file
     * @return the url path of the given file
     */
    private String getFilePath(ParticipantFile file) {
        return file.getUserId() + "/" + file.getFileId();
    }

    private URL generatePresignedRequest(ParticipantFile file, HttpMethod method) {
        Date expiration = new DateTime().plusMinutes(EXPIRATION_IN_MINUTES).toDate();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, getFilePath(file), method);
        request.setExpiration(expiration);
        request.setContentType(file.getMimeType());
        request.addRequestParameter(Headers.SERVER_SIDE_ENCRYPTION, ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        return s3Client.generatePresignedUrl(request);
    }
}
