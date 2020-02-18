package org.sagebionetworks.bridge.spring.controllers;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.API_STUDY_ID_STRING;
import static org.sagebionetworks.bridge.BridgeUtils.setsAreEqual;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
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
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentDto;
import org.sagebionetworks.bridge.models.assessments.AssessmentDtoTest;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.services.AssessmentService;

public class AssessmentControllerTest extends Mockito {

    private static final ImmutableSet<String> TAG_SET = ImmutableSet.of("tag1", "tag2");
    private static final ImmutableSet<String> CATEGORY_SET = ImmutableSet.of("cat1", "cat2");
    private static final String IDENTIFIER = "identifier";

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
    AssessmentController controller;
    
    UserSession session;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
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
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(mockAssessment), 100)
                .withRequestParam(OFFSET_BY, 100)
                .withRequestParam(PAGE_SIZE, 25)
                .withRequestParam(INCLUDE_DELETED, true)
                .withRequestParam(PagedResourceList.CATEGORIES, CATEGORY_SET)
                .withRequestParam(PagedResourceList.TAGS, TAG_SET);
        when(mockService.getAssessments(API_STUDY_ID_STRING, 100, 25, CATEGORY_SET,
                TAG_SET, true)).thenReturn(page);
        
        PagedResourceList<AssessmentDto> retValue = controller.getAssessments("100", "25",
                CATEGORY_SET, TAG_SET, "true");

        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(100));
        assertEquals(retValue.getRequestParams().get("offsetBy"), Integer.valueOf(100));
        assertEquals(retValue.getRequestParams().get("pageSize"), Integer.valueOf(25));
        assertEquals(retValue.getRequestParams().get("includeDeleted"), TRUE);
        assertEquals(retValue.getRequestParams().get("categories"), CATEGORY_SET);
        assertEquals(retValue.getRequestParams().get("tags"), TAG_SET);
    }

    @Test
    public void getAssessmentsNullTags() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockService.getAssessments(API_STUDY_ID_STRING, 100, 25, null, null, true)).thenReturn(page);
        
        controller.getAssessments("100", "25", null, null, "true");
        
        verify(mockService).getAssessments(API_STUDY_ID_STRING, 100, 25, null, null, true);
    }

    @Test
    public void createAssessment() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        mockRequestBody(mockRequest, AssessmentDto.create(AssessmentTest.createAssessment()));
        
        Assessment updated = AssessmentTest.createAssessment();
        updated.setVersion(100);
        when(mockService.createAssessment(eq(API_STUDY_ID_STRING), any())).thenReturn(updated);
        
        AssessmentDto retValue = controller.createAssessment();

        verify(mockService).createAssessment(eq(API_STUDY_ID_STRING), assessmentCaptor.capture());
        
        Assessment captured = assessmentCaptor.getValue();
        assertEquals(captured.getAppId(), API_STUDY_ID_STRING);
        assertEquals(captured.getIdentifier(), "identifier");
        assertEquals(captured.getTitle(), "title");
        assertEquals(captured.getSummary(), "summary");
        assertEquals(captured.getValidationStatus(), "validationStatus");
        assertEquals(captured.getNormingStatus(), "normingStatus");
        assertEquals(captured.getOsName(), ANDROID);
        assertEquals(captured.getOriginGuid(), "originGuid");
        assertEquals(captured.getOwnerId(), "ownerId");
        assertEquals(captured.getCategories(), AssessmentDtoTest.CATEGORIES);
        assertEquals(captured.getTags(), AssessmentDtoTest.TAGS);
        assertEquals(captured.getCustomizationFields(), AssessmentDtoTest.CUSTOMIZATION_FIELDS);
        assertEquals(captured.getCreatedOn(), AssessmentDtoTest.CREATED_ON);
        assertEquals(captured.getModifiedOn(), AssessmentDtoTest.MODIFIED_ON);
        assertTrue(captured.isDeleted());
        assertEquals(captured.getRevision(), 5);
        assertEquals(captured.getVersion(), 8L);
        
        assertEquals(retValue.getVersion(), 100);
    }

    @Test
    public void updateAssessment() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        mockRequestBody(mockRequest, AssessmentDto.create(AssessmentTest.createAssessment()));
        
        Assessment updated = AssessmentTest.createAssessment();
        updated.setVersion(100);
        when(mockService.updateAssessment(eq(API_STUDY_ID_STRING), any())).thenReturn(updated);
        
        AssessmentDto retValue = controller.updateAssessmentByGuid(GUID);
        assertNotNull(retValue);

        verify(mockService).updateAssessment(eq(API_STUDY_ID_STRING), assessmentCaptor.capture());
        Assessment captured = assessmentCaptor.getValue();
        
        assertEquals(captured.getGuid(), GUID);
    }

    @Test
    public void getAssessmentByGuid() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentByGuid(API_STUDY_ID_STRING, GUID)).thenReturn(assessment);
        
        AssessmentDto dto = controller.getAssessmentByGuid(GUID);

        assertEquals(dto.getIdentifier(), "identifier");
        assertEquals(dto.getTitle(), "title");
        assertEquals(dto.getSummary(), "summary");
        assertEquals(dto.getValidationStatus(), "validationStatus");
        assertEquals(dto.getNormingStatus(), "normingStatus");
        assertEquals(dto.getOsName(), ANDROID);
        assertEquals(dto.getOriginGuid(), "originGuid");
        assertEquals(dto.getOwnerId(), "ownerId");
        assertTrue(setsAreEqual(dto.getCategories(), AssessmentDtoTest.STRING_CATEGORIES));
        assertTrue(setsAreEqual(dto.getTags(), AssessmentDtoTest.STRING_TAGS));
        assertEquals(dto.getCustomizationFields(), AssessmentDtoTest.CUSTOMIZATION_FIELDS);
        assertEquals(dto.getCreatedOn(), AssessmentDtoTest.CREATED_ON);
        assertEquals(dto.getModifiedOn(), AssessmentDtoTest.MODIFIED_ON);
        assertTrue(dto.isDeleted());
        assertEquals(dto.getRevision(), 5);
        assertEquals(dto.getVersion(), 8L);
    }

    @Test
    public void getAssessmentById() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getAssessmentById(API_STUDY_ID_STRING, IDENTIFIER, 10)).thenReturn(assessment);
        
        AssessmentDto dto = controller.getAssessmentById(IDENTIFIER, "10");
        
        assertEquals(dto.getIdentifier(), "identifier");
        assertEquals(dto.getTitle(), "title");
        assertEquals(dto.getSummary(), "summary");
        assertEquals(dto.getValidationStatus(), "validationStatus");
        assertEquals(dto.getNormingStatus(), "normingStatus");
        assertEquals(dto.getOsName(), ANDROID);
        assertEquals(dto.getOriginGuid(), "originGuid");
        assertEquals(dto.getOwnerId(), "ownerId");
        assertTrue(setsAreEqual(dto.getCategories(), AssessmentDtoTest.STRING_CATEGORIES));
        assertTrue(setsAreEqual(dto.getTags(), AssessmentDtoTest.STRING_TAGS));
        assertEquals(dto.getCustomizationFields(), AssessmentDtoTest.CUSTOMIZATION_FIELDS);
        assertEquals(dto.getCreatedOn(), AssessmentDtoTest.CREATED_ON);
        assertEquals(dto.getModifiedOn(), AssessmentDtoTest.MODIFIED_ON);
        assertTrue(dto.isDeleted());
        assertEquals(dto.getRevision(), 5);
        assertEquals(dto.getVersion(), 8L);
    }

    @Test
    public void getLatestAssessment() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.getLatestAssessment(API_STUDY_ID_STRING, IDENTIFIER)).thenReturn(assessment);

        AssessmentDto dto = controller.getLatestAssessment(IDENTIFIER);
        assertNotNull(dto);
    }

    @Test
    public void getAssessmentRevisions() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(assessment), 10);
        when(mockService.getAssessmentRevisionsById(
                API_STUDY_ID_STRING, IDENTIFIER, 20, 5, true)).thenReturn(page);
        
        PagedResourceList<AssessmentDto> retValue = controller.getAssessmentRevisionsById(IDENTIFIER, "20", "5", "true");
        assertEquals(retValue.getItems().get(0).getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getTotal(), Integer.valueOf(10));
    }

    @Test
    public void publishAssessment() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        Assessment assessment = AssessmentTest.createAssessment();
        when(mockService.publishAssessment(API_STUDY_ID_STRING, GUID)).thenReturn(assessment);
        
        AssessmentDto dto = controller.publishAssessment(GUID);
        assertEquals(dto.getIdentifier(), IDENTIFIER);
    }

    @Test
    public void developerCanLogicallyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "false");
        verify(mockService).deleteAssessment(API_STUDY_ID_STRING, GUID);
    }

    @Test
    public void adminCanLogicallyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "false");
        verify(mockService).deleteAssessment(API_STUDY_ID_STRING, GUID);
    }

    @Test
    public void physicalDeleteONotAllowedForDeveloper() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(DEVELOPER)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "true");
        verify(mockService).deleteAssessment(API_STUDY_ID_STRING, GUID);        
    }

    @Test
    public void physicalDeleteAllowedForAdmin() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        controller.deleteAssessment(GUID, "true");
        verify(mockService).deleteAssessmentPermanently(API_STUDY_ID_STRING, GUID);        
    }    
}
