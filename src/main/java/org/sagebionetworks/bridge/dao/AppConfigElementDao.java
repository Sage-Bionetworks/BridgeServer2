package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

public interface AppConfigElementDao {
    
    List<AppConfigElement> getMostRecentElements(String appId, boolean includeDeleted);
    
    AppConfigElement getMostRecentElement(String appId, String id);

    List<AppConfigElement> getElementRevisions(String appId, String id, boolean includeDeleted);
    
    AppConfigElement getElementRevision(String appId, String id, long revision);
    
    VersionHolder saveElementRevision(AppConfigElement element);
    
    void deleteElementRevisionPermanently(String appId, String id, long revision);

}
