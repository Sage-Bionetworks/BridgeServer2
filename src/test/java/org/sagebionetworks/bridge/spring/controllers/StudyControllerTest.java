package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;

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

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyControllerTest extends Mockito {

    private static final PagedResourceList<Study> STUDIES = new PagedResourceList<>(
            ImmutableList.of(Study.create(), Study.create()), 2);
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

        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, ORG_ADMIN, ADMIN);
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);
        doReturn(session).when(controller).getAdministrativeSession();
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
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
    public void getStudiesWithDefaults() throws Exception {
        when(service.getStudies(TEST_APP_ID, 0, API_DEFAULT_PAGE_SIZE, false)).thenReturn(STUDIES);

        ResourceList<Study> result = controller.getStudies(null, null, false);

        assertEquals(result.getItems().size(), 2);

        verify(service).getStudies(TEST_APP_ID, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test
    public void getStudiesExcludeDeleted() throws Exception {
        when(service.getStudies(TEST_APP_ID, 0, 50, false)).thenReturn(STUDIES);

        ResourceList<Study> result = controller.getStudies("0", "50", false);

        assertEquals(result.getItems().size(), 2);

        verify(service).getStudies(TEST_APP_ID, 0, 50, false);
    }

    @Test
    public void getStudiesIncludeDeleted() throws Exception {
        when(service.getStudies(TEST_APP_ID, 0, 50, true)).thenReturn(STUDIES);

        ResourceList<Study> result = controller.getStudies("0", "50", true);

        assertEquals(result.getItems().size(), 2);

        verify(service).getStudies(TEST_APP_ID, 0, 50, true);
    }

    @Test
    public void createStudy() throws Exception {
        when(service.createStudy(any(), any(), anyBoolean())).thenReturn(VERSION_HOLDER);

        Study study = Study.create();
        study.setIdentifier("oneId");
        study.setName("oneName");
        mockRequestBody(mockRequest, study);

        VersionHolder result = controller.createStudy();
        assertEquals(result, VERSION_HOLDER);

        verify(service).createStudy(eq(TEST_APP_ID), studyCaptor.capture(), eq(true));

        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getIdentifier(), "oneId");
        assertEquals(persisted.getName(), "oneName");
    }

    @Test
    public void getStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Study study = Study.create();
        study.setIdentifier("oneId");
        study.setName("oneName");
        when(service.getStudy(TEST_APP_ID, "id", true)).thenReturn(study);

        Study result = controller.getStudy("id");
        assertEquals(result, study);

        assertEquals(result.getIdentifier(), "oneId");
        assertEquals(result.getName(), "oneName");

        verify(service).getStudy(TEST_APP_ID, "id", true);
    }

    @Test
    public void updateStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("id"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());

        Study study = Study.create();
        study.setIdentifier("oneId");
        study.setName("oneName");
        mockRequestBody(mockRequest, study);

        when(service.updateStudy(eq(TEST_APP_ID), any())).thenReturn(VERSION_HOLDER);

        VersionHolder result = controller.updateStudy("id");

        assertEquals(result, VERSION_HOLDER);

        verify(service).updateStudy(eq(TEST_APP_ID), studyCaptor.capture());

        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getIdentifier(), "oneId");
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