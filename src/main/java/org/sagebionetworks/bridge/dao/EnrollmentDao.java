package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentFilter;

public interface EnrollmentDao {
    /**
     * Get accounts that have been enrolled in the study (past and present).
     */
    PagedResourceList<Enrollment> getEnrollmentsForStudy(String appId, String studyId, 
            EnrollmentFilter filter, boolean includeTesters, Integer offsetBy, Integer pageSize);
    
    /**
     * Get enrollments for a specific account.
     */
    public List<Enrollment> getEnrollmentsForUser(String appId, String userId);
}
