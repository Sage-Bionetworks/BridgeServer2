package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.services.AssessmentService.OFFSET_NOT_POSITIVE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

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

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.services.AssessmentService;

public class SharedAssessmentControllerTest extends Mockito {
    private static final String NEW_ID = "oneNewIdentifier";

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
        when(mockService.importAssessment(TEST_STUDY_IDENTIFIER, OWNER_ID, null, GUID)).thenReturn(assessment);

        Assessment retValue = controller.importAssessment(GUID, OWNER_ID, null);
        assertSame(retValue, assessment);

        verify(mockService).importAssessment(TEST_STUDY_IDENTIFIER, OWNER_ID, null, GUID);
    }

    @Test
    public void importAssessmentWithNewId() {
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);

        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.importAssessment(TEST_STUDY_IDENTIFIER, OWNER_ID, NEW_ID, GUID)).thenReturn(assessment);

        Assessment retValue = controller.importAssessment(GUID, OWNER_ID, NEW_ID);
        assertSame(retValue, assessment);

        verify(mockService).importAssessment(TEST_STUDY_IDENTIFIER, OWNER_ID, NEW_ID, GUID);
    }
    
    @Test
    public void getSharedAssessments() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(new Assessment()), 100);
        when(mockService.getAssessments(SHARED_STUDY_ID_STRING, 10, 25, STRING_TAGS, true)).thenReturn(page);

        PagedResourceList<Assessment> retValue = controller.getSharedAssessments("10", "25", STRING_TAGS, "true");
        assertSame(retValue, page);

        verify(mockService).getAssessments(SHARED_STUDY_ID_STRING, 10, 25, STRING_TAGS, true);
    }

    @Test
    public void getSharedAssessmentsNoArguments() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getAssessments(SHARED_STUDY_ID_STRING, 0, API_DEFAULT_PAGE_SIZE, null, false))
                .thenReturn(page);

        PagedResourceList<Assessment> retValue = controller.getSharedAssessments(null, null, null, null);
        assertSame(retValue, page);

        verify(mockService).getAssessments(SHARED_STUDY_ID_STRING, 0, API_DEFAULT_PAGE_SIZE, null, false);
    }

    @Test
    public void getSharedAssessmentByGuid() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentByGuid(SHARED_STUDY_ID_STRING, GUID)).thenReturn(assessment);

        Assessment retValue = controller.getSharedAssessmentByGuid(GUID);
        assertSame(retValue, assessment);
    }

    @Test
    public void getLatestSharedAssessment() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getLatestAssessment(SHARED_STUDY_ID_STRING, IDENTIFIER)).thenReturn(assessment);

        Assessment retValue = controller.getLatestSharedAssessment(IDENTIFIER);
        assertSame(retValue, assessment);
    }

    @Test
    public void getSharedAssessmentById() {
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentById(SHARED_STUDY_ID_STRING, IDENTIFIER, 10)).thenReturn(assessment);

        Assessment retValue = controller.getSharedAssessmentById(IDENTIFIER, "10");
        assertSame(retValue, assessment);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = OFFSET_NOT_POSITIVE)
    public void getSharedAssessmentByIdWithBadOffset() {
        controller.getSharedAssessmentById(IDENTIFIER, "0");
    }

    @Test
    public void getSharedAssessmentRevisionsByGuid() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(new Assessment()), 100);
        when(mockService.getAssessmentRevisionsByGuid(SHARED_STUDY_ID_STRING, GUID, 10, 25, true)).thenReturn(page);

        PagedResourceList<Assessment> retValue = controller.getSharedAssessmentRevisionsByGuid(GUID, "10", "25",
                "true");
        assertSame(retValue, page);
    }

    @Test
    public void getSharedAssessmentRevisionsByGuidWithNullParameters() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(new Assessment()), 100);
        when(mockService.getAssessmentRevisionsByGuid(SHARED_STUDY_ID_STRING, GUID, 0, 50, false)).thenReturn(page);

        PagedResourceList<Assessment> retValue = controller.getSharedAssessmentRevisionsByGuid(GUID, null, null, null);
        assertSame(retValue, page);
    }

    @Test
    public void getSharedAssessmentRevisionsById() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(new Assessment()), 100);
        when(mockService.getAssessmentRevisionsById(SHARED_STUDY_ID_STRING, IDENTIFIER, 5, 25, false)).thenReturn(page);

        PagedResourceList<Assessment> retValue = controller.getSharedAssessmentRevisionsById(IDENTIFIER, "5", "25",
                "false");
        assertSame(retValue, page);
    }

    @Test
    public void getSharedAssessmentRevisionsByIdWithNullParameters() {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(new Assessment()), 100);
        when(mockService.getAssessmentRevisionsById(SHARED_STUDY_ID_STRING, IDENTIFIER, 0, API_DEFAULT_PAGE_SIZE,
                false)).thenReturn(page);

        PagedResourceList<Assessment> retValue = controller.getSharedAssessmentRevisionsById(IDENTIFIER, null, null,
                null);
        assertSame(retValue, page);
    }

    @Test
    public void updateSharedAssessment() throws Exception {
        // You do need a session for this call
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);

        Assessment assessment = AssessmentTest.createAssessment();
        assessment.setGuid("notCorrectGuid");

        when(mockService.updateSharedAssessment(eq(TEST_STUDY_IDENTIFIER), any()))
                .thenAnswer(invoke -> invoke.getArgument(1));

        mockRequestBody(mockRequest, assessment);

        Assessment retValue = controller.updateSharedAssessment(GUID);
        assertEquals(retValue.getGuid(), GUID);

        verify(mockService).updateSharedAssessment(eq(TEST_STUDY_IDENTIFIER), assessmentCaptor.capture());
        Assessment captured = assessmentCaptor.getValue();
        assertEquals(captured.getIdentifier(), IDENTIFIER);
        assertEquals(captured.getGuid(), GUID);
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
