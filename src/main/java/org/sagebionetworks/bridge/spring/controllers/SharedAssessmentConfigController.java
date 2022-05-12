package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.services.AssessmentConfigService;
import org.sagebionetworks.bridge.spring.util.EtagCacheKey;
import org.sagebionetworks.bridge.spring.util.EtagSupport;

@CrossOrigin
@RestController
public class SharedAssessmentConfigController extends BaseController {
    
    @Autowired
    private AssessmentConfigService service;
    
    @EtagSupport({
        // Most recent modification to the configuration
        @EtagCacheKey(model=AssessmentConfig.class, keys={"guid"})
    })
    @GetMapping("/v1/sharedassessments/{guid}/config")
    public AssessmentConfig getSharedAssessmentConfig(@PathVariable String guid) {
        return service.getAssessmentConfig(SHARED_APP_ID, guid);
    }
}