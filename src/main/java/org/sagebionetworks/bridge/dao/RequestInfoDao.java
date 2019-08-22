package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.RequestInfo;

public interface RequestInfoDao {

    /**
     * Take existing data in the request info object and augment with any new information 
     * in the request info object passed as a parameter, then persist that. Different calls
     * contribute some different fields to the total RequestInfo object.
     */    
    public void updateRequestInfo(RequestInfo requestInfo);
    
    public RequestInfo getRequestInfo(String userId);
    
    public void removeRequestInfo(String userId);
    
}
