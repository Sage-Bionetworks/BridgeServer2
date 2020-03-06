package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.BridgeUtils.getEnumOrDefault;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.ResourceCategory;
import org.sagebionetworks.bridge.services.AssessmentResourceService;

@CrossOrigin
@RestController
public class SharedAssessmentResourceController extends BaseController {

    private AssessmentResourceService service;
    
    @Autowired
    final void setAssessmentResourceService(AssessmentResourceService service) {
        this.service = service;
    }
    
    @GetMapping("/v1/sharedassessments/identifier:{assessmentId}/resources")
    public PagedResourceList<AssessmentResource> getAssessmentResources(@PathVariable String assessmentId, 
            @RequestParam(required = false) String offsetBy,
            @RequestParam(required = false) String pageSize,
            @RequestParam(name = "category", required = false) Set<String> cats,
            @RequestParam(required = false) String minRevision,
            @RequestParam(required = false) String maxRevision,
            @RequestParam(required = false) String includeDeleted) {
        
        int offsetByInt = BridgeUtils.getIntOrDefault(offsetBy, 0);
        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);
        Integer minRevisionInt = BridgeUtils.getIntegerOrDefault(minRevision, null);
        Integer maxRevisionInt = BridgeUtils.getIntegerOrDefault(maxRevision, null);
        boolean incDeletedBool = Boolean.valueOf(includeDeleted);
        
        Set<ResourceCategory> categories = null;
        if (cats != null) {
            categories = cats.stream()
                .map(s -> getEnumOrDefault(s, ResourceCategory.class, null))
                .collect(toSet());
        }
        return service.getResources(SHARED_STUDY_ID_STRING, assessmentId, offsetByInt, pageSizeInt, categories,
                minRevisionInt, maxRevisionInt, incDeletedBool);
    }
    
    @GetMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/{guid}")
    public AssessmentResource getAssessmentResource(@PathVariable String assessmentId, @PathVariable String guid) {
        return service.getResource(SHARED_STUDY_ID_STRING, assessmentId, guid);
    }

    @PostMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/{guid}")
    public AssessmentResource updateAssessmentResource(@PathVariable String assessmentId, @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String appId = session.getStudyIdentifier().getIdentifier();
        
        AssessmentResource resource = parseJson(AssessmentResource.class);
        resource.setGuid(guid);
        
        return service.updateResource(appId, assessmentId, resource);
    }

    @DeleteMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/{guid}")
    public StatusMessage deleteAssessmentResource(@PathVariable String assessmentId, @PathVariable String guid,
            @RequestParam(required = false) String physical) {
        UserSession session = getAuthenticatedSession(SUPERADMIN);
        String appId = session.getStudyIdentifier().getIdentifier();
        
        if ("true".equals(physical)) {
            service.deleteResourcePermanently(appId, assessmentId, guid);
        } else {
            service.deleteResource(appId, assessmentId, guid);
        }
        return new StatusMessage("Assessment resource deleted.");        
    }
}
