package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;

import javax.annotation.Nonnull;
import java.util.Optional;

/** DAO for health data documentation. */
public interface HealthDataDocumentationDao {
    /** Create or update health data documentation. Returns the created or updated documentation. */
    HealthDataDocumentation createOrUpdateDocumentation(@Nonnull HealthDataDocumentation documentation);

    /** Deletes all health data documentation for the given parentId. */
    void deleteDocumentationForParentId(@Nonnull String parentId);

    /** Retrieves the documentation for the given identifier. */
    Optional<HealthDataDocumentation> getDocumentationById(@Nonnull String identifier);

    /** Retrieves all documentation for the given parentId */
    ForwardCursorPagedResourceList<HealthDataDocumentation> getDocumentationForParentId(@Nonnull String parentId,
                                                                                        int pageSize, String offsetKey);
}
