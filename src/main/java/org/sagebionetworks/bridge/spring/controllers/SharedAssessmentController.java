package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.services.AssessmentService.OFFSET_NOT_POSITIVE;

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
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getStudyIdentifier().getIdentifier();
        
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
                SHARED_APP_ID, offsetByInt, pageSizeInt, tags, incDeletedBool);
    }
    
    @GetMapping("/v1/sharedassessments/{guid}")
    public Assessment getSharedAssessmentByGuid(@PathVariable String guid) {
        return service.getAssessmentByGuid(SHARED_APP_ID, guid);
    }
    
    @GetMapping("/v1/sharedassessments/identifier:{identifier}")
    public Assessment getLatestSharedAssessment(@PathVariable String identifier) {
        return service.getLatestAssessment(SHARED_APP_ID, identifier);
    }

    @GetMapping("/v1/sharedassessments/identifier:{identifier}/revisions/{revision}")
    public Assessment getSharedAssessmentById(@PathVariable String identifier, @PathVariable String revision) {
        int revisionInt = BridgeUtils.getIntOrDefault(revision, 0);
        if (revisionInt < 1) {
            throw new BadRequestException(OFFSET_NOT_POSITIVE);
        }
        return service.getAssessmentById(SHARED_APP_ID, identifier, revisionInt);
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
                SHARED_APP_ID, guid, offsetByInt, pageSizeInt, incDeletedBool);
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
                SHARED_APP_ID, identifier, offsetByInt, pageSizeInt, incDeletedBool);
    }

    @PostMapping("/v1/sharedassessments/{guid}")
    public Assessment updateSharedAssessment(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getStudyIdentifier().getIdentifier();
        
        Assessment assessment = parseJson(Assessment.class);
        assessment.setGuid(guid);
        
        // Note that we are passing in the appId of the caller, and the assessment is in the 
        // shared app, which is the opposite of all the other shared calls
        return service.updateSharedAssessment(appId, assessment);
    }
    
    @DeleteMapping("/v1/sharedassessments/{guid}")
    public StatusMessage deleteSharedAssessment(@PathVariable String guid,
            @RequestParam(required = false) String physical) {
        getAuthenticatedSession(SUPERADMIN);
        
        if ("true".equals(physical)) {
            service.deleteAssessmentPermanently(SHARED_APP_ID, guid);
        } else {
            service.deleteAssessment(SHARED_APP_ID, guid);
        }
        return new StatusMessage("Shared assessment deleted.");        
    }    
}
