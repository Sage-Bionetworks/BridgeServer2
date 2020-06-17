package org.sagebionetworks.bridge.spring.controllers;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.NONPOSITIVE_REVISION_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_ASSESSMENTS_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.CUSTOMIZATION_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_DELETED;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

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
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.services.AssessmentService;

public class AssessmentControllerTest extends Mockito {
    private static final String NEW_IDENTIFIER = "oneNewIdentifier";
    
    @Mock
    AssessmentService mockService;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<Assessment> assessmentCaptor;

    @InjectMocks
    @Spy
    AssessmentController controller;
    
    UserSession session;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
    }

    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AssessmentController.class);
        assertGet(AssessmentController.class, "getAssessments");
        assertCreate(AssessmentController.class, "createAssessment");
        assertGet(AssessmentController.class, "getAssessmentByGuid");
        assertPost(AssessmentController.class, "updateAssessmentByGuid");
        assertGet(AssessmentController.class, "getAssessmentRevisionsByGuid");
        assertCreate(AssessmentController.class, "createAssessmentRevision");
        assertCreate(AssessmentController.class, "publishAssessment");
        assertDelete(AssessmentController.class, "deleteAssessment");
        assertGet(AssessmentController.class, "getLatestAssessment");
        assertGet(AssessmentController.class, "getAssessmentRevisionsById");
        assertGet(AssessmentController.class, "getAssessmentById");
    }
    
    @Test
    public void getAssessments() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(new Assessment()), 100)
                .withRequestParam(OFFSET_BY, 100)
                .withRequestParam(PAGE_SIZE, 25)
                .withRequestParam(INCLUDE_DELETED, true)
                .withRequestParam(PagedResourceList.TAGS, STRING_TAGS);
        when(mockService.getAssessments(TEST_APP_ID, 100, 25, STRING_TAGS, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = controller.getAssessments("100", "25",
                STRING_TAGS, "true");

        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        assertEquals(retValue.getRequestParams().get("offsetBy"), Integer.valueOf(100));
        assertEquals(retValue.getRequestParams().get("pageSize"), Integer.valueOf(25));
        assertEquals(retValue.getRequestParams().get("includeDeleted"), TRUE);
        assertEquals(retValue.getRequestParams().get("tags"), STRING_TAGS);
    }

    @Test
    public void getAssessmentsNullArguments() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getAssessments(TEST_APP_ID, 0, 50, null, false)).thenReturn(page);
        
        controller.getAssessments(null, null, null, null);
        
        verify(mockService).getAssessments(TEST_APP_ID, 0, 50, null, false);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentsRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.getAssessments(null, null, null, null);
    }
    
    @Test
    public void createAssessment() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        mockRequestBody(mockRequest, AssessmentTest.createAssessment());
        
        Assessment updated = AssessmentTest.createAssessment();
        updated.setVersion(100);
        when(mockService.createAssessment(eq(TEST_APP_ID), any())).thenReturn(updated);
        
        Assessment retValue = controller.createAssessment();

        verify(mockService).createAssessment(eq(TEST_APP_ID), assessmentCaptor.capture());
        
        Assessment captured = assessmentCaptor.getValue();
        assertEquals(captured.getIdentifier(), IDENTIFIER);
        assertEquals(captured.getTitle(), "title");
        assertEquals(captured.getSummary(), "summary");
        assertEquals(captured.getValidationStatus(), "validationStatus");
        assertEquals(captured.getNormingStatus(), "normingStatus");
        assertEquals(captured.getOsName(), ANDROID);
        assertEquals(captured.getOriginGuid(), "originGuid");
        assertEquals(captured.getOwnerId(), OWNER_ID);
        assertEquals(captured.getTags(), STRING_TAGS);
        assertEquals(captured.getCustomizationFields(), CUSTOMIZATION_FIELDS);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertTrue(captured.isDeleted());
        assertEquals(captured.getRevision(), 5);
        assertEquals(captured.getVersion(), 8L);
        
        assertEquals(retValue.getVersion(), 100);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void createAssessmentRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.createAssessment();
    }
    
    @Test
    public void updateAssessment() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid("thisGuidWillBeReplaced");
        mockRequestBody(mockRequest, assessment);
        
        Assessment updated = AssessmentTest.createAssessment();
        updated.setVersion(100);
        when(mockService.updateAssessment(eq(TEST_APP_ID), any())).thenReturn(updated);
        
        Assessment retValue = controller.updateAssessmentByGuid(GUID);
        assertNotNull(retValue);

        verify(mockService).updateAssessment(eq(TEST_APP_ID), assessmentCaptor.capture());
        Assessment captured = assessmentCaptor.getValue();
        
        assertEquals(captured.getGuid(), GUID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void updateAssessmentRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.updateAssessmentByGuid(GUID);
    }

    @Test
    public void getAssessmentByGuid() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentByGuid(TEST_APP_ID, GUID)).thenReturn(assessment);
        
        Assessment retValue = controller.getAssessmentByGuid(GUID);
        assertSame(retValue, assessment);

        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getTitle(), "title");
        assertEquals(retValue.getSummary(), "summary");
        assertEquals(retValue.getValidationStatus(), "validationStatus");
        assertEquals(retValue.getNormingStatus(), "normingStatus");
        assertEquals(retValue.getOsName(), ANDROID);
        assertEquals(retValue.getOriginGuid(), "originGuid");
        assertEquals(retValue.getOwnerId(), OWNER_ID);
        assertEquals(retValue.getTags(), STRING_TAGS);
        assertEquals(retValue.getCustomizationFields(), CUSTOMIZATION_FIELDS);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertTrue(retValue.isDeleted());
        assertEquals(retValue.getRevision(), 5);
        assertEquals(retValue.getVersion(), 8L);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentByGuidRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.getAssessmentByGuid(GUID);
    }
    
    @Test
    public void getAssessmentById() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentById(TEST_APP_ID, IDENTIFIER, 10)).thenReturn(assessment);
        
        Assessment retValue = controller.getAssessmentById(IDENTIFIER, "10");
        assertSame(retValue, assessment);
        
        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getTitle(), "title");
        assertEquals(retValue.getSummary(), "summary");
        assertEquals(retValue.getValidationStatus(), "validationStatus");
        assertEquals(retValue.getNormingStatus(), "normingStatus");
        assertEquals(retValue.getOsName(), ANDROID);
        assertEquals(retValue.getOriginGuid(), "originGuid");
        assertEquals(retValue.getOwnerId(), OWNER_ID);
        assertEquals(retValue.getTags(), STRING_TAGS);
        assertEquals(retValue.getCustomizationFields(), CUSTOMIZATION_FIELDS);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertTrue(retValue.isDeleted());
        assertEquals(retValue.getRevision(), 5);
        assertEquals(retValue.getVersion(), 8L);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = NONPOSITIVE_REVISION_ERROR)
    public void getAssessmentByIdZeroRevision() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.getAssessmentById(IDENTIFIER, "0");
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = NONPOSITIVE_REVISION_ERROR)
    public void getAssessmentByIdNegativeRevision() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.getAssessmentById(IDENTIFIER, "-1");
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "nonsense is not an integer")
    public void getAssessmentByIdNonsenseRevision() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        controller.getAssessmentById(IDENTIFIER, "nonsense");
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentByIdRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.getAssessmentById(IDENTIFIER, "3");
    }
    
    @Test
    public void getLatestAssessment() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getLatestAssessment(TEST_APP_ID, IDENTIFIER)).thenReturn(assessment);

        Assessment retValue = controller.getLatestAssessment(IDENTIFIER);
        assertSame(retValue, assessment);
    }

    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getLatestAssessmentRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.getLatestAssessment(IDENTIFIER);
    }    
    
    @Test
    public void getAssessmentRevisionsById() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(assessment), 10);
        when(mockService.getAssessmentRevisionsById(
                TEST_APP_ID, IDENTIFIER, 20, 5, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = controller.getAssessmentRevisionsById(IDENTIFIER, "20", "5", "true");
        assertSame(retValue, page);
    }

    @Test
    public void getAssessmentRevisionsByIdWithNullParameters() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(assessment), 10);
        when(mockService.getAssessmentRevisionsById(
                TEST_APP_ID, IDENTIFIER, 0, 50, false)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = controller.getAssessmentRevisionsById(IDENTIFIER, null, null, null);
        assertSame(retValue, page);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentRevisionsByIdRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.getAssessmentRevisionsById(IDENTIFIER, null, null, null);
    }
    
    @Test
    public void getAssessmentRevisionsByGuid() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(assessment), 10);
        when(mockService.getAssessmentRevisionsByGuid(
                TEST_APP_ID, GUID, 20, 5, true)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = controller.getAssessmentRevisionsByGuid(GUID, "20", "5", "true");
        assertSame(retValue, page);
    }
    
    @Test
    public void getAssessmentRevisionsByGuidWithNullParameters() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(assessment), 10);
        when(mockService.getAssessmentRevisionsByGuid(
                TEST_APP_ID, GUID, 0, 50, false)).thenReturn(page);
        
        PagedResourceList<Assessment> retValue = controller.getAssessmentRevisionsByGuid(GUID, null, null, null);
        assertSame(retValue, page);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void getAssessmentRevisionsByGuidRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.getAssessmentRevisionsByGuid(GUID, null, null, null);
    }
    
    @Test
    public void createAssessmentRevision() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        mockRequestBody(mockRequest, AssessmentTest.createAssessment());
        
        Assessment updated = AssessmentTest.createAssessment();
        updated.setVersion(100);
        when(mockService.createAssessmentRevision(eq(TEST_APP_ID), eq(GUID), any())).thenReturn(updated);
        
        Assessment retValue = controller.createAssessmentRevision(GUID);

        verify(mockService).createAssessmentRevision(eq(TEST_APP_ID), eq(GUID), assessmentCaptor.capture());
        
        Assessment captured = assessmentCaptor.getValue();
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getIdentifier(), IDENTIFIER);
        assertEquals(captured.getTitle(), "title");
        assertEquals(captured.getSummary(), "summary");
        assertEquals(captured.getValidationStatus(), "validationStatus");
        assertEquals(captured.getNormingStatus(), "normingStatus");
        assertEquals(captured.getOsName(), ANDROID);
        assertEquals(captured.getOriginGuid(), "originGuid");
        assertEquals(captured.getOwnerId(), OWNER_ID);
        assertEquals(captured.getTags(), STRING_TAGS);
        assertEquals(captured.getCustomizationFields(), CUSTOMIZATION_FIELDS);
        assertEquals(captured.getCreatedOn(), CREATED_ON);
        assertEquals(captured.getModifiedOn(), MODIFIED_ON);
        assertTrue(captured.isDeleted());
        assertEquals(captured.getRevision(), 5);
        assertEquals(captured.getVersion(), 8L);
        
        assertEquals(retValue.getVersion(), 100);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void createAssessmentRevisionRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.createAssessmentRevision(GUID);
    }
    
    @Test
    public void publishAssessment() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.publishAssessment(TEST_APP_ID, null, GUID)).thenReturn(assessment);
        
        Assessment retValue = controller.publishAssessment(GUID, null);
        assertSame(retValue, assessment);
    }

    @Test
    public void publishAssessmentWithNewIdentifier() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.publishAssessment(TEST_APP_ID, NEW_IDENTIFIER, GUID)).thenReturn(assessment);
        
        Assessment retValue = controller.publishAssessment(GUID, NEW_IDENTIFIER);
        assertSame(retValue, assessment);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void publishAssessmentRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.publishAssessment(GUID, null);
    }
    
    @Test
    public void developerCanLogicallyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "false");
        verify(mockService).deleteAssessment(TEST_APP_ID, GUID);
    }

    @Test
    public void adminCanLogicallyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "false");
        verify(mockService).deleteAssessment(TEST_APP_ID, GUID);
    }

    @Test
    public void physicalDeleteONotAllowedForDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "true");
        verify(mockService).deleteAssessment(TEST_APP_ID, GUID);        
    }

    @Test
    public void physicalDeleteAllowedForAdmin() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "true");
        verify(mockService).deleteAssessmentPermanently(TEST_APP_ID, GUID);        
    }
    
    @Test(expectedExceptions = UnauthorizedException.class, 
            expectedExceptionsMessageRegExp = SHARED_ASSESSMENTS_ERROR)
    public void deleteAssessmentRejectsSharedAppContext() {
        session.setAppId(SHARED_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        controller.deleteAssessment(GUID, null);
    }
}
