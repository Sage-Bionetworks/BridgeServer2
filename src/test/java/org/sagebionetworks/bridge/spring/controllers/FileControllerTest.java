package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.FileController.DELETE_MSG;
import static org.sagebionetworks.bridge.spring.controllers.FileController.UPLOAD_FINISHED_MSG;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;
import org.sagebionetworks.bridge.services.FileService;

public class FileControllerTest extends Mockito {
    
    private static final DateTime CREATED_ON = new DateTime();
    
    @Mock
    FileService mockFileService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<FileMetadata> metadataCaptor;
    
    @Captor
    ArgumentCaptor<FileRevision> revisionCaptor;
    
    @Captor
    ArgumentCaptor<DateTime> dateTimeCaptor;
    
    @Spy
    @InjectMocks
    FileController controller;
    
    UserSession session;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(FileController.class);
        assertGet(FileController.class, "getFiles");
        assertCreate(FileController.class, "createFile");
        assertGet(FileController.class, "getFile");
        assertPost(FileController.class, "updateFile");
        assertDelete(FileController.class, "deleteFile");
        assertGet(FileController.class, "getFileRevisions");
        assertGet(FileController.class, "getFileRevision");
        assertCreate(FileController.class, "createFileRevision");
        assertPost(FileController.class, "finishFileRevision");
    }
    
    @Test
    public void getFilesIncludeDeleted() {
        doReturn(session).when(controller).getAdministrativeSession();
        
        PagedResourceList<FileMetadata> page = new PagedResourceList<>(ImmutableList.of(new FileMetadata()), 100);
        when(mockFileService.getFiles(TEST_APP_ID, 5, 40, true)).thenReturn(page);
        
        ResourceList<FileMetadata> results = controller.getFiles("5", "40", "true");
        assertSame(results, page);
        
        verify(mockFileService).getFiles(TEST_APP_ID, 5, 40, true);
    }
    
    @Test
    public void getFilesExcludeDeleted() {
        doReturn(session).when(controller).getAdministrativeSession();
        
        PagedResourceList<FileMetadata> page = new PagedResourceList<>(ImmutableList.of(new FileMetadata()), 100);
        when(mockFileService.getFiles(TEST_APP_ID, 0, API_DEFAULT_PAGE_SIZE, false)).thenReturn(page);
        
        ResourceList<FileMetadata> results = controller.getFiles(null, null, "false");
        assertSame(results, page);
        
        verify(mockFileService).getFiles(TEST_APP_ID, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test
    public void createFile() throws Exception {
        FileMetadata persisted = new FileMetadata();
        persisted.setGuid(GUID);
        persisted.setVersion(3L);
        when(mockFileService.createFile(eq(TEST_APP_ID), any())).thenReturn(persisted);
        
        FileMetadata file = new FileMetadata();
        file.setName("a test name");
        mockRequestBody(mockRequest, file);
        
        GuidVersionHolder keys = controller.createFile();
        assertEquals(keys.getGuid(), GUID);
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockFileService).createFile(eq(TEST_APP_ID), metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getName(), "a test name");
    }
    
    @Test
    public void getFile() {
        FileMetadata persisted = new FileMetadata();
        when(mockFileService.getFile(TEST_APP_ID, GUID)).thenReturn(persisted);
        
        FileMetadata file = controller.getFile(GUID);
        assertSame(file, persisted);
        
        verify(mockFileService).getFile(TEST_APP_ID, GUID);
    }
    
    @Test
    public void updateFile() throws Exception {
        FileMetadata persisted = new FileMetadata();
        persisted.setGuid(GUID);
        persisted.setVersion(3L);
        when(mockFileService.updateFile(eq(TEST_APP_ID), any())).thenReturn(persisted);
        
        FileMetadata file = new FileMetadata();
        file.setName("a test name");
        mockRequestBody(mockRequest, file);
        
        GuidVersionHolder keys = controller.updateFile(GUID);
        assertEquals(keys.getGuid(), GUID);
        assertEquals(keys.getVersion(), Long.valueOf(3L));
        
        verify(mockFileService).updateFile(eq(TEST_APP_ID), metadataCaptor.capture());
        assertEquals(metadataCaptor.getValue().getName(), "a test name");
        assertEquals(metadataCaptor.getValue().getGuid(), GUID);
    }
    
    @Test
    public void deleteFileDefault() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StatusMessage message = controller.deleteFile(GUID, null);
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFile(TEST_APP_ID, GUID);
    }

    @Test
    public void deleteTemplate() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StatusMessage message = controller.deleteFile(GUID, "false");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFile(TEST_APP_ID, GUID);
    }

    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StatusMessage message = controller.deleteFile(GUID, "true");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFile(TEST_APP_ID, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        StatusMessage message = controller.deleteFile(GUID, "true");
        assertEquals(message.getMessage(), DELETE_MSG.getMessage());
        
        verify(mockFileService).deleteFilePermanently(TEST_APP_ID, GUID);
    }
    
    @Test
    public void createFileRevision() throws Exception {
        FileRevision revision = new FileRevision();
        revision.setName("name");
        revision.setDescription("description");
        revision.setMimeType("text/plain");
        mockRequestBody(mockRequest, revision);
        
        controller.createFileRevision(GUID);
        
        verify(mockFileService).createFileRevision(eq(TEST_APP_ID), revisionCaptor.capture());
        FileRevision captured = revisionCaptor.getValue();
        assertEquals(GUID, captured.getFileGuid());
        assertEquals("name", captured.getName());
        assertEquals("description", captured.getDescription());
        assertEquals("text/plain", captured.getMimeType());
    }
    
    @Test
    public void finishFileRevision() throws Exception {
        StatusMessage message = controller.finishFileRevision(GUID, CREATED_ON.toString());
        assertEquals(UPLOAD_FINISHED_MSG, message);

        verify(mockFileService).finishFileRevision(eq(TEST_APP_ID), eq(GUID), dateTimeCaptor.capture());
        assertEquals(CREATED_ON.toString(), dateTimeCaptor.getValue().toString());
    }
    
    @Test
    public void getFileRevisions() throws Exception {
        PagedResourceList<FileRevision> list = new PagedResourceList<>(ImmutableList.of(new FileRevision(), new FileRevision()), 2);
        when(mockFileService.getFileRevisions(TEST_APP_ID, GUID, 10, 50)).thenReturn(list);
        
        PagedResourceList<FileRevision> revisions = controller.getFileRevisions(GUID, "10", "50");
        assertSame(revisions, list);
        
        verify(mockFileService).getFileRevisions(TEST_APP_ID, GUID, 10, 50);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*bad-value is not an integer.*")
    public void getFileRevisionsBadOffset() { 
        controller.getFileRevisions(GUID, "bad-value", "50");
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*bad-value is not an integer.*")
    public void getFileRevisionsBadPageSize() { 
        controller.getFileRevisions(GUID, "10", "bad-value");
    }
    
    @Test
    public void getFileRevisionsDefaults() { 
        controller.getFileRevisions(GUID, null, null);
        
        verify(mockFileService).getFileRevisions(TEST_APP_ID, GUID, 0, API_DEFAULT_PAGE_SIZE);
    }
    
    @Test
    public void getFileRevision() { 
        FileRevision revision = new FileRevision();
        when(mockFileService.getFile(TEST_APP_ID, GUID)).thenReturn(new FileMetadata());
        when(mockFileService.getFileRevision(eq(GUID), any())).thenReturn(Optional.of(revision));
        
        FileRevision retValue = controller.getFileRevision(GUID, CREATED_ON.toString());
        assertSame(retValue, revision);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "FileMetadata not found.")
    public void getFileRevisionFileNotFound() throws Exception {
        when(mockFileService.getFile(TEST_APP_ID, GUID))
            .thenThrow(new EntityNotFoundException(FileMetadata.class));
        
        controller.getFileRevision(GUID, CREATED_ON.toString());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "FileRevision not found.")
    public void getFileRevisionFileRevisionNotFound() {
        when(mockFileService.getFile(TEST_APP_ID, GUID)).thenReturn(new FileMetadata());
        when(mockFileService.getFileRevision(eq(GUID), any())).thenReturn(Optional.empty());
        
        controller.getFileRevision(GUID, CREATED_ON.toString());
    }
}
