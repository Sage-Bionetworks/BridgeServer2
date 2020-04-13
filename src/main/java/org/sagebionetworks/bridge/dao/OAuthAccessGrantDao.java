package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;

public interface OAuthAccessGrantDao {

    ForwardCursorPagedResourceList<OAuthAccessGrant> getAccessGrants(String studyId, String vendorId, String offsetKey,
            int pageSize);
    
    public OAuthAccessGrant getAccessGrant(String studyId, String vendorId, String healthCode);
    
    public OAuthAccessGrant saveAccessGrant(String studyId, OAuthAccessGrant grant);
    
    public void deleteAccessGrant(String studyId, String vendorId, String healthCode);
    
}
