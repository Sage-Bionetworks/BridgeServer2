package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableMap;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.HibernateOrganization;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.organizations.OrganizationId;

@Component
public class HibernateOrganizationDao implements OrganizationDao {
    
    private static String FULL_CLASS_NAME = "org.sagebionetworks.bridge.models.organizations.HibernateOrganization";
    
    static final String GET_QUERY = "FROM HibernateOrganization WHERE appId = :appId";
    static final String GET_SUMMARY_QUERY = "SELECT new "+FULL_CLASS_NAME+"(o.name, o.identifier, o.description) "
            + "FROM "+FULL_CLASS_NAME+" o WHERE appId = :appId";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "organizationHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Organization> getOrganizations(String appId, Integer offsetBy, Integer pageSize) {
        checkNotNull(appId);
        
        Map<String, Object> params = ImmutableMap.of("appId", appId);
        
        int total = hibernateHelper.queryCount("SELECT count(*) " + GET_QUERY, params);
        List<HibernateOrganization> hibernateOrgs = hibernateHelper.queryGet(
                GET_SUMMARY_QUERY, params, offsetBy, pageSize, HibernateOrganization.class);
        
        List<Organization> orgs = hibernateOrgs.stream().map(o -> (Organization)o).collect(toList());
        
        return new PagedResourceList<Organization>(orgs, total);
    }

    @Override
    public Organization createOrganization(Organization organization) {
        checkNotNull(organization);
        
        hibernateHelper.create(organization, null);
        return organization;
    }

    @Override
    public Organization updateOrganization(Organization organization) {
        checkNotNull(organization);
        
        hibernateHelper.update(organization, null);
        return organization;
    }
    
    @Override
    public Optional<Organization> getOrganization(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        OrganizationId id = new OrganizationId(appId, identifier);
        HibernateOrganization org = hibernateHelper.getById(HibernateOrganization.class, id);
        
        return (org == null) ? Optional.empty() : Optional.of((Organization)org);
    }

    @Override
    public void deleteOrganization(Organization organization) {
        checkNotNull(organization);
        
        OrganizationId id = new OrganizationId(organization.getAppId(), organization.getIdentifier());
        hibernateHelper.deleteById(HibernateOrganization.class, id);
    }
    
    @Override
    public void deleteAllOrganizations(String appId) {
        checkNotNull(appId);
        
        Map<String,Object> parameters = ImmutableMap.of("appId", appId);
        String query = "delete from HibernateOrganization where appId=:appId";

        hibernateHelper.queryUpdate(query, parameters);
    }
}
