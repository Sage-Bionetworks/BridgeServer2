package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

public interface EnrollmentDao {
    /**
     * Get accounts that have been enrolled in the study (past and present).
     */
    PagedResourceList<EnrollmentDetail> getEnrollmentsForStudy(String appId, String studyId, 
            EnrollmentFilter filter, Integer offsetBy, Integer pageSize);
    
    /**
     * Get enrollments for a specific account.
     */
    public List<EnrollmentDetail> getEnrollmentsForUser(String appId, String userId);
}
