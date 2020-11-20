package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import static org.apache.http.HttpStatus.SC_GONE;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ExternalIdService;

@CrossOrigin
@RestController
public class ExternalIdControllerV4 extends BaseController {

    private ExternalIdService externalIdService;
    
    @Autowired
    final void setExternalIdService(ExternalIdService externalIdService) {
        this.externalIdService = externalIdService;
    }
    
    @GetMapping("/v4/externalids")
    public ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIdentifiers(
            @RequestParam(required = false) String offsetKey, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String idFilter, @RequestParam(required = false) String assignmentFilter) {
        getAuthenticatedSession(DEVELOPER, RESEARCHER);
        
        String message = "To retrieve external IDs use the GET /v5/studies/{studyId}/externalids API for a target study.";
        throw new BridgeServiceException(message, SC_GONE);        
    }
    
    @GetMapping("/v5/studies/{studyId}/externalids")
    public PagedResourceList<ExternalIdentifierInfo> getExternalIdentifiersForStudy(@PathVariable String studyId,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String idFilter) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);

        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return externalIdService.getPagedExternalIds(session.getAppId(), studyId, idFilter, offsetByInt, pageSizeInt);
    }

    @PostMapping("/v4/externalids")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createExternalIdentifier() {
        getAuthenticatedSession(DEVELOPER, RESEARCHER);
        
        String message = "To create an external ID use the POST /v3/participants API and include " +
                "the external ID in the 'externalIds' property map: { \"studyId\": \"newExternalId\" }";
        
        throw new BridgeServiceException(message, SC_GONE);        
    }
    
    @DeleteMapping("/v4/externalids/{externalId}")
    public StatusMessage deleteExternalIdentifier(@PathVariable String externalId) {
        UserSession session = getAuthenticatedSession(ADMIN);
        App app = appService.getApp(session.getAppId());
        
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(app.getIdentifier(), externalId);
        externalIdService.deleteExternalIdPermanently(app, externalIdentifier);
        
        return new StatusMessage("External identifier deleted from account.");
    }
    
    @PostMapping(path = {"/v3/externalids/{externalId}/password", "/v3/externalIds/{externalId}/password"})
    public GeneratedPassword generatePassword(@PathVariable String externalId) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        App app = appService.getApp(session.getAppId());
        return authenticationService.generatePassword(app, externalId);
    }
}
