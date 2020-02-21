package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentDto;
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
    public AssessmentDto importAssessment(@PathVariable String guid, @RequestParam(required = false) String ownerId) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getStudyIdentifier().getIdentifier();
        
        Assessment assessment = service.importAssessment(appId, ownerId, guid);
        return AssessmentDto.create(assessment);
    }
    
    @GetMapping("/v1/sharedassessments")
    public PagedResourceList<AssessmentDto> getSharedAssessments(
            @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false, name = "tag") Set<String> tags,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        PagedResourceList<Assessment> page = service.getAssessments(
                SHARED_STUDY_ID_STRING, offsetByInt, pageSizeInt, tags, incDeletedBool);
        
        List<AssessmentDto> dtos = page.getItems().stream()
                .map(assessment -> AssessmentDto.create(assessment))
                .collect(toList());
        
        return new PagedResourceList<>(dtos, page.getTotal())
                .withAllRequestParams(page.getRequestParams());
    }
    
    @GetMapping("/v1/sharedassessments/{guid}")
    public AssessmentDto getSharedAssessmentByGuid(@PathVariable String guid) {
        Assessment retValue = service.getAssessmentByGuid(SHARED_STUDY_ID_STRING, guid);
        return AssessmentDto.create(retValue);        
    }
    
    @GetMapping("/v1/sharedassessments/identifier:{identifier}")
    public AssessmentDto getLatestSharedAssessment(@PathVariable String identifier) {
        Assessment retValue = service.getLatestAssessment(SHARED_STUDY_ID_STRING, identifier);
        return AssessmentDto.create(retValue);
    }

    @GetMapping("/v1/sharedassessments/identifier:{identifier}/revisions/{revision}")
    public AssessmentDto getSharedAssessmentById(@PathVariable String identifier, @PathVariable String revision) {
        // 0 is not a valid value, on purpose, will throw BadRequestException
        int revisionInt = BridgeUtils.getIntOrDefault(revision, 0);
        
        Assessment retValue = service.getAssessmentById(SHARED_STUDY_ID_STRING, identifier, revisionInt);
        return AssessmentDto.create(retValue);
    }

    @GetMapping("/v1/sharedassessments/{guid}/revisions")
    public PagedResourceList<AssessmentDto> getSharedAssessmentRevisionsByGuid(
            @PathVariable String guid,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        PagedResourceList<Assessment> page = service.getAssessmentRevisionsByGuid(
                SHARED_STUDY_ID_STRING, guid, offsetByInt, pageSizeInt, incDeletedBool);

        List<AssessmentDto> dtos = page.getItems().stream()
                .map(assessment -> AssessmentDto.create(assessment))
                .collect(Collectors.toList());
        
        return new PagedResourceList<>(dtos, page.getTotal())
                .withAllRequestParams(page.getRequestParams());
    }

    @GetMapping("/v1/sharedassessments/identifier:{identifier}/revisions")
    public PagedResourceList<AssessmentDto> getSharedAssessmentRevisionsById(
            @PathVariable String identifier,
            @RequestParam(required = false) String offsetBy, 
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        PagedResourceList<Assessment> page = service.getAssessmentRevisionsById(
                SHARED_STUDY_ID_STRING, identifier, offsetByInt, pageSizeInt, incDeletedBool);

        List<AssessmentDto> dtos = page.getItems().stream()
                .map(assessment -> AssessmentDto.create(assessment))
                .collect(Collectors.toList());
        
        return new PagedResourceList<>(dtos, page.getTotal())
                .withAllRequestParams(page.getRequestParams());
    }

    @PostMapping("/v1/sharedassessments/{guid}")
    public AssessmentDto updateSharedAssessment(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getStudyIdentifier().getIdentifier();
        
        AssessmentDto dto = parseJson(AssessmentDto.class);
        Assessment assessment = Assessment.create(dto, SHARED_STUDY_ID_STRING);
        
        // Note that we are passing in the appId of the caller, and the assessment is in the 
        // shared app, which is the opposite of all the other shared calls
        Assessment retValue = service.updateSharedAssessment(appId, assessment);
        
        return AssessmentDto.create(retValue);
    }
    
    @DeleteMapping("/v1/sharedassessments/{guid}")
    public StatusMessage deleteSharedAssessment(@PathVariable String guid,
            @RequestParam(required = false) String physical) {
        getAuthenticatedSession(SUPERADMIN);
        
        if ("true".equals(physical)) {
            service.deleteAssessmentPermanently(SHARED_STUDY_ID_STRING, guid);
        } else {
            service.deleteAssessment(SHARED_STUDY_ID_STRING, guid);
        }
        return new StatusMessage("Shared assessment deleted.");        
    }    
}
