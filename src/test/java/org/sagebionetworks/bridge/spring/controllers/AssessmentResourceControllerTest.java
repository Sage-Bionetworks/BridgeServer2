package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_ASSESSMENTS_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_ID;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.RESOURCE_CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.Set;

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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;
import org.sagebionetworks.bridge.services.AssessmentResourceService;

public class AssessmentResourceControllerTest extends Mockito {
    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    AssessmentResourceService mockService;
    
    @InjectMocks
    @Spy
    AssessmentResourceController controller;
    
    @Captor
    ArgumentCaptor<AssessmentResource> resourceCaptor;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
    }
    
    @Test
    public void verifyAnnotations() throws Exception { 
        assertCrossOrigin(AssessmentResourceController.class);
        assertCreate(AssessmentResourceController.class, "createAssessmentResource");
        assertGet(AssessmentResourceController.class, "getAssessmentResources");
        assertGet(AssessmentResourceController.class, "getAssessmentResource");
        assertPost(AssessmentResourceController.class, "updateAssessmentResource");
        assertDelete(AssessmentResourceController.class, "deleteAssessmentResource");
    }
    
    @Test
    public void getAssessmentResources() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        PagedResourceList<AssessmentResource> page = new PagedResourceList<>(
                ImmutableList.of(AssessmentResourceTest.createAssessmentResource()), 100);
        when(mockService.getResources(TEST_APP_ID, ASSESSMENT_ID, 10, 100, RESOURCE_CATEGORIES, 1, 1000, true)).thenReturn(page);
        
        PagedResourceList<AssessmentResource> retValue = controller.getAssessmentResources(
                ASSESSMENT_ID, "10", "100", ImmutableSet.of("license", "publication"), 
                "1", "1000", "true");
        assertSame(retValue, page);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        
        verify(mockService).getResources(TEST_APP_ID, ASSESSMENT_ID, 10, 100, RESOURCE_CATEGORIES, 1, 1000, true);
    }
    
    @Test
    public void getAssessmentResourcesNoArguments() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        PagedResourceList<AssessmentResource> page = new PagedResourceList<>(
                ImmutableList.of(AssessmentResourceTest.createAssessmentResource()), 100);
        when(mockService.getResources(TEST_APP_ID, ASSESSMENT_ID, 0, API_DEFAULT_PAGE_SIZE, 
                null, null, null, false)).thenReturn(page);
        
        PagedResourceList<AssessmentResource> retValue = controller.getAssessmentResources(
                ASSESSMENT_ID, null, null, null, null, null, null);
        assertSame(retValue, page);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        
        verify(mockService).getResources(TEST_APP_ID, ASSESSMENT_ID, 0, API_DEFAULT_PAGE_SIZE, null, null, null, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getAssessmentResourcesInvalidMinRevision() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        controller.getAssessmentResources(ASSESSMENT_ID, null, null, null, "junk", null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getAssessmentResourcesInvalidMaxRevision() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        controller.getAssessmentResources(ASSESSMENT_ID, null, null, null, null, "junk", null);
    }
    
    @Test
    public void createAssessmentResource() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        mockRequestBody(mockRequest, resource);
        
        controller.createAssessmentResource(ASSESSMENT_ID);
        
        verify(mockService).createResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), resourceCaptor.capture());
        assertEquals(resourceCaptor.getValue().getTitle(), resource.getTitle());
    }

    @Test
    public void getAssessmentResource() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        when(mockService.getResource(TEST_APP_ID, ASSESSMENT_ID, GUID)).thenReturn(resource);
        
        AssessmentResource retValue = controller.getAssessmentResource(ASSESSMENT_ID, GUID);
        assertSame(retValue, resource);
    }

    @Test
    public void updateAssessmentResource() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        resource.setGuid(null); // verify we set this from the path
        mockRequestBody(mockRequest, resource);
        
        controller.updateAssessmentResource(ASSESSMENT_ID, GUID);
        
        verify(mockService).updateResource(eq(TEST_APP_ID), eq(ASSESSMENT_ID), resourceCaptor.capture());
        assertEquals(resourceCaptor.getValue().getTitle(), resource.getTitle());
        assertEquals(resourceCaptor.getValue().getGuid(), GUID);
    }

    @Test
    public void deleteAssessmentResourceDefaultsToLogical() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER, ADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, null);
        verify(mockService).deleteResource(TEST_APP_ID, ASSESSMENT_ID, GUID);
    }

    @Test
    public void deleteAssessmentResourceDeveloperMustBeLogical() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER, ADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, "true");
        verify(mockService).deleteResource(TEST_APP_ID, ASSESSMENT_ID, GUID);
    }
    
    @Test
    public void deleteAssessmentResourceAdminCanBePhysical() {
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER, ADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, "true");
        verify(mockService).deleteResourcePermanently(TEST_APP_ID, ASSESSMENT_ID, GUID);
    }

    @Test
    public void deleteAssessmentResourceAdminCanBeLogical() {
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(Roles.ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER, ADMIN);
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, "false");
        verify(mockService).deleteResource(TEST_APP_ID, ASSESSMENT_ID, GUID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentResourcesRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        controller.getAssessmentResources(ASSESSMENT_ID, null, null, null, null, null, null);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void createAssessmentResourceRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        controller.createAssessmentResource(ASSESSMENT_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentResourceRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        controller.getAssessmentResource(ASSESSMENT_ID, GUID);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void updateAssessmentResourceRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        controller.updateAssessmentResource(ASSESSMENT_ID, GUID);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void deleteAssessmentResourceRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER, ADMIN);
        
        controller.deleteAssessmentResource(ASSESSMENT_ID, GUID, null);
    }
    
    @Test
    public void publishAssessmentResource() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Set<String> guids = ImmutableSet.of("guid1", "guid2", "guid3");
        mockRequestBody(mockRequest, guids);
        
        controller.publishAssessmentResource(ASSESSMENT_ID);
        
        verify(mockService).publishAssessmentResources(TEST_APP_ID, ASSESSMENT_ID, guids);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void publishAssessmentResourceRejectsSharedAppContext() throws Exception {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.publishAssessmentResource(ASSESSMENT_ID);
    }    
}
