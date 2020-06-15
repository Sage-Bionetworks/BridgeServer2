package org.sagebionetworks.bridge.dao;

import java.util.Optional;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;

public interface OrganizationDao {
    
    PagedResourceList<Organization> getOrganizations(String appId, int offsetBy, int pageSize);
    
    Organization createOrganization(Organization organization);
    
    Organization updateOrganization(Organization organization);
    
    Optional<Organization> getOrganization(String appId, String identifier);
    
    void deleteOrganization(Organization organization);
}
