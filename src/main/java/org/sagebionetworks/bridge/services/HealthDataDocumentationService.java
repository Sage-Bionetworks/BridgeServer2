package org.sagebionetworks.bridge.services;

import com.amazonaws.services.s3.AmazonS3Client;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.HealthDataDocumentationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

@Component
public class HealthDataDocumentationService {
    private static final String DOCUMENTATION_BUCKET = BridgeConfigFactory.getConfig().getProperty("healthdata.docs.bucket");

    private HealthDataDocumentationDao healthDataDocumentationDao;
    private AmazonS3Client s3Client;
    private S3Helper s3Helper;

    @Autowired
    final void setHealthDataDocumentationDao(HealthDataDocumentationDao healthDataDocumentationDao) {
        this.healthDataDocumentationDao = healthDataDocumentationDao;
    }

    @Resource(name = "s3Client")
    final void setS3Client(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Resource(name = "s3Helper")
    final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    /** Create or update a health data documentation.*/
    public HealthDataDocumentation createOrUpdateHealthDataDocumentation(HealthDataDocumentation documentation, byte[] documentationBytes) throws IOException {
        checkNotNull(documentation);

        String s3Key = documentation.getParentId() + "-" + documentation.getIdentifier();
        s3Helper.writeBytesToS3(DOCUMENTATION_BUCKET, s3Key, documentationBytes);

        return healthDataDocumentationDao.createOrUpdateDocumentation(documentation);
    }

    /** Delete health data documentation for the given identifier. */
    public void deleteHealthDataDocumentation(String identifier, String parentId) {
        HealthDataDocumentation documentation = this.getHealthDataDocumentationForId(identifier, parentId);

        if (documentation == null) {
            throw new EntityNotFoundException(HealthDataDocumentation.class);
        }

        s3Client.deleteObject(DOCUMENTATION_BUCKET, documentation.getS3Key());
        healthDataDocumentationDao.deleteDocumentationForIdentifier(identifier, parentId);
    }

    /** Delete all health data documentation for the given parentId */
    public void deleteAllHealthDataDocumentation(String parentId) {
        checkNotNull(parentId);

        String offsetKey = null;
        ForwardCursorPagedResourceList<HealthDataDocumentation> documentationList =
                getAllHealthDataDocumentation(parentId, API_DEFAULT_PAGE_SIZE, offsetKey);

        while ((offsetKey = documentationList.getNextPageOffsetKey()) != null) {
            for (HealthDataDocumentation documentation : documentationList.getItems()) {
                s3Client.deleteObject(DOCUMENTATION_BUCKET, documentation.getS3Key());
            }
            documentationList = getAllHealthDataDocumentation(parentId, API_DEFAULT_PAGE_SIZE, offsetKey);
        }

        healthDataDocumentationDao.deleteDocumentationForParentId(parentId);
    }

    /** Get health data documentation for the given identifier. */
    public HealthDataDocumentation getHealthDataDocumentationForId(String identifier, String parentId) {
        HealthDataDocumentation documentation = healthDataDocumentationDao.getDocumentationById(identifier, parentId);

        if (documentation == null) {
            throw new EntityNotFoundException(HealthDataDocumentation.class);
        }

        return documentation;
    }

    /** List all health data documentation for the given parentId. */
    public ForwardCursorPagedResourceList<HealthDataDocumentation> getAllHealthDataDocumentation(String parentId, int pageSize, String offsetKey) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }

        return healthDataDocumentationDao.getDocumentationForParentId(parentId, pageSize, offsetKey);
    }
}
