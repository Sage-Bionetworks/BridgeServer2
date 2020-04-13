package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

public interface AppConfigElementDao {
    
    List<AppConfigElement> getMostRecentElements(String studyId, boolean includeDeleted);
    
    AppConfigElement getMostRecentElement(String studyId, String id);

    List<AppConfigElement> getElementRevisions(String studyId, String id, boolean includeDeleted);
    
    AppConfigElement getElementRevision(String studyId, String id, long revision);
    
    VersionHolder saveElementRevision(AppConfigElement element);
    
    void deleteElementRevisionPermanently(String studyId, String id, long revision);

}
