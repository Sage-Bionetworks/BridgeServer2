package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.springframework.http.HttpStatus.CREATED;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.services.TemplateService;

@CrossOrigin
@RestController
public class TemplateController extends BaseController {
    
    private TemplateService templateService;
    
    @Autowired
    final void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }
    
    @GetMapping("/v3/templates")
    public PagedResourceList<? extends Template> getTemplates(
            @RequestParam(name = "type", required = false) String templateType,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        if (templateType == null) {
            throw new BadRequestException("Template type is required");
        }
        TemplateType type = null;
        try {
            type = TemplateType.valueOf(templateType.toUpperCase());    
        } catch(IllegalArgumentException e) {
            throw new BadRequestException("Invalid template type: " + templateType);
        }
        
        Integer offsetInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        Integer pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        Boolean includeDeletedFlag = Boolean.valueOf(includeDeleted);
        
        return templateService.getTemplatesForType(session.getAppId(), type, offsetInt, pageSizeInt,
                includeDeletedFlag);
    }
    
    @PostMapping("/v3/templates")
    @ResponseStatus(CREATED)
    public GuidVersionHolder createTemplate() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        App app = appService.getApp(session.getAppId());
        
        Template template = parseJson(Template.class);
        
        return templateService.createTemplate(app, template);
    }
    
    @GetMapping("/v3/templates/{guid}")
    public Template getTemplate(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        return templateService.getTemplate(session.getAppId(), guid);
    }

    @PostMapping("/v3/templates/{guid}")
    public GuidVersionHolder updateTemplate(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        Template template = parseJson(Template.class);
        template.setGuid(guid);
        
        return templateService.updateTemplate(session.getAppId(), template);
    }
    
    @DeleteMapping("/v3/templates/{guid}")
    public StatusMessage deleteTemplate(@PathVariable String guid, @RequestParam String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            templateService.deleteTemplatePermanently(session.getAppId(), guid);
        } else {
            templateService.deleteTemplate(session.getAppId(), guid);
        }
        return new StatusMessage("Template deleted.");
    }
}
