package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;

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
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentDto;
import org.sagebionetworks.bridge.services.AssessmentService;

@CrossOrigin
@RestController
public class AssessmentController extends BaseController {
    static final String SHARED_ASSESSMENTS_ERROR = "Only shared assessment APIs are enabled for the shared assessment library.";
    
    private AssessmentService service;
    
    @Autowired
    final void setAssessmentService(AssessmentService service) {
        this.service = service;
    }
    
    @GetMapping("/v1/assessments")
    public PagedResourceList<AssessmentDto> getAssessments(@RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize,
            @RequestParam(name = "cat", required = false) Set<String> categories,
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
        
        PagedResourceList<Assessment> page = service.getAssessments(
                appId, offsetByInt, pageSizeInt, categories, tags, incDeletedBool);
        
        List<AssessmentDto> dtos = page.getItems().stream()
                .map(assessment -> AssessmentDto.create(assessment))
                .collect(toList());
        
        return new PagedResourceList<>(dtos, page.getTotal())
                .withAllRequestParams(page.getRequestParams());
    }
    
    @PostMapping("/v1/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentDto createAssessment() {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }
        
        AssessmentDto dto = parseJson(AssessmentDto.class);
        Assessment assessment = Assessment.create(dto, appId);
        assessment.setAppId(appId);
        
        Assessment retValue = service.createAssessment(appId, assessment);
        return AssessmentDto.create(retValue);        
    }   

    @GetMapping("/v1/assessments/{guid}")
    public AssessmentDto getAssessmentByGuid(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        Assessment retValue = service.getAssessmentByGuid(appId, guid);
        return AssessmentDto.create(retValue);        
    }
    
    @PostMapping("/v1/assessments/{guid}")
    public AssessmentDto updateAssessmentByGuid(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        AssessmentDto dto = parseJson(AssessmentDto.class);
        Assessment assessment = Assessment.create(dto, appId);
        assessment.setGuid(guid);
        
        Assessment retValue = service.updateAssessment(appId, assessment);
        return AssessmentDto.create(retValue);
    }
    
    @GetMapping("/v1/assessments/{guid}/revisions")
    public PagedResourceList<AssessmentDto> getAssessmentRevisionsByGuid(@PathVariable String guid,
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

        PagedResourceList<Assessment> page = service.getAssessmentRevisionsByGuid(
                appId, guid, offsetByInt, pageSizeInt, incDeletedBool);
        
        List<AssessmentDto> dtos = page.getItems().stream()
                .map(assessment -> AssessmentDto.create(assessment))
                .collect(toList());
        
        return new PagedResourceList<>(dtos, page.getTotal())
                .withAllRequestParams(page.getRequestParams());
    }
    
    @PostMapping("/v1/assessments/{guid}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentDto createAssessmentRevision(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        AssessmentDto dto = parseJson(AssessmentDto.class);
        Assessment assessment = Assessment.create(dto, appId);
        assessment.setAppId(appId);
        assessment.setGuid(guid);
        
        Assessment retValue = service.createAssessmentRevision(appId, assessment);
        return AssessmentDto.create(retValue);        
    }
    
    @PostMapping("/v1/assessments/{guid}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentDto publishAssessment(@PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        Assessment retValue = service.publishAssessment(appId, guid);
        return AssessmentDto.create(retValue);
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
    public AssessmentDto getLatestAssessment(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        Assessment retValue = service.getLatestAssessment(appId, identifier);
        return AssessmentDto.create(retValue);
    }
    
    @GetMapping("/v1/assessments/identifier:{identifier}/revisions")
    public PagedResourceList<AssessmentDto> getAssessmentRevisionsById(@PathVariable String identifier,
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
        
        PagedResourceList<Assessment> page = service.getAssessmentRevisionsById(
                appId, identifier, offsetByInt, pageSizeInt, incDeletedBool);

        List<AssessmentDto> dtos = page.getItems().stream()
                .map(assessment -> AssessmentDto.create(assessment))
                .collect(Collectors.toList());
        
        return new PagedResourceList<>(dtos, page.getTotal())
                .withAllRequestParams(page.getRequestParams());
    }
    
    @GetMapping("/v1/assessments/identifier:{identifier}/revisions/{revision}")
    public AssessmentDto getAssessmentById(@PathVariable String identifier, @PathVariable String revision) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        String appId = session.getStudyIdentifier().getIdentifier();
        if (SHARED_STUDY_ID_STRING.equals(appId)) {
            throw new UnauthorizedException(SHARED_ASSESSMENTS_ERROR);
        }

        // 0 is not a valid value, on purpose, will throw BadRequestException
        int revisionInt = BridgeUtils.getIntOrDefault(revision, 0);
        
        Assessment assessment = service.getAssessmentById(appId, identifier, revisionInt);
        
        return AssessmentDto.create(assessment);
    }
}
