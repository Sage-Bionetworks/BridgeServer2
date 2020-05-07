package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.apps.App;

public interface AppDao {

    boolean doesIdentifierExist(String appId);
    
    App getApp(String appId);
    
    List<App> getApps();
    
    App createApp(App app);
    
    App updateApp(App app);
    
    void deleteApp(App app);

    void deactivateApp(String appId);
}
