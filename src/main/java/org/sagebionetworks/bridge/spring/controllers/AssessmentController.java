package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_ASSESSMENTS_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
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
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.services.AssessmentService;

@CrossOrigin
@RestController
public class AssessmentController extends BaseController {
    
    private AssessmentService service;
    
    @Autowired
    final void setAssessmentService(AssessmentService service) {
        this.service = service;
    }
    
    @GetMapping("/v1/assessments")
    public PagedResourceList<Assessment> getAssessments(@RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize,
            @RequestParam(name = "tag", required = false) Set<String> tags,
            @RequestParam(required = false) String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        return service.getAssessments(appId, offsetByInt, pageSizeInt, tags, incDeletedBool);
    }
    
    @PostMapping("/v1/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public Assessment createAssessment() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }
        
        Assessment assessment = parseJson(Assessment.class);
        return service.createAssessment(appId, assessment);
    }   

    @GetMapping("/v1/assessments/{guid}")
    public Assessment getAssessmentByGuid(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }
        return service.getAssessmentByGuid(appId, guid);
    }
    
    @PostMapping("/v1/assessments/{guid}")
    public Assessment updateAssessmentByGuid(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }
        Assessment assessment = parseJson(Assessment.class);
        assessment.setGuid(guid);
        
        return service.updateAssessment(appId, assessment);
    }
    
    @GetMapping("/v1/assessments/{guid}/revisions")
    public PagedResourceList<Assessment> getAssessmentRevisionsByGuid(@PathVariable String guid,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);

        return service.getAssessmentRevisionsByGuid(
                appId, guid, offsetByInt, pageSizeInt, incDeletedBool);
    }
    
    @PostMapping("/v1/assessments/{guid}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public Assessment createAssessmentRevision(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        Assessment assessment = parseJson(Assessment.class);
        // do not set the GUID, it's the GUID of another revision, not
        // the one we're about to create... the GUID is used in the
        // service to ensure this assessment is associated to the GUID
        // in the URL, which always takes precedence over what is in 
        // the JSON in the body of a request.
        
        return service.createAssessmentRevision(appId, guid, assessment);
    }
    
    @PostMapping("/v1/assessments/{guid}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public Assessment publishAssessment(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        return service.publishAssessment(appId, guid);
    }
        
    @DeleteMapping("/v1/assessments/{guid}")
    public StatusMessage deleteAssessment(@PathVariable String guid, @RequestParam(required = false) String physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        if ("true".equals(physical) && session.isInRole(ADMIN)) {
            service.deleteAssessmentPermanently(appId, guid);
        } else {
            service.deleteAssessment(appId, guid);
        }
        return new StatusMessage("Assessment deleted.");        
    }

    /* === Methods for working with an identifier, not a GUID === */
        
    @GetMapping("/v1/assessments/identifier:{identifier}")
    public Assessment getLatestAssessment(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        return service.getLatestAssessment(appId, identifier);
    }
    
    @GetMapping("/v1/assessments/identifier:{identifier}/revisions")
    public PagedResourceList<Assessment> getAssessmentRevisionsById(@PathVariable String identifier,
            @RequestParam(required = false) String offsetBy, @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        return service.getAssessmentRevisionsById(
                appId, identifier, offsetByInt, pageSizeInt, incDeletedBool);
    }
    
    @GetMapping("/v1/assessments/identifier:{identifier}/revisions/{revision}")
    public Assessment getAssessmentById(@PathVariable String identifier, @PathVariable String revision) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        int revisionInt = BridgeUtils.getIntOrDefault(revision, 0);
        if (revisionInt < 1) {
            throw new BadRequestException(OFFSET_NOT_POSITIVE);
        }
        return service.getAssessmentById(appId, identifier, revisionInt);
    }
}
