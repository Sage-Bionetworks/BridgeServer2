package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.OrganizationValidator.INSTANCE;

import java.util.Optional;

import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class OrganizationService {

    private OrganizationDao dao;
    
    @Autowired
    final void setOrganizationDao(OrganizationDao dao) {
        this.dao = dao;
    }
    
    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    /**
     * Get a paged list of partially initialized organizations (containing name, description, 
     * and identifier).
     */
    public PagedResourceList<Organization> getOrganizations(String appId, int offsetBy, int pageSize) {
        checkArgument(isNotBlank(appId));
        
        if (offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        return dao.getOrganizations(appId, offsetBy, pageSize)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize);
    }
    
    /**
     * Create an organization. The identifier of this organization must be unique within the context
     * of the app. 
     * @throws EntityAlreadyExistsException
     */
    public Organization createOrganization(Organization organization) {
        checkNotNull(organization);
        
        Validate.entityThrowingException(INSTANCE, organization);
        
        Optional<Organization> optional = dao.getOrganization(
                organization.getAppId(), organization.getIdentifier());
        
        if (optional.isPresent()) {
            throw new EntityAlreadyExistsException(Organization.class, 
                    ImmutableMap.of("appId", organization.getAppId(), 
                            "identifier", organization.getIdentifier()));
        }
        DateTime timestamp = getCreatedOn();
        organization.setCreatedOn(timestamp);
        organization.setModifiedOn(timestamp);
        organization.setVersion(null);
        return dao.createOrganization(organization);
    }
    
    /**
     * Update an existing organization.
     * @throws EntityNotFoundException
     */
    public Organization updateOrganization(Organization organization) {
        checkNotNull(organization);
        
        Validate.entityThrowingException(INSTANCE, organization);
        
        Organization existing = dao.getOrganization(organization.getAppId(), organization.getIdentifier())
                .orElseThrow(() -> new EntityNotFoundException(Organization.class));        
        
        organization.setModifiedOn(getModifiedOn());
        organization.setCreatedOn(existing.getCreatedOn());
        
        return dao.updateOrganization(organization);
    }
    
    /**
     * Get a fully initialized organization object.
     * @throws EntityNotFoundException
     */
    public Organization getOrganization(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        return dao.getOrganization(appId, identifier)
                .orElseThrow(() -> new EntityNotFoundException(Organization.class));        
    }
    
    /**
     * Delete the organization with the given identifier.
     * @throws EntityNotFoundException
     */
    public void deleteOrganization(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        Organization existing = dao.getOrganization(appId, identifier)
                .orElseThrow(() -> new EntityNotFoundException(Organization.class));        

        dao.deleteOrganization(existing);
    }
}
