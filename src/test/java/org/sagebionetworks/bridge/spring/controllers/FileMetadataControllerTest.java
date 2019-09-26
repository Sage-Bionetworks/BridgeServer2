package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
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
import static org.sagebionetworks.bridge.spring.controllers.FileMetadataController.DELETE_MSG;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.services.FileService;

public class FileMetadataControllerTest extends Mockito {
    
    @Mock
    FileService mockFileService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<FileMetadata> metadataCaptor;
    
    @Spy
    @InjectMocks
    FileMetadataController controller;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(FileMetadataController.class);
        assertGet(FileMetadataController.class, "getFiles");
        assertCreate(FileMetadataController.class, "createFile");
        assertGet(FileMetadataController.class, "getFile");
        assertPost(FileMetadataController.class, "updateFile");
        assertDelete(FileMetadataController.class, "deleteFile");
    }
    
    @Test
    public void getFilesIncludeDeleted() {
        PagedResourceList<FileMetadata> page = new PagedResourceList<>(ImmutableList.of(new FileMetadata()), 100);
        when(mockFileService.getFiles(TEST_STUDY, 5, 40, true)).thenReturn(page);
        
        ResourceList<FileMetadata> results = controller.getFiles("5", "40", "true");
        assertSame(results, page);
        
        verify(mockFileService).getFiles(TEST_STUDY, 5, 40, true);
    }
    
    @Test
    public void getFilesExcludeDeleted() {
        PagedResourceList<FileMetadata> page = new PagedResourceList<>(ImmutableList.of(new FileMetadata()), 100);
        when(mockFileService.getFiles(TEST_STUDY, 0, API_DEFAULT_PAGE_SIZE, false)).thenReturn(page);
        
        ResourceList<FileMetadata> results = controller.getFiles(null, null, "false");
        assertSame(results, page);
        
        verify(mockFileService).getFiles(TEST_STUDY, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test
    public void createFile() throws Exception {
        FileMetadata persisted = new FileMetadata();
        persisted.setGuid(GUID);
        persisted.setVersion(3L);
        when(mockFileService.createFile(eq(TEST_STUDY), any())).thenReturn(persisted);
        
        FileMetadata file = new FileMetadata();
        file.setName("a test name");
        mockRequestBody(mockRequest, file);
        
        GuidVersionHolder keys = controller.createFile();
        assertEquals(keys.getGuid(), GUID);
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockFileService).createFile(eq(TEST_STUDY), metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getName(), "a test name");
    }
    
    @Test
    public void getFile() {
        FileMetadata persisted = new FileMetadata();
        when(mockFileService.getFile(TEST_STUDY, GUID)).thenReturn(persisted);
        
        FileMetadata file = controller.getFile(GUID);
        assertSame(file, persisted);
        
        verify(mockFileService).getFile(TEST_STUDY, GUID);
    }
    
    @Test
    public void updateFile() throws Exception {
        FileMetadata persisted = new FileMetadata();
        persisted.setGuid(GUID);
        persisted.setVersion(3L);
        when(mockFileService.updateFile(eq(TEST_STUDY), any())).thenReturn(persisted);
        
        FileMetadata file = new FileMetadata();
        file.setName("a test name");
        mockRequestBody(mockRequest, file);
        
        GuidVersionHolder keys = controller.updateFile(GUID);
        assertEquals(keys.getGuid(), GUID);
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockFileService).updateFile(eq(TEST_STUDY), metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getName(), "a test name");
        assertEquals(metadataCaptor.getValue().getGuid(), GUID);
    }
    
    @Test
    public void deleteFileDefault() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage message = controller.deleteFile(GUID, null);
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFile(TEST_STUDY, GUID);
    }

    @Test
    public void deleteTemplate() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage message = controller.deleteFile(GUID, "false");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFile(TEST_STUDY, GUID);
    }

    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        StatusMessage message = controller.deleteFile(GUID, "true");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFile(TEST_STUDY, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        StatusMessage message = controller.deleteFile(GUID, "true");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFilePermanently(TEST_STUDY, GUID);
    }
}
