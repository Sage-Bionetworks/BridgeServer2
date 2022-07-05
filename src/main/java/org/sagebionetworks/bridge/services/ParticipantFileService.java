package org.sagebionetworks.bridge.services;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.sagebionetworks.bridge.util.ByteRateLimiter;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.amazonaws.HttpMethod.GET;
import static com.amazonaws.HttpMethod.PUT;
import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMIT_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_INITIAL_BYTES_PROD;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_MAXIMUM_BYTES_PROD;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_REFILL_INTERVAL_SECONDS_PROD;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_REFILL_BYTES_PROD;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_INITIAL_BYTES_TEST;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_MAXIMUM_BYTES_TEST;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_REFILL_INTERVAL_SECONDS_TEST;
import static org.sagebionetworks.bridge.BridgeConstants.PARTICIPANT_FILE_RATE_LIMITER_REFILL_BYTES_TEST;
import static org.sagebionetworks.bridge.validators.ParticipantFileValidator.INSTANCE;


@Component
public class ParticipantFileService {

    static final int EXPIRATION_IN_MINUTES = 1440;

    static final String PARTICIPANT_FILE_BUCKET = "participant-file.bucket";

    private ParticipantFileDao participantFileDao;

    private AmazonS3 s3Client;

    private String bucketName;

    private boolean isProduction;

    private Map<String, ByteRateLimiter> userByteRateLimiters = new ConcurrentHashMap<>();

    @Autowired
    final void setParticipantFileDao(ParticipantFileDao dao) {
        this.participantFileDao = dao;
    }

    @Autowired
    final void setConfig(BridgeConfig config) {
        bucketName = config.get(PARTICIPANT_FILE_BUCKET);
        isProduction = config.isProduction();
    }

    @Resource(name = "s3Client")
    final void setS3client(AmazonS3 s3) {
        this.s3Client = s3;
    }

    /**
     * Creates and returns a ByteRateLimiter with different settings depending on
     * the environment.
     * 
     * @return a ByteRateLimiter
     */
    private ByteRateLimiter createByteRateLimiter() {
        if (isProduction) {
            return new ByteRateLimiter(PARTICIPANT_FILE_RATE_LIMITER_INITIAL_BYTES_PROD,
                    PARTICIPANT_FILE_RATE_LIMITER_MAXIMUM_BYTES_PROD,
                    PARTICIPANT_FILE_RATE_LIMITER_REFILL_INTERVAL_SECONDS_PROD,
                    PARTICIPANT_FILE_RATE_LIMITER_REFILL_BYTES_PROD);
        } else {
            return new ByteRateLimiter(PARTICIPANT_FILE_RATE_LIMITER_INITIAL_BYTES_TEST,
                    PARTICIPANT_FILE_RATE_LIMITER_MAXIMUM_BYTES_TEST,
                    PARTICIPANT_FILE_RATE_LIMITER_REFILL_INTERVAL_SECONDS_TEST,
                    PARTICIPANT_FILE_RATE_LIMITER_REFILL_BYTES_TEST);
        }
    }

    /**
     * Retrieves the file size of a file stored on S3 in bytes. If the file has not
     * yet been uploaded or does not exist, 0 is returned.
     * 
     * @param file the file to get the size of
     * @return the size of the file in bytes (0 if not found)
     */
    private long getS3FileSize(ParticipantFile file) {
        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, getFilePath(file));
            if (metadata != null) {
                return metadata.getContentLength();
            }
            return 0;
        } catch (AmazonS3Exception e) {
            // file may not have been uploaded yet
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return 0;
            }
            throw e;
        }
    }

    /**
     * Get a ForwardCursorPagedResourceList of ParticipantFiles from the given
     * userId, with nextPageOffsetKey set.
     * If nextPageOffsetKey is null, then the list reached the end and there does
     * not exist next page.
     *
     * @param userId    the id of the StudyParticipant
     * @param offsetKey the nextPageOffsetKey.
     *                  (the exclusive starting offset of the query, if null, then
     *                  query from the start)
     * @param pageSize  the number of items in the result page
     * @return a ForwardCursorPagedResourceList of ParticipantFiles
     * @throws BadRequestException    if pageSize is less than API_MINIMUM_PAGE_SIZE
     *                                or greater
     *                                than API_MAXIMUM_PAGE_SIZE
     * @throws LimitExceededException if the user has requested to download too
     *                                much data too frequently
     */
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(String userId, String offsetKey,
            int pageSize) throws BadRequestException, LimitExceededException {
        checkArgument(isNotBlank(userId));

        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        ForwardCursorPagedResourceList<ParticipantFile> files = participantFileDao.getParticipantFiles(userId,
                offsetKey, pageSize);
        if (files == null) {
            return null;
        }

        long totalFileSizesBytes = 0;
        for (ParticipantFile file : files.getItems()) {
            totalFileSizesBytes += getS3FileSize(file);
        }
        ByteRateLimiter rateLimiter = userByteRateLimiters.computeIfAbsent(userId, (u) -> createByteRateLimiter());
        if (!rateLimiter.tryConsumeBytes(totalFileSizesBytes)) {
            throw new LimitExceededException(PARTICIPANT_FILE_RATE_LIMIT_ERROR);
        }

        return files;
    }

    /**
     * Returns this ParticipantFile metadata for download. If this file does not
     * exist,
     * throws EntityNotFoundException.
     *
     * @param userId the userId to be queried
     * @param fileId the fileId of the file
     * @return the ParticipantFile with the pre-signed S3 download URL if this file
     *         exists
     * @throws EntityNotFoundException if the file does not exist.
     * @throws LimitExceededException  if the user has requested to download too
     *                                 much data too frequently
     */
    public ParticipantFile getParticipantFile(String userId, String fileId)
            throws EntityNotFoundException, LimitExceededException {
        checkArgument(isNotBlank(userId));
        checkArgument(isNotBlank(fileId));

        ParticipantFile file = participantFileDao.getParticipantFile(userId, fileId)
                .orElseThrow(() -> new EntityNotFoundException(ParticipantFile.class));

        long fileSizeBytes = getS3FileSize(file);
        ByteRateLimiter rateLimiter = userByteRateLimiters.computeIfAbsent(userId, (u) -> createByteRateLimiter());
        if (!rateLimiter.tryConsumeBytes(fileSizeBytes)) {
            throw new LimitExceededException(PARTICIPANT_FILE_RATE_LIMIT_ERROR);
        }

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
     */
    public ParticipantFile createParticipantFile(String appId, String userId, ParticipantFile file) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(userId));
        file.setUserId(userId);
        file.setAppId(appId);
        file.setCreatedOn(DateTime.now());
        Validate.entityThrowingException(INSTANCE, file);

        participantFileDao.uploadParticipantFile(file);

        // Deleting any previous object prevents a user from updating the ParticipantFile
        // but leaving the previous object on S3.
        s3Client.deleteObject(bucketName, getFilePath(file));

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
        DateTime expiration = DateTime.now().plusMinutes(EXPIRATION_IN_MINUTES);
        file.setExpiresOn(expiration);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, getFilePath(file), method);
        request.setExpiration(expiration.toDate());
        if (PUT.equals(method)) {
            request.setContentType(file.getMimeType());
            request.addRequestParameter(Headers.SERVER_SIDE_ENCRYPTION, ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        }

        return s3Client.generatePresignedUrl(request);
    }
}
