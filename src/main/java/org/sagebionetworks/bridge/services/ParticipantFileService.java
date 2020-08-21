package org.sagebionetworks.bridge.services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import org.apache.shiro.crypto.hash.Hash;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
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

import static com.amazonaws.HttpMethod.PUT;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;
import static org.sagebionetworks.bridge.validators.ParticipantFileValidator.INSTANCE;

@Component
public class ParticipantFileService {

    static final int EXPIRATION_IN_MINUTES = 10;

    private ParticipantFileDao participantFileDao;

    private AmazonS3 s3Client;

    private Environment env;

    private String bucketName;

    @Autowired
    final void setParticipantFileDao(ParticipantFileDao dao) {
        this.participantFileDao = dao;
    }

    @Autowired
    final void setConfig(BridgeConfig config) {
        bucketName = config.getHostnameWithPostfix("participant-file");
        env = config.getEnvironment();
    }

    @Resource(name = "participantFileS3Client")
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
     */
    public ForwardCursorPagedResourceList<ParticipantFile> getParticipantFiles(String userId, String offsetKey, int pageSize) {
        checkNotNull(userId);
        checkArgument(isNotBlank(userId));

        // pageSize validation is checked in DAO
        return participantFileDao.getParticipantFiles(userId, offsetKey, pageSize);
    }

    /**
     * Returns the S3 pre-signed URL of this ParticipantFile for download. If this file does not exist,
     * throws EntityNotFoundException.
     *
     * @param userId the userId to be queried
     * @param fileId the fileId of the file
     * @return the S3 pre-signed URL of this ParticipantFile for download if this file exists
     * @throws EntityNotFoundException if the file does not exist.
     */
    public String getParticipantFileUrl(String userId, String fileId) {
        checkNotNull(userId);
        checkNotNull(fileId);
        checkArgument(isNotBlank(userId));
        checkArgument(isNotBlank(fileId));

        ParticipantFile file = participantFileDao.getParticipantFile(userId, fileId)
                .orElseThrow(() -> new EntityNotFoundException(ParticipantFile.class));
        return getDownloadURL(file);
    }

    /**
     * Logs the metadata in the database, and then returns a pre-signed URL for S3 file upload.
     * If the file or the file metadata already exists, throws EntityAlreadyExistsException.
     *
     * @param file the file metadata to be upload
     * @return a pre-signed S3 URL for file upload.
     * @throws org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException
     *         if the file already exists.
     */
    public URL createParticipantFile(ParticipantFile file) {
        Validate.entityThrowingException(INSTANCE, file);

        participantFileDao.getParticipantFile(file.getUserId(), file.getFileId()).ifPresent(
                oldFile -> {
                    Map<String, Object> entityKey = new HashMap<>();
                    entityKey.put("oldFile", oldFile);
                    throw new EntityAlreadyExistsException(ParticipantFile.class, entityKey);
                }
        );

        // file.setCreatedOn(new DateTime().withZone(UTC));
        Date expiration = new DateTime().withZone(UTC).plusMinutes(EXPIRATION_IN_MINUTES).toDate();
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, getFilePath(file), PUT);
        request.setExpiration(expiration);
        request.setContentType(file.getMimeType());
        ResponseHeaderOverrides headers = new ResponseHeaderOverrides()
                .withContentDisposition("attachment; filename=\""+getFilePath(file)+"\"");
        request.setResponseHeaders(headers);

        return s3Client.generatePresignedUrl(request);
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
        ParticipantFile file = participantFileDao.getParticipantFile(userId, fileId)
                .orElseThrow(() -> new EntityNotFoundException(ParticipantFile.class));

        participantFileDao.deleteParticipantFile(userId, fileId);
        // If the file does not exist on S3, the s3Client will actually return success
        // instead of an error message.
        s3Client.deleteObject(bucketName, userId + "/" + fileId);
    }

    /**
     * Returns the download URL for the given ParticipantFile.
     * This method does not check whether this file actually exists.
     *
     * @param file the file whose URL is returned
     * @return the download URL for the given ParticipantFile.
     */
    protected String getDownloadURL(ParticipantFile file) {
        String protocol = (env == LOCAL) ? "http" : "https";
        return protocol + "://" + bucketName + "/" + getFilePath(file);
    }

    /**
     * Returns the url path of the given file.
     * @param file the file
     * @return the url path of the given file
     */
    protected String getFilePath(ParticipantFile file) {
        return file.getUserId() + "/" + file.getFileId();
    }
}
