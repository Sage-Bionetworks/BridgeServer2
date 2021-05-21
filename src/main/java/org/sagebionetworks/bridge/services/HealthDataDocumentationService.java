package org.sagebionetworks.bridge.services;

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
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

@Component
public class HealthDataDocumentationService {
    private static final String DOCUMENTATION_BUCKET = BridgeConfigFactory.getConfig().getProperty("healthdata.docs.bucket");

    private HealthDataDocumentationDao healthDataDocumentationDao;
    private S3Helper s3Helper;

    @Autowired
    final void setHealthDataDocumentationDao(HealthDataDocumentationDao healthDataDocumentationDao) {
        this.healthDataDocumentationDao = healthDataDocumentationDao;
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
        //TODO delete from S3

        healthDataDocumentationDao.deleteDocumentationForIdentifier(identifier, parentId);
    }

    /** Delete all health data documentation for the given parentId */
    public void deleteAllHealthDataDocumentation(String parentId) {
        healthDataDocumentationDao.deleteDocumentationForParentId(parentId);
        //TODO delete from S3
    }

    /** Get health data documentation for the given identifier. */
    public HealthDataDocumentation getHealthDataDocumentationForId(String identifier, String parentId) {
        HealthDataDocumentation documentation = healthDataDocumentationDao.getDocumentationById(identifier, parentId);

        if (documentation == null) {
            throw new EntityNotFoundException(HealthDataDocumentation.class);
        }

        // TODO read from S3 and perhaps return byte[] instead of HealthDataDocumentation

        return documentation;
    }

    /** List all health data documentation for the given parentId. */
    public ForwardCursorPagedResourceList<HealthDataDocumentation> getAllHealthDataDocumentation(String parentId, int pageSize, String offsetKey) {
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }

        // TODO return list<byte[]> instead of HealthDataDocumentation

        return healthDataDocumentationDao.getDocumentationForParentId(parentId, pageSize, offsetKey);
    }
}
