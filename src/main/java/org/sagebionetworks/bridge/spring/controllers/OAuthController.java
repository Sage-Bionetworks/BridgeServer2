package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.WORKER;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.oauth.OAuthAccessToken;
import org.sagebionetworks.bridge.models.oauth.OAuthAuthorizationToken;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.services.OAuthService;

@CrossOrigin
@RestController
public class OAuthController extends BaseController {
    
    private static final String AUTH_TOKEN = "authToken";
    private OAuthService service;
    
    @Autowired
    final void setOAuthService(OAuthService service) {
        this.service = service;
    }

    @PostMapping("/v3/oauth/{vendorId}")
    public OAuthAccessToken requestAccessToken(@PathVariable String vendorId) {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        JsonNode node = parseJson(JsonNode.class);
        String token = node.has(AUTH_TOKEN) ? node.get(AUTH_TOKEN).textValue() : null;
        OAuthAuthorizationToken authToken = new OAuthAuthorizationToken(null, vendorId, token, null);
        
        App app = studyService.getStudy(session.getAppId());
        
        return service.requestAccessToken(app, session.getHealthCode(), authToken);
    }
    
    @GetMapping(path = {"/v1/apps/{appId}/oauth/{vendorId}", 
            "/v3/studies/{appId}/oauth/{vendorId}"})
    public ForwardCursorPagedResourceList<String> getHealthCodesGrantingAccess(@PathVariable String appId,
            @PathVariable String vendorId, @RequestParam(required = false) String offsetKey,
            @RequestParam(required = false) String pageSize) {
        getAuthenticatedSession(WORKER);
        
        App app = studyService.getStudy(appId);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return service.getHealthCodesGrantingAccess(app, vendorId, pageSizeInt, offsetKey);
    }
    
    @GetMapping(path = {"/v1/apps/{appId}/oauth/{vendorId}/{healthCode}", 
            "/v3/studies/{appId}/oauth/{vendorId}/{healthCode}"})
    public OAuthAccessToken getAccessToken(@PathVariable String appId, @PathVariable String vendorId,
            @PathVariable String healthCode) {
        getAuthenticatedSession(WORKER);
        
        App app = studyService.getStudy(appId);
        return service.getAccessToken(app, vendorId, healthCode);
    }
}
