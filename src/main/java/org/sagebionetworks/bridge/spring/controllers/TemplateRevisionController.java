package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeUtils.getDateTimeOrDefault;
import static org.sagebionetworks.bridge.BridgeUtils.getIntOrDefault;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.springframework.http.HttpStatus.CREATED;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.CreatedOnHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.TemplateRevisionService;

@CrossOrigin
@RestController
public class TemplateRevisionController extends BaseController {
    
    static final StatusMessage PUBLISHED_MSG = new StatusMessage("Template revision published.");

    private TemplateRevisionService templateRevisionService;
    
    @Autowired
    final void setTemplateRevisionService(TemplateRevisionService templateRevisionService) {
        this.templateRevisionService = templateRevisionService;
    }
    
    @GetMapping("/v3/templates/{guid}/revisions")
    public PagedResourceList<? extends TemplateRevision> getTemplateRevisions(@PathVariable String guid,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        int offsetInt = getIntOrDefault(offsetBy, 0);
        int pageSizeInt = getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        
        return templateRevisionService.getTemplateRevisions(session.getAppId(), guid, offsetInt, pageSizeInt);
    }
    
    @PostMapping("/v3/templates/{guid}/revisions")
    @ResponseStatus(CREATED)
    public CreatedOnHolder createTemplateRevision(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        TemplateRevision revision = parseJson(TemplateRevision.class);
        
        return templateRevisionService.createTemplateRevision(session.getAppId(), guid, revision);
    }
    
    @GetMapping("/v3/templates/{guid}/revisions/{createdOn}")
    public TemplateRevision getTemplateRevision(@PathVariable String guid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        DateTime createdOnDate = getDateTimeOrDefault(createdOn, null);
        
        return templateRevisionService.getTemplateRevision(session.getAppId(), guid, createdOnDate);
    }
    
    @PostMapping("/v3/templates/{guid}/revisions/{createdOn}/publish")
    public StatusMessage publishTemplateRevision(@PathVariable String guid, @PathVariable String createdOn) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        DateTime createdOnDate = getDateTimeOrDefault(createdOn, null);
        
        templateRevisionService.publishTemplateRevision(session.getAppId(), guid, createdOnDate);
        
        return PUBLISHED_MSG;
    }
}
