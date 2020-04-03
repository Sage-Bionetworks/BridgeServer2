package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.services.AssessmentConfigService;

@CrossOrigin
@RestController
public class SharedAssessmentConfigController extends BaseController {
    
    private AssessmentConfigService service;
    
    @Autowired
    final void setAssessmentConfigService(AssessmentConfigService service) {
        this.service = service;
    }
    
    @GetMapping("/v1/sharedassessments/{guid}/config")
    public AssessmentConfig getSharedAssessmentConfig(@PathVariable String guid) {
        return service.getAssessmentConfig(SHARED_STUDY_ID_STRING, guid);
    }
}