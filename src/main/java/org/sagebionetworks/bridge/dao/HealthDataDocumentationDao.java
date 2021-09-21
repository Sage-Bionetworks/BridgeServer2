package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;

import javax.annotation.Nonnull;

/** DAO for health data documentation. */
public interface HealthDataDocumentationDao {
    /** Create or update health data documentation. Returns the created or updated documentation. */
    HealthDataDocumentation createOrUpdateDocumentation(@Nonnull HealthDataDocumentation documentation);

    /** Deletes all health data documentation for the given parentId. */
    void deleteDocumentationForParentId(@Nonnull String parentId);

    /** Delete health data documentation for the given identifier */
    void deleteDocumentationForIdentifier(@Nonnull String parentId, @Nonnull String identifier);

    /** Retrieves the documentation for the given identifier. */
    HealthDataDocumentation getDocumentationByIdentifier( @Nonnull String parentId, @Nonnull String identifier);

    /** Retrieves all documentation for the given parentId */
    ForwardCursorPagedResourceList<HealthDataDocumentation> getDocumentationForParentId(@Nonnull String parentId,
                                                                                        int pageSize, String offsetKey);
}
