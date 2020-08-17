package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyDao {
    
    List<Study> getStudies(String appId, boolean includeDeleted);
    
    Study getStudy(String appId, String studyId);
    
    /**
     * If an organization ID is provided, that organization will be 
     * established as the initial sponsor organization. This will be 
     * defaulted to the caller's organization unless the caller can set it
     * as something else (admins).
     */
    VersionHolder createStudy(Study study, String orgId);
    
    VersionHolder updateStudy(Study study);
    
    void deleteStudyPermanently(String appId, String studyId);

}
