package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.dao.HealthDataDocumentationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;

@Component
public class HealthDataDocumentationService {
    private HealthDataDocumentationDao healthDataDocumentationDao;

    @Autowired
    final void setHealthDataDocumentationDao(HealthDataDocumentationDao healthDataDocumentationDao) {
        this.healthDataDocumentationDao = healthDataDocumentationDao;
    }

    /** Create or update a health data documentation.*/
    public HealthDataDocumentation createOrUpdateHealthDataDocumentation(HealthDataDocumentation documentation) {
        checkNotNull(documentation);

        return healthDataDocumentationDao.createOrUpdateDocumentation(documentation);
    }

    /** Delete health data documentation for the given identifier. */
    public void deleteHealthDataDocumentation(String identifier, String parentId) {
        HealthDataDocumentation documentation = this.getHealthDataDocumentationForId(identifier, parentId);

        if (documentation == null) {
            throw new EntityNotFoundException(HealthDataDocumentation.class);
        }

        healthDataDocumentationDao.deleteDocumentationForIdentifier(identifier, parentId);
    }

    /** Delete all health data documentation for the given parentId */
    public void deleteAllHealthDataDocumentation(String parentId) {
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
