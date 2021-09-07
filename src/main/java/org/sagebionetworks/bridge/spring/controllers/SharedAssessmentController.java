package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NONPOSITIVE_REVISION_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.Set;

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
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.services.AssessmentService;

@CrossOrigin
@RestController
public class SharedAssessmentController extends BaseController {

    private AssessmentService service;
    
    @Autowired
    final void setAssessmentService(AssessmentService service) {
        this.service = service;
    }
    
    @PostMapping("/v1/sharedassessments/{guid}/import")
    @ResponseStatus(HttpStatus.CREATED)
    public Assessment importAssessment(@PathVariable String guid, @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String newIdentifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        String appId = session.getAppId();
        
        return service.importAssessment(appId, ownerId, newIdentifier, guid);
    }
    
    @GetMapping("/v1/sharedassessments")
    public PagedResourceList<Assessment> getSharedAssessments(
            @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false, name = "tag") Set<String> tags,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        return service.getAssessments(
                SHARED_APP_ID, null, offsetByInt, pageSizeInt, tags, incDeletedBool);
    }
    
    @GetMapping("/v1/sharedassessments/{guid}")
    public Assessment getSharedAssessmentByGuid(@PathVariable String guid) {
        return service.getAssessmentByGuid(SHARED_APP_ID, null, guid);
    }
    
    @GetMapping("/v1/sharedassessments/identifier:{identifier}")
    public Assessment getLatestSharedAssessment(@PathVariable String identifier) {
        return service.getLatestAssessment(SHARED_APP_ID, null, identifier);
    }

    @GetMapping("/v1/sharedassessments/identifier:{identifier}/revisions/{revision}")
    public Assessment getSharedAssessmentById(@PathVariable String identifier, @PathVariable String revision) {
        int revisionInt = BridgeUtils.getIntOrDefault(revision, 0);
        if (revisionInt < 1) {
            throw new BadRequestException(NONPOSITIVE_REVISION_ERROR);
        }
        return service.getAssessmentById(SHARED_APP_ID, null, identifier, revisionInt);
    }

    @GetMapping("/v1/sharedassessments/{guid}/revisions")
    public PagedResourceList<Assessment> getSharedAssessmentRevisionsByGuid(
            @PathVariable String guid,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        return service.getAssessmentRevisionsByGuid(
                SHARED_APP_ID, null, guid, offsetByInt, pageSizeInt, incDeletedBool);
    }

    @GetMapping("/v1/sharedassessments/identifier:{identifier}/revisions")
    public PagedResourceList<Assessment> getSharedAssessmentRevisionsById(
            @PathVariable String identifier,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        return service.getAssessmentRevisionsById(
                SHARED_APP_ID, null, identifier, offsetByInt, pageSizeInt, incDeletedBool);
    }

    @PostMapping("/v1/sharedassessments/{guid}")
    public Assessment updateSharedAssessment(@PathVariable String guid) {
        getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);

        Assessment assessment = parseJson(Assessment.class);
        assessment.setGuid(guid);
        
        return service.updateSharedAssessment(assessment);
    }
    
    @DeleteMapping("/v1/sharedassessments/{guid}")
    public StatusMessage deleteSharedAssessment(@PathVariable String guid,
            @RequestParam(required = false) String physical) {
        getAuthenticatedSession(SUPERADMIN);
        
        if ("true".equals(physical)) {
            service.deleteAssessmentPermanently(SHARED_APP_ID, null, guid);
        } else {
            service.deleteAssessment(SHARED_APP_ID, null, guid);
        }
        return new StatusMessage("Shared assessment deleted.");        
    }    
}
