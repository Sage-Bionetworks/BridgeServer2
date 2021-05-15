package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_ASSESSMENTS_ERROR;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUtils.CustomServletInputStream;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.services.AssessmentConfigService;

public class AssessmentConfigControllerTest extends Mockito {
    
    @Mock
    AssessmentConfigService mockService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Spy
    @InjectMocks
    AssessmentConfigController controller;
    
    @Captor
    ArgumentCaptor<AssessmentConfig> configCaptor;
    
    @Captor
    ArgumentCaptor<Map<String, Map<String, JsonNode>>> updatesCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(mockRequest).when(controller).request();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AssessmentConfigController.class);
        assertGet(AssessmentConfigController.class, "getAssessmentConfig");
        assertPost(AssessmentConfigController.class, "updateAssessmentConfig");
        assertPost(AssessmentConfigController.class, "customizeAssessmentConfig");
    }
    
    @Test
    public void getAssessmentConfig() {
        doReturn(session).when(controller).getAuthenticatedSession();
        AssessmentConfig config = new AssessmentConfig();
        when(mockService.getAssessmentConfig(TEST_APP_ID, GUID)).thenReturn(config);
        
        AssessmentConfig retValue = controller.getAssessmentConfig(GUID);
        assertSame(retValue, config);
        
        verify(mockService).getAssessmentConfig(TEST_APP_ID, GUID);
    }
    
    @Test
    public void updateAssessmentConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(TestUtils.getClientData());
        when(mockService.updateAssessmentConfig(eq(TEST_APP_ID), eq(GUID), any())).thenReturn(config);
        
        mockRequestBody(mockRequest, config);
        
        AssessmentConfig retValue = controller.updateAssessmentConfig(GUID);
        assertSame(retValue, config);
        
        verify(mockService).updateAssessmentConfig(eq(TEST_APP_ID), eq(GUID), configCaptor.capture());
        
        AssessmentConfig captured = configCaptor.getValue();
        assertEquals(captured.getConfig().toString(), TestUtils.getClientData().toString());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void updateAssessmentConfigRejectsSharedApp() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        session.setAppId(SHARED_APP_ID);
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(TestUtils.getClientData());
        mockRequestBody(mockRequest, config);
        
        controller.updateAssessmentConfig(GUID);
    }
    
    @Test
    public void customizeAssessmentConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        AssessmentConfig config = new AssessmentConfig();
        config.setConfig(TestUtils.getClientData());
        when(mockService.customizeAssessmentConfig(eq(TEST_APP_ID), eq(GUID), any())).thenReturn(config);
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        updates.put("guid", ImmutableMap.of("objGuid", TestUtils.getClientData()));
        mockRequestBody(mockRequest, updates);
        
        AssessmentConfig retValue = controller.customizeAssessmentConfig(GUID);
        assertSame(retValue, config);
        
        verify(mockService).customizeAssessmentConfig(eq(TEST_APP_ID), eq(GUID), updatesCaptor.capture());
        
        Map<String, Map<String, JsonNode>> captured = updatesCaptor.getValue();
        assertEquals(captured.get("guid").get("objGuid"), TestUtils.getClientData());
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void customizeAssessmentConfigRejectsSharedApp() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        session.setAppId(SHARED_APP_ID);
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        updates.put("guid", ImmutableMap.of("objGuid", TestUtils.getClientData()));
        mockRequestBody(mockRequest, updates);
        
        controller.customizeAssessmentConfig(GUID);
    }
    
    // nulls are a positive signal that a property should be deleted from the config,
    // so in this call we use a different ObjectMapper to capture these.
    @Test
    public void customizeAsessmentConfigPreservesNulls() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        Map<String, Map<String, JsonNode>> updates = new HashMap<>();
        
        Map<String, JsonNode> nodeUpdates = new HashMap<>();
        nodeUpdates.put("objGuid", null);
        updates.put("guid", nodeUpdates);
        
        ServletInputStream stream = new CustomServletInputStream("{\"guid\":{\"objGuid\":null}}");
        when(mockRequest.getInputStream()).thenReturn(stream);
        
        controller.customizeAssessmentConfig(GUID);
        
        verify(mockService).customizeAssessmentConfig(eq(TEST_APP_ID), 
                eq(GUID), updatesCaptor.capture());
        
        Map<String, Map<String, JsonNode>> captured = updatesCaptor.getValue();
        assertTrue(captured.get("guid").containsKey("objGuid"));
        assertTrue(captured.get("guid").get("objGuid").isNull());
    }
}
