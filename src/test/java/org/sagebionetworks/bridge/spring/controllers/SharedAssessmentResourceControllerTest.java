package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_ID;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.RESOURCE_CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;
import org.sagebionetworks.bridge.services.AssessmentResourceService;

public class SharedAssessmentResourceControllerTest extends Mockito {

    @Mock
    AssessmentResourceService mockService;
    
    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    @InjectMocks
    @Spy
    SharedAssessmentResourceController controller;
    
    @Captor
    ArgumentCaptor<AssessmentResource> resourceCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(DEVELOPER)).build());
    }

    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(SharedAssessmentResourceController.class);
        assertGet(SharedAssessmentResourceController.class, "getAssessmentResources");
        assertGet(SharedAssessmentResourceController.class, "getAssessmentResource");
        assertPost(SharedAssessmentResourceController.class, "updateAssessmentResource");
        assertDelete(SharedAssessmentResourceController.class, "deleteAssessmentResource");
    }
    
    @Test
    public void getAssessmentResources() {
        PagedResourceList<AssessmentResource> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getResources(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, 50, 100, RESOURCE_CATEGORIES,
                1, 10, true)).thenReturn(page);

        PagedResourceList<AssessmentResource> retValue = controller.getAssessmentResources(ASSESSMENT_ID, "50", "100",
                ImmutableSet.of("license", "publication"), "1", "10", "true");
        assertSame(retValue, page);
        
        verify(mockService).getResources(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, 50, 100, RESOURCE_CATEGORIES,
                1, 10, true);
    }
    
    @Test
    public void getAssessmentResourcesNoParameters() {
        PagedResourceList<AssessmentResource> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getResources(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, 0, 50, null, null, null, false))
                .thenReturn(page);

        PagedResourceList<AssessmentResource> retValue = controller.getAssessmentResources(ASSESSMENT_ID, null, null,
                null, null, null, null);
        assertSame(retValue, page);
        
        verify(mockService).getResources(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, 0, 50, null,
                null, null, false);
    }
    
    @Test
    public void getAssessmentResource() {
        controller.getAssessmentResource(ASSESSMENT_ID, GUID);
        verify(mockService).getResource(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, GUID);
    }

    @Test
    public void updateAssessmentResource() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setGuid("junkGuid");
        mockRequestBody(mockRequest, resource);
        
        when(mockService.updateSharedResource(eq(TEST_STUDY_IDENTIFIER), eq(ASSESSMENT_ID), any()))
                .thenReturn(resource);
        
        AssessmentResource retValue = controller.updateAssessmentResource(ASSESSMENT_ID, GUID);
        assertSame(retValue, resource);
        
        verify(mockService).updateSharedResource(eq(TEST_STUDY_IDENTIFIER), eq(ASSESSMENT_ID),
                resourceCaptor.capture());
        AssessmentResource captured = resourceCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
    }

    @Test
    public void deleteAssessmentResourceDefaultsToLogical() {
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, null);
        verify(mockService).deleteResource(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, GUID);
    }

    @Test
    public void deleteAssessmentResourcePhysically() {
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, "true");
        verify(mockService).deleteResourcePermanently(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, GUID);
    }

    @Test
    public void deleteAssessmentResourceLogically() {
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, "false");
        verify(mockService).deleteResource(SHARED_STUDY_ID_STRING, ASSESSMENT_ID, GUID);
    }
}
