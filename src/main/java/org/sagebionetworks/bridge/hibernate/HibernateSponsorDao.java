package org.sagebionetworks.bridge.hibernate;

import static java.util.stream.Collectors.toList;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.SponsorDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.HibernateOrganization;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

@Component
public class HibernateSponsorDao implements SponsorDao {
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "sponsorHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Organization> getStudySponsors(String appId, String studyId, int offsetBy, int pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Organizations o INNER JOIN OrganizationsStudies os ON o.identifier = os.orgId AND ");
        builder.append("o.appId = :appId AND os.appId = :appId ", "appId", appId);
        builder.append("AND os.studyId = :studyId", "studyId", studyId);

        int total = hibernateHelper.nativeQueryCount("SELECT count(*) " + builder.getQuery(), 
                builder.getParameters());

        List<HibernateOrganization> hibernateStudies = hibernateHelper.nativeQueryGet("SELECT * " + builder.getQuery(), 
                builder.getParameters(), offsetBy, pageSize, HibernateOrganization.class);
        
        List<Organization> studies = hibernateStudies.stream().map(o -> (Organization)o).collect(toList());
        
        return new PagedResourceList<Organization>(studies, total, true);
    }

    @Override
    public PagedResourceList<Study> getSponsoredStudies(String appId, String orgId, int offsetBy, int pageSize) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("FROM Substudies s INNER JOIN OrganizationsStudies os ON s.id = os.studyId AND ");
        builder.append("s.studyId = :appId AND os.appId = :appId ", "appId", appId);
        builder.append("AND os.orgId = :orgId", "orgId", orgId);

        int total = hibernateHelper.nativeQueryCount("SELECT count(*) " + builder.getQuery(), 
                builder.getParameters());

        List<HibernateStudy> hibernateStudies = hibernateHelper.nativeQueryGet("SELECT * " + builder.getQuery(), 
                builder.getParameters(), offsetBy, pageSize, HibernateStudy.class);
        
        List<Study> studies = hibernateStudies.stream().map(s -> (Study)s).collect(toList());
        
        return new PagedResourceList<Study>(studies, total, true);
    }

    @Override
    public void addStudySponsor(String appId, String studyId, String orgId) {
        QueryBuilder builder = new QueryBuilder();
        builder.append("INSERT INTO OrganizationsStudies (appId, studyId, orgId) VALUES (:appId, :studyId, :orgId)");
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
