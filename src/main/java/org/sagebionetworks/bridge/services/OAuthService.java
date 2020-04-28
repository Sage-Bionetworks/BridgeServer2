package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_KEY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dao.OAuthAccessGrantDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.OAuthProvider;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessGrant;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OAuthService {
    
    private OAuthAccessGrantDao grantDao;
    
    private OAuthProviderService providerService;
    
    @Autowired
    final void setOAuthAccessGrantDao(OAuthAccessGrantDao grantDao) {
        this.grantDao = grantDao;
    }
    
    @Autowired
    final void setOAuthProviderService(OAuthProviderService providerService) {
        this.providerService = providerService;
    }
    
    // Accessor to be mocked in tests
    protected DateTime getDateTime() {
        return DateTime.now(DateTimeZone.UTC);
    }
    
    public ForwardCursorPagedResourceList<String> getHealthCodesGrantingAccess(App app, String vendorId,
            int pageSize, String offsetKey) {
        checkNotNull(app);
        checkNotNull(vendorId);
        
        // Verify the provider exists
        OAuthProvider provider = app.getOAuthProviders().get(vendorId);
        if (provider == null) {
            throw new EntityNotFoundException(OAuthProvider.class);
        }
        ForwardCursorPagedResourceList<OAuthAccessGrant> list = grantDao.getAccessGrants(app.getIdentifier(), vendorId,
                offsetKey, pageSize);

        List<String> healthCodes = list.getItems().stream().map(OAuthAccessGrant::getHealthCode)
                .collect(Collectors.toList());
        
        return new ForwardCursorPagedResourceList<String>(healthCodes, list.getNextPageOffsetKey())
                .withRequestParam(PAGE_SIZE, pageSize)
                .withRequestParam(OFFSET_KEY, offsetKey);
    }
    
    public OAuthAccessToken requestAccessToken(App app, String healthCode, OAuthAuthorizationToken authToken) {
        checkNotNull(app);
        checkNotNull(healthCode);
        checkNotNull(authToken);
        
        return retrieveAccessToken(app, authToken.getVendorId(), healthCode, authToken);
    }
    
    public OAuthAccessToken getAccessToken(App app, String vendorId, String healthCode) {
        checkNotNull(app);
        checkNotNull(vendorId);
        checkNotNull(healthCode);
        
        return retrieveAccessToken(app, vendorId, healthCode, null);
    }
    
    private OAuthAccessToken retrieveAccessToken(App app, String vendorId, String healthCode,
            OAuthAuthorizationToken authToken) {
        checkNotNull(app);
        checkNotNull(vendorId);
        checkNotNull(healthCode);
        
        OAuthProvider provider = app.getOAuthProviders().get(vendorId);
        if (provider == null) {
            throw new EntityNotFoundException(OAuthProvider.class);
        }
        OAuthAccessGrant grant = null;
        
        try {
            // If client has submitted an authorization token, we always refresh the grant
            if (authToken != null && authToken.getAuthToken() != null) {
                grant = providerService.requestAccessGrant(provider, authToken);
            } else {
                // If not, start first by seeing if a grant has been saved
                grant = grantDao.getAccessGrant(app.getIdentifier(), vendorId, healthCode);
            }
            // If no grant was saved or successfully returned from a grant, it's not found.
            if (grant == null) {
                throw new EntityNotFoundException(OAuthAccessGrant.class);
            } else if (getDateTime().isAfter(grant.getExpiresOn())) {
                // If there's a grant record, but it has expired, attempt to refresh it
                grant = providerService.refreshAccessGrant(provider, vendorId, grant.getRefreshToken());
            }
        } catch(BridgeServiceException e) {
            // 502, 503, and 504 are potentially transient errors, but other server errors, delete the grant.
            // It is in an unknown state.
            if (e.getStatusCode() < 502 || e.getStatusCode() > 504) {
                grantDao.deleteAccessGrant(app.getIdentifier(), vendorId, healthCode);
            }
            throw e;
        }
        grant.setVendorId(vendorId);
        grant.setHealthCode(healthCode);
        grantDao.saveAccessGrant(app.getIdentifier(), grant);
        return getTokenForGrant(grant);
    }
    
    private OAuthAccessToken getTokenForGrant(OAuthAccessGrant grant) {
        DateTime expiresOn = new DateTime(grant.getExpiresOn(), DateTimeZone.UTC);
        return new OAuthAccessToken(grant.getVendorId(), grant.getAccessToken(), expiresOn, grant.getProviderUserId(),
                grant.getScopes());
    }
}
