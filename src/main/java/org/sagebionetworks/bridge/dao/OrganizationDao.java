package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;

public interface OrganizationDao {
    /**
     * Return a list of partially initialized organization objects (containing the name, description and 
     * identifier of each record).  
     */
    PagedResourceList<Organization> getOrganizations(String appId, int offsetBy, int pageSize);

    /**
     * Create an organization.
     */
    Organization createOrganization(Organization organization);
    
    /**
     * Update an organization.
     */
    Organization updateOrganization(Organization organization);
    
    /**
     * Get a completely initialized organization object.
     */
    Optional<Organization> getOrganization(String appId, String identifier);
    
    /**
     * Delete this organization object from the database.
     */
    void deleteOrganization(Organization organization);
    
    /**
     * Delete all the organizations in this app (as part of test cleanup).
     */
    void deleteAllOrganizations(String appId);
}
