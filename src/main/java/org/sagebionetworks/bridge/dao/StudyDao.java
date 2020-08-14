package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyDao {
    
    PagedResourceList<Study> getStudies(String appId, int offsetBy, int pageSize, boolean includeDeleted);
    
    Study getStudy(String appId, String studyId);
    
    VersionHolder createStudy(Study study);
    
    VersionHolder updateStudy(Study study);
    
    void deleteStudyPermanently(String appId, String studyId);

}
