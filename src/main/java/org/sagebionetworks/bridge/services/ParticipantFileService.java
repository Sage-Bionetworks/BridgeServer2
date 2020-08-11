package org.sagebionetworks.bridge.services;

import com.amazonaws.services.s3.AmazonS3;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantFileDao;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static org.sagebionetworks.bridge.config.Environment.LOCAL;

@Component
public class ParticipantFileService {
    private DynamoParticipantFileDao participantFileDao;

    private AmazonS3 s3client;

    private Environment env;

    private String bucket;

    @Autowired
    final void setParticipantFileDao(DynamoParticipantFileDao dao) {
        this.participantFileDao = dao;
    }

    @Autowired
    final void setConfig(BridgeConfig config) {
        bucket = config.getHostnameWithPostfix("participant-file");
        env = config.getEnvironment();
    }

    @Resource(name = "participantFileS3Client")
    final void setS3client(AmazonS3 s3) {
        this.s3client = s3;
    }

    /**
     * Returns the download URL for the given ParticipantFile.
     *
     * @param file the file whose URL is returned
     * @return the download URL for the given ParticipantFile.
     */
    protected String getDownloadURL(ParticipantFile file) {
        String protocol = (env == LOCAL) ? "http" : "https";
        String filePath = file.getUserId() + "/" + file.getFileId();
        return protocol + "://" + bucket + "/" + filePath;
    }
}
