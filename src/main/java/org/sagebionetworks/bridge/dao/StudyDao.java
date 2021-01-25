package org.sagebionetworks.bridge.dao;

import java.util.Set;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyDao {
    
    PagedResourceList<Study> getStudies(String appId, Set<String> studyIds, 
            Integer offsetBy, Integer pageSize, boolean includeDeleted);
    
    Study getStudy(String appId, String studyId);
    
    VersionHolder createStudy(Study study);
    
    VersionHolder updateStudy(Study study);
    
    void deleteStudyPermanently(String appId, String studyId);

    void deleteAllStudies(String appId);
}
