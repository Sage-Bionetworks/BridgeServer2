package org.sagebionetworks.bridge.services;

import com.amazonaws.services.s3.AmazonS3;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantFileDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.config.Environment.LOCAL;

@Component
public class ParticipantFileService {
    private DynamoParticipantFileDao participantFileDao;

    private AmazonS3 s3client;

    private Environment env;

    private String bucketName;

    @Autowired
    final void setParticipantFileDao(DynamoParticipantFileDao dao) {
        this.participantFileDao = dao;
    }

    @Autowired
    final void setConfig(BridgeConfig config) {
        bucketName = config.getHostnameWithPostfix("participant-file");
        env = config.getEnvironment();
    }

    @Resource(name = "participantFileS3Client")
    final void setS3client(AmazonS3 s3) {
        this.s3client = s3;
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

    public String createParticipantFile(ParticipantFile file) {
        // TODO
        return null;
    }

    public void deleteParticipantFile(String userId, String fileId) {
        // TODO
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
