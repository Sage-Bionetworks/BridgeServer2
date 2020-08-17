package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;

public interface StudyDao {
    
    List<Study> getStudies(String appId, boolean includeDeleted);
    
    Study getStudy(String appId, String studyId);
    
    VersionHolder createStudy(String orgId, Study study);
    
    VersionHolder updateStudy(Study study);
    
    void deleteStudyPermanently(String appId, String studyId);

}
