package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;

public interface OAuthAccessGrantDao {

    ForwardCursorPagedResourceList<OAuthAccessGrant> getAccessGrants(String appId, String vendorId, String offsetKey,
            int pageSize);
    
    public OAuthAccessGrant getAccessGrant(String appId, String vendorId, String healthCode);
    
    public OAuthAccessGrant saveAccessGrant(String appId, OAuthAccessGrant grant);
    
    public void deleteAccessGrant(String appId, String vendorId, String healthCode);
    
}
