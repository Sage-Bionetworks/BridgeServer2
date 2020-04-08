package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.testng.Assert.assertSame;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.services.AssessmentConfigService;

public class SharedAssessmentConfigControllerTest extends Mockito {
    
    @Mock
    AssessmentConfigService mockService;
    
    @InjectMocks
    SharedAssessmentConfigController controller;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SharedAssessmentConfigController.class);
        assertGet(SharedAssessmentConfigController.class, "getSharedAssessmentConfig");
    }
    
    @Test
    public void getSharedAssessmentConfig() {
        AssessmentConfig config = new AssessmentConfig();
        when(mockService.getAssessmentConfig(SHARED_APP_ID, GUID)).thenReturn(config);
        
        AssessmentConfig retValue = controller.getSharedAssessmentConfig(GUID);
        assertSame(retValue, config);
        
        verify(mockService).getAssessmentConfig(SHARED_APP_ID, GUID);
    }
}
