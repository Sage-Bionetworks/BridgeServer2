package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

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

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
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

        Integer pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        Boolean assignmentFilterBool = Boolean.valueOf(assignmentFilter);
        
        return externalIdService.getExternalIds(offsetKey, pageSizeInt, idFilter, assignmentFilterBool);
    }

    @PostMapping("/v4/externalids")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusMessage createExternalIdentifier() {
        getAuthenticatedSession(DEVELOPER, RESEARCHER);
        
        ExternalIdentifier externalIdentifier = parseJson(ExternalIdentifier.class);
        externalIdService.createExternalId(externalIdentifier, false);
        
        return new StatusMessage("External identifier created.");
    }
    
    @DeleteMapping("/v4/externalids/{externalId}")
    public StatusMessage deleteExternalIdentifier(@PathVariable String externalId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), externalId);        
        externalIdService.deleteExternalIdPermanently(study, externalIdentifier);
        
        return new StatusMessage("External identifier deleted.");
    }
    
    @PostMapping("/v3/externalids/{externalId}/password")
    public GeneratedPassword generatePassword(@PathVariable String externalId,
            @RequestParam(required = false, defaultValue = "true") String createAccount) throws Exception {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER);
        
        Boolean createAccountBool = Boolean.valueOf(createAccount);
        
        Study study = studyService.getStudy(session.getStudyIdentifier());
        return authenticationService.generatePassword(study, externalId, createAccountBool);
    }
}
