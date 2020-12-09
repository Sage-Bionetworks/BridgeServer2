package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;

/**
 * A DAO for managing external identifiers. For apps utilizing strict validation of external identifiers, they must be 
 * selected from a known list of identifiers, uploaded by study designers. An ID will not be assigned to two different users 
 * or re-assigned to another user if assigned.
 */
public interface ExternalIdDao {
    /**
     * Get a page of external IDs associated to the indicated study.  
     */
    PagedResourceList<ExternalIdentifierInfo> getPagedExternalIds(String appId, String studyId, String idFilter,
            Integer offsetBy, Integer pageSize);

    /**
     * Delete an external identifier.
     */
    void deleteExternalId(ExternalIdentifier externalIdentifier);
}
