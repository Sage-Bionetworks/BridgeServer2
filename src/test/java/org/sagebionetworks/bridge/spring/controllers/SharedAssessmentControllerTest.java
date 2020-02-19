package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.STRING_CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentDto;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.services.AssessmentService;

public class SharedAssessmentControllerTest extends Mockito {
    @Mock
    AssessmentService mockService;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    Assessment mockAssessment;
    
    @Captor
    ArgumentCaptor<Assessment> assessmentCaptor;

    @InjectMocks
    @Spy
    SharedAssessmentController controller;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void importAssessment() {
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.importAssessment(TEST_STUDY_IDENTIFIER, OWNER_ID, GUID)).thenReturn(assessment);
        
        AssessmentDto retValue = controller.importAssessment(GUID, OWNER_ID);
        assertEquals(retValue.getGuid(), assessment.getGuid());
        
        verify(mockService).importAssessment(TEST_STUDY_IDENTIFIER, OWNER_ID, GUID);
    }
    
    @Test
    public void getSharedAssessments() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(mockAssessment), 100);
        when(mockService.getAssessments(
                SHARED_STUDY_ID_STRING, 10, 25, STRING_CATEGORIES, STRING_TAGS, true)).thenReturn(page);
        
        PagedResourceList<AssessmentDto> retValue = controller.getSharedAssessments("10", "25", STRING_CATEGORIES, STRING_TAGS, "true");
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        
        verify(mockService).getAssessments(
                SHARED_STUDY_ID_STRING, 10, 25, STRING_CATEGORIES, STRING_TAGS, true);
    }
    
    @Test
    public void getSharedAssessmentsNoArguments() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getAssessments(
                SHARED_STUDY_ID_STRING, 0, API_DEFAULT_PAGE_SIZE, null, null, false)).thenReturn(page);
        
        PagedResourceList<AssessmentDto> retValue = controller.getSharedAssessments(null, null, null, null, "false");
        // Just verify this was returned
        assertEquals(retValue.getTotal(), Integer.valueOf(0));
        
        verify(mockService).getAssessments(
                SHARED_STUDY_ID_STRING, 0, API_DEFAULT_PAGE_SIZE, null, null, false);
    }
    
    @Test
    public void getSharedAssessmentByGuid() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentByGuid(SHARED_STUDY_ID_STRING, GUID)).thenReturn(assessment);
        
        AssessmentDto dto = controller.getSharedAssessmentByGuid(GUID);
        assertEquals(dto.getGuid(), GUID);
    }
    
    @Test
    public void getLatestSharedAssessment() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getLatestAssessment(SHARED_STUDY_ID_STRING, IDENTIFIER)).thenReturn(assessment);
        
        AssessmentDto dto = controller.getLatestSharedAssessment(IDENTIFIER);
        assertEquals(dto.getIdentifier(), IDENTIFIER);
    }

    @Test
    public void getSharedAssessmentById() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentById(SHARED_STUDY_ID_STRING, IDENTIFIER, 10)).thenReturn(assessment);
        
        AssessmentDto dto = controller.getSharedAssessmentById(IDENTIFIER, "10");
        assertEquals(dto.getGuid(), GUID);
    }

    @Test
    public void getSharedAssessmentRevisionsByGuid() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(mockAssessment), 100);
        when(mockService.getAssessmentRevisionsByGuid(
                SHARED_STUDY_ID_STRING, GUID, 10, 25, true)).thenReturn(page);
        
        PagedResourceList<AssessmentDto> retValue = controller.getSharedAssessmentRevisionsByGuid(GUID, "10", "25", "true");
        assertEquals(retValue.getItems().size(), 1);
    }

    @Test
    public void getSharedAssessmentRevisionsById() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(mockAssessment), 100);        
        when(mockService.getAssessmentRevisionsById(
                SHARED_STUDY_ID_STRING, IDENTIFIER, 0, API_DEFAULT_PAGE_SIZE, false)).thenReturn(page);
        
        PagedResourceList<AssessmentDto> retValue = controller.getSharedAssessmentRevisionsById(IDENTIFIER, null, null, "false");
        assertEquals(retValue.getItems().size(), 1);
    }

    @Test
    public void updateSharedAssessment() throws Exception {
        // You do need a session for this call
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.updateSharedAssessment(eq(TEST_STUDY_IDENTIFIER), any())).thenReturn(assessment);
        
        mockRequestBody(mockRequest, AssessmentDto.create(assessment));

        AssessmentDto retValue = controller.updateSharedAssessment(GUID);
        assertEquals(retValue.getIdentifier(), assessment.getIdentifier());
        assertEquals(retValue.getGuid(), assessment.getGuid());
        
        verify(mockService).updateSharedAssessment(eq(TEST_STUDY_IDENTIFIER), assessmentCaptor.capture());
        Assessment captured = assessmentCaptor.getValue();
        assertEquals(captured.getIdentifier(), assessment.getIdentifier());
        assertEquals(captured.getGuid(), assessment.getGuid());
    }

    @Test
    public void deleteSharedAssessmentLogically() {
        UserSession session = new UserSession();
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteSharedAssessment(GUID, "false");
        verify(mockService).deleteAssessment(SHARED_STUDY_ID_STRING, GUID);
    }    

    @Test
    public void deleteSharedAssessmentDefaultsToLogical() {
        UserSession session = new UserSession();
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteSharedAssessment(GUID, null);
        verify(mockService).deleteAssessment(SHARED_STUDY_ID_STRING, GUID);
    }
    
    @Test
    public void deleteSharedAssessmentPermanently() {
        UserSession session = new UserSession();
        doReturn(session).when(controller).getAuthenticatedSession(SUPERADMIN);

        controller.deleteSharedAssessment(GUID, "true");
        verify(mockService).deleteAssessmentPermanently(SHARED_STUDY_ID_STRING, GUID);
    }    
}
