package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.ONE_DAY_IN_SECONDS;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertAccept;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.files.FileDispositionType.INLINE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.FileService;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyControllerTest extends Mockito {

    private static final PagedResourceList<Study> STUDIES = new PagedResourceList<>(
            ImmutableList.of(Study.create(), Study.create()), 2);
    private static final VersionHolder VERSION_HOLDER = new VersionHolder(1L);

    @Mock
    StudyService service;

    @Mock
    FileService mockFileService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    HttpServletRequest mockRequest;

    @Mock
    HttpServletResponse mockResponse;

    @Captor
    ArgumentCaptor<Study> studyCaptor;
    
    @Captor
    ArgumentCaptor<FileMetadata> metadataCaptor;

    @Captor
    ArgumentCaptor<FileRevision> revisionCaptor;
    
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

        doReturn(session).when(controller).getAuthenticatedSession(STUDY_COORDINATOR, STUDY_DESIGNER, ORG_ADMIN, ADMIN);
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
        assertAccept(StudyController.class, "createStudyLogo");
        assertCreate(StudyController.class, "finishStudyLogo");
        assertGet(StudyController.class, "getStudyForApp");
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
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);

        Study result = controller.getStudy(TEST_STUDY_ID);
        assertEquals(result, study);

        assertEquals(result.getIdentifier(), TEST_STUDY_ID);
        assertEquals(result.getName(), "oneName");

        verify(service).getStudy(TEST_APP_ID, TEST_STUDY_ID, true);
    }

    @Test
    public void updateStudy() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());

        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_ID);
        study.setName("oneName");
        mockRequestBody(mockRequest, study);

        when(service.updateStudy(eq(TEST_APP_ID), any())).thenReturn(VERSION_HOLDER);

        VersionHolder result = controller.updateStudy(TEST_STUDY_ID);

        assertEquals(result, VERSION_HOLDER);

        verify(service).updateStudy(eq(TEST_APP_ID), studyCaptor.capture());

        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getIdentifier(), TEST_STUDY_ID);
        assertEquals(persisted.getName(), "oneName");
    }

    @Test
    public void deleteStudyLogical() throws Exception {
        StatusMessage result = controller.deleteStudy(TEST_STUDY_ID, false);
        assertEquals(result, StudyController.DELETED_MSG);

        verify(service).deleteStudy(TEST_APP_ID, TEST_STUDY_ID);
    }

    @Test
    public void deleteStudyPhysical() throws Exception {
        StatusMessage result = controller.deleteStudy(TEST_STUDY_ID, true);
        assertEquals(result, StudyController.DELETED_MSG);

        verify(service).deleteStudyPermanently(TEST_APP_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void createStudyLogoNoExistingFile() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        session.setAppId(TEST_APP_ID);
        
        Study study = Study.create();
        study.setName("Study Name");
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        FileMetadata created = new FileMetadata();
        created.setGuid(GUID);
        when(mockFileService.createFile(eq(TEST_APP_ID), any())).thenReturn(created);
        
        TestUtils.mockRequestBody(mockRequest, new FileRevision());
        
        FileRevision createdRevision = new FileRevision();
        when(mockFileService.createFileRevision(eq(TEST_APP_ID), any())).thenReturn(createdRevision);
        
        FileRevision retValue = controller.createStudyLogo(TEST_STUDY_ID);
        assertSame(retValue, createdRevision);
        
        verify(mockFileService).createFile(eq(TEST_APP_ID), metadataCaptor.capture());
        FileMetadata captured = metadataCaptor.getValue();
        assertEquals(captured.getName(), "Study Name Logo");
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getDisposition(), INLINE);
        
        verify(service).updateStudy(eq(TEST_APP_ID), studyCaptor.capture());
        assertEquals(studyCaptor.getValue().getLogoGuid(), GUID);
        
        verify(mockFileService).createFileRevision(eq(TEST_APP_ID), revisionCaptor.capture());
        assertEquals(revisionCaptor.getValue().getFileGuid(), GUID);
    }
    
    @Test
    public void createStudyLogoWithExistingFile() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        session.setAppId(TEST_APP_ID);
        
        Study study = Study.create();
        study.setName("Study Name");
        study.setLogoGuid(GUID);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        FileMetadata created = new FileMetadata();
        created.setGuid(GUID);
        when(mockFileService.getFile(TEST_APP_ID, GUID)).thenReturn(created);
        
        TestUtils.mockRequestBody(mockRequest, new FileRevision());
        
        FileRevision createdRevision = new FileRevision();
        when(mockFileService.createFileRevision(eq(TEST_APP_ID), any())).thenReturn(createdRevision);
        
        FileRevision retValue = controller.createStudyLogo(TEST_STUDY_ID);
        assertSame(retValue, createdRevision);
        
        verify(mockFileService, never()).createFile(any(), any());
        verify(service, never()).updateStudy(any(), any());
        
        verify(mockFileService).createFileRevision(eq(TEST_APP_ID), revisionCaptor.capture());
        assertEquals(revisionCaptor.getValue().getFileGuid(), GUID);
    }
    
    @Test
    public void finishStudyLogo() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        Study study = Study.create();
        study.setLogoGuid(GUID);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        FileRevision revision = new FileRevision();
        revision.setDownloadURL("test url");
        when(mockFileService.getFileRevision(GUID, CREATED_ON)).thenReturn(Optional.of(revision));
        
        Study retValue = controller.finishStudyLogo(TEST_STUDY_ID, CREATED_ON.toString());
        assertEquals(retValue.getStudyLogoUrl(), "test url");
        
        verify(mockFileService).finishFileRevision(TEST_APP_ID, GUID, CREATED_ON);
        
        verify(service).updateStudy(eq(TEST_APP_ID), studyCaptor.capture());
        assertEquals(studyCaptor.getValue().getStudyLogoUrl(), "test url");
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void finishStudyLogo_noGuid() { 
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        Study study = Study.create();
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        controller.finishStudyLogo(TEST_STUDY_ID, CREATED_ON.toString());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void finishStudyLogo_noFinishedRevision() { 
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, STUDY_DESIGNER);
        
        Study study = Study.create();
        study.setLogoGuid(GUID);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        when(mockFileService.getFileRevision(GUID, CREATED_ON)).thenReturn(Optional.empty());
        
        controller.finishStudyLogo(TEST_STUDY_ID, CREATED_ON.toString());
    }
    
    @Test
    public void getStudyForApp_noCache() throws JsonProcessingException {
        Study study = Study.create();
        study.setName("Name1");
        study.setIdentifier("id1");
        study.setVersion(10L);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        String retValue = controller.getStudyForApp(TEST_APP_ID, TEST_STUDY_ID);
        
        Study deser = BridgeObjectMapper.get().readValue(retValue, Study.class);
        assertEquals(deser.getName(), "Name1");
        assertEquals(deser.getIdentifier(), "id1");
        assertNull(deser.getVersion());
        
        CacheKey key = CacheKey.publicStudy(TEST_APP_ID, TEST_STUDY_ID);
        String json = Study.STUDY_SUMMARY_WRITER.writeValueAsString(study);
        
        verify(mockCacheProvider).setObject(key, json, ONE_DAY_IN_SECONDS);
    }
    
    @Test
    public void getStudyForApp_cached() throws JsonProcessingException {
        Study study = Study.create();
        study.setName("Name1");
        study.setIdentifier("id1");
        study.setVersion(10L);
        when(service.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);

        CacheKey key = CacheKey.publicStudy(TEST_APP_ID, TEST_STUDY_ID);
        String json = Study.STUDY_SUMMARY_WRITER.writeValueAsString(study);
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(json);
        
        String retValue = controller.getStudyForApp(TEST_APP_ID, TEST_STUDY_ID);
        
        Study deser = BridgeObjectMapper.get().readValue(retValue, Study.class);
        assertEquals(deser.getName(), "Name1");
        assertEquals(deser.getIdentifier(), "id1");
        assertNull(deser.getVersion());
        
        verify(mockCacheProvider, never()).setObject(key, json, ONE_DAY_IN_SECONDS);
    }
}