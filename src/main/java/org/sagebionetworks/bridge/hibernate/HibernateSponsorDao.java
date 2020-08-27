package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.SponsorDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.HibernateOrganization;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

@Component
public class HibernateSponsorDao implements SponsorDao {
    
    public static final String ADD_SPONSOR_SQL = "INSERT INTO OrganizationsStudies (appId, studyId, orgId) VALUES (:appId, :studyId, :orgId)";

    private HibernateHelper hibernateHelper;
    
    @Resource(name = "sponsorHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Organization> getStudySponsors(String appId, String studyId, Integer offsetBy, Integer pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Organizations o INNER JOIN OrganizationsStudies os ON o.identifier = os.orgId AND ");
        builder.append("o.appId = :appId AND os.appId = :appId ", "appId", appId);
        builder.append("AND os.studyId = :studyId", "studyId", studyId);

        int total = hibernateHelper.nativeQueryCount("SELECT count(*) " + builder.getQuery(), builder.getParameters());

        List<HibernateOrganization> organizations = hibernateHelper.nativeQueryGet(
                "SELECT * " + builder.getQuery(), builder.getParameters(), 
                offsetBy, pageSize, HibernateOrganization.class);
        
        return new PagedResourceList<Organization>(ImmutableList.copyOf(organizations), total);
    }

    @Override
    public PagedResourceList<Study> getSponsoredStudies(String appId, String orgId, Integer offsetBy, Integer pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Substudies s INNER JOIN OrganizationsStudies os ON s.id = os.studyId AND ");
        builder.append("s.studyId = :appId AND os.appId = :appId ", "appId", appId);
        builder.append("AND os.orgId = :orgId", "orgId", orgId);
        builder.append("AND s.deleted != 1");

        int total = hibernateHelper.nativeQueryCount("SELECT count(*) " + builder.getQuery(), builder.getParameters());

        List<HibernateStudy> studies = hibernateHelper.nativeQueryGet(
                "SELECT * " + builder.getQuery(), builder.getParameters(), 
                offsetBy, pageSize, HibernateStudy.class);
        
        return new PagedResourceList<Study>(ImmutableList.copyOf(studies), total);
    }

    @Override
    public void addStudySponsor(String appId, String studyId, String orgId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append(ADD_SPONSOR_SQL);
        builder.getParameters().put("appId", appId);
        builder.getParameters().put("studyId", studyId);
        builder.getParameters().put("orgId", orgId);
        
        hibernateHelper.nativeQueryUpdate(builder.getQuery(), builder.getParameters());
    }

    @Override
    public void removeStudySponsor(String appId, String studyId, String orgId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("DELETE FROM OrganizationsStudies WHERE appId = :appId ", "appId", appId);
        builder.append("AND studyId = :studyId ", "studyId", studyId);
        builder.append("AND orgId = :orgId", "orgId", orgId);
        
        hibernateHelper.nativeQueryUpdate(builder.getQuery(), builder.getParameters());
    }
    
    @Override
    public boolean doesOrganizationSponsorStudy(String appId, String studyId, String orgId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("SELECT count(*) FROM OrganizationsStudies os WHERE os.appId = :appId ", "appId", appId);
        builder.append("AND os.studyId = :studyId ", "studyId", studyId);
        builder.append("AND os.orgId = :orgId", "orgId", orgId);

        return hibernateHelper.nativeQueryCount(builder.getQuery(), builder.getParameters()) > 0;
    }
}
