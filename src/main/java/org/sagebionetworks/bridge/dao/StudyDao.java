package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.studies.App;

public interface StudyDao {

    boolean doesIdentifierExist(String identifier);
    
    App getStudy(String identifier);
    
    List<App> getStudies();
    
    App createStudy(App app);
    
    App updateStudy(App app);
    
    void deleteStudy(App app);

    void deactivateStudy(String studyId);
}
