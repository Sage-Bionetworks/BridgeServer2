package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;

public interface SponsorDao {
    /**
     * Get the organizations that are sponsoring this study. Members of this organization have additional permissions
     * vis-a-vis the study.
     */
    PagedResourceList<Organization> getStudySponsors(String appId, String studyId, int offsetBy, int pageSize);
    
    /**
     * Get the studies that this organization sponsors. Members of this organization have additional permissions
     * vis-a-vis these studies.
     */
    PagedResourceList<Study> getSponsoredStudies(String appId, String orgId, int offsetBy, int pageSize);
    
    /**
     * Add a sponsoring organization to  a study.
     */
    void addStudySponsor(String appId, String studyId, String orgId);

    /**
     * Remove a sponsoring organization from a study.
     */
    void removeStudySponsor(String appId, String studyId, String orgId);
    
    boolean doesOrganizationSponsorStudy(String appId, String studyId, String orgId);
}
