package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyControllerTest extends Mockito {

    private static final String INCLUDE_DELETED_PARAM = "includeDeleted";
    private static final List<Study> STUDIES = ImmutableList.of(Study.create(), Study.create());
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);

    @Mock
    StudyService service;

    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    @Captor
    ArgumentCaptor<Study> studyCaptor;

    @Spy
    @InjectMocks
    StudyController controller;

    UserSession session;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        session = new UserSession();
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        session.setAppId(TEST_APP_ID);

        controller.setStudyService(service);

        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER, ADMIN);

        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(StudyController.class);
        assertGet(StudyController.class, "getStudies");
        assertCreate(StudyController.class, "createStudy");
        assertGet(StudyController.class, "getStudy");
        assertPost(StudyController.class, "updateStudy");
        assertDelete(StudyController.class, "deleteStudy");
    }

    @Test
    public void getStudiesExcludeDeleted() throws Exception {
        when(service.getStudies(TEST_APP_ID, false)).thenReturn(STUDIES);

        ResourceList<Study> result = controller.getStudies(false);

        assertEquals(result.getItems().size(), 2);
        assertFalse((boolean) result.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(service).getStudies(TEST_APP_ID, false);
    }

    @Test
    public void getStudiesIncludeDeleted() throws Exception {
        when(service.getStudies(TEST_APP_ID, true)).thenReturn(STUDIES);

        ResourceList<Study> result = controller.getStudies(true);

        assertEquals(result.getItems().size(), 2);
        assertTrue((boolean) result.getRequestParams().get(INCLUDE_DELETED_PARAM));

        verify(service).getStudies(TEST_APP_ID, true);
    }

    @Test
    public void createStudyDefaultingOrgId() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withOrgMembership(TEST_ORG_ID).build());
        when(service.createStudy(any(), any(), any())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        
        mockRequestBody(mockRequest, study);

        VersionHolder result = controller.createStudy();
        assertEquals(result, VERSION_HOLDER);

        verify(service).createStudy(eq(TEST_APP_ID), eq(TEST_ORG_ID), studyCaptor.capture());

        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getId(), "oneId");
        assertEquals(persisted.getName(), "oneName");
    }
    
    @Test
    public void createStudyWithOrgId() throws Exception {
        when(service.createStudy(any(), any(), any())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        JsonNode node = BridgeObjectMapper.get().valueToTree(study);
        ((ObjectNode)node).put("orgId", TEST_ORG_ID);
        
        mockRequestBody(mockRequest, node.toString());

        VersionHolder result = controller.createStudy();
        assertEquals(result, VERSION_HOLDER);

        verify(service).createStudy(eq(TEST_APP_ID), eq(TEST_ORG_ID), studyCaptor.capture());

        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getId(), "oneId");
        assertEquals(persisted.getName(), "oneName");        
    }

    @Test
    public void getStudy() throws Exception {
        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        when(service.getStudy(TEST_APP_ID, "id", true)).thenReturn(study);

        Study result = controller.getStudy("id");
        assertEquals(result, study);

        assertEquals(result.getId(), "oneId");
        assertEquals(result.getName(), "oneName");

        verify(service).getStudy(TEST_APP_ID, "id", true);
    }

    @Test
    public void updateStudy() throws Exception {
        Study study = Study.create();
        study.setId("oneId");
        study.setName("oneName");
        mockRequestBody(mockRequest, study);

        when(service.updateStudy(eq(TEST_APP_ID), any())).thenReturn(VERSION_HOLDER);

        VersionHolder result = controller.updateStudy("id");

        assertEquals(result, VERSION_HOLDER);

        verify(service).updateStudy(eq(TEST_APP_ID), studyCaptor.capture());

        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getId(), "oneId");
        assertEquals(persisted.getName(), "oneName");
    }

    @Test
    public void deleteStudyLogical() throws Exception {
        StatusMessage result = controller.deleteStudy("id", false);
        assertEquals(result, StudyController.DELETED_MSG);

        verify(service).deleteStudy(TEST_APP_ID, "id");
    }

    @Test
    public void deleteStudyPhysical() throws Exception {
        StatusMessage result = controller.deleteStudy("id", true);
        assertEquals(result, StudyController.DELETED_MSG);

        verify(service).deleteStudyPermanently(TEST_APP_ID, "id");
    }
}