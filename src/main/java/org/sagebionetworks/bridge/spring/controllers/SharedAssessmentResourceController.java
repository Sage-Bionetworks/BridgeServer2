package org.sagebionetworks.bridge.spring.controllers;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.STRING_SET_TYPEREF;
import static org.sagebionetworks.bridge.BridgeUtils.getEnumOrDefault;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.List;
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
import org.sagebionetworks.bridge.models.ResourceList;
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
        return service.getResources(SHARED_APP_ID, null, assessmentId, offsetByInt, pageSizeInt, categories,
                minRevisionInt, maxRevisionInt, incDeletedBool);
    }
    
    @GetMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/{guid}")
    public AssessmentResource getAssessmentResource(@PathVariable String assessmentId, @PathVariable String guid) {
        return service.getResource(SHARED_APP_ID, null, assessmentId, guid);
    }

    @PostMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/{guid}")
    public AssessmentResource updateAssessmentResource(@PathVariable String assessmentId, @PathVariable String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        String appId = session.getAppId();
        
        AssessmentResource resource = parseJson(AssessmentResource.class);
        resource.setGuid(guid);
        
        return service.updateSharedResource(appId, assessmentId, resource);
    }

    @DeleteMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/{guid}")
    public StatusMessage deleteAssessmentResource(@PathVariable String assessmentId, @PathVariable String guid,
            @RequestParam(required = false) String physical) {
        getAuthenticatedSession(SUPERADMIN);
        
        if ("true".equals(physical)) {
            service.deleteResourcePermanently(SHARED_APP_ID, assessmentId, guid);
        } else {
            service.deleteResource(SHARED_APP_ID, null, assessmentId, guid);
        }
        return new StatusMessage("Assessment resource deleted.");        
    }
    
    @PostMapping("/v1/sharedassessments/identifier:{assessmentId}/resources/import")
    public ResourceList<AssessmentResource> importAssessmentResources(@PathVariable String assessmentId) {
        UserSession session = getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        String appId = session.getAppId();
        String ownerId = session.getParticipant().getOrgMembership();

        Set<String> resourceGuids = parseJson(STRING_SET_TYPEREF);
        
        List<AssessmentResource> resources = service.importAssessmentResources(appId, ownerId, assessmentId, resourceGuids);
        return new ResourceList<AssessmentResource>(resources, true);
    }
}
