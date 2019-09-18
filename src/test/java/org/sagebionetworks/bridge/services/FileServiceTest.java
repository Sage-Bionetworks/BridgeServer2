package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.FileMetadataDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.files.FileMetadata;

public class FileServiceTest extends Mockito {

    private static final String NAME = "oneName";
    
    @Spy
    FileMetadataDao mockFileDao;
    
    @InjectMocks
    @Spy
    FileService service;
    
    @Captor
    ArgumentCaptor<FileMetadata> metadataCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        when(service.getDateTime()).thenReturn(TIMESTAMP);
        when(service.generateGuid()).thenReturn(GUID);
    }
    
    @Test
    public void getFilesExcludeDeleted() {
        service.getFiles(TEST_STUDY, 5, 30, false);
        
        verify(mockFileDao).getFiles(TEST_STUDY, 5, 30, false);
    }

    @Test
    public void getFilesIncludeDeleted() {
        service.getFiles(TEST_STUDY, 0, 25, true);
        
        verify(mockFileDao).getFiles(TEST_STUDY, 0, 25, true);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getFilesPageSizeTooSmall() {
        service.getFiles(TEST_STUDY, 0, 1, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getFilesPageSizeTooLarge() {
        service.getFiles(TEST_STUDY, 0, 1000, false);
    }
    
    @Test
    public void getFile() {
        FileMetadata metadata = new FileMetadata();
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.of(metadata));
        
        FileMetadata returned = service.getFile(TEST_STUDY, GUID);
        assertSame(returned, metadata);
        
        verify(mockFileDao).getFile(TEST_STUDY, GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getFileNotFound() { 
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.empty());
        
        service.getFile(TEST_STUDY, GUID);
    }
    
    @Test
    public void createFile() {
        FileMetadata metadata = new FileMetadata();
        metadata.setName(NAME);
        metadata.setStudyId("garbage"); // ignored
        metadata.setVersion(10L); // ignored
        metadata.setDeleted(true); // ignored
        metadata.setCreatedOn(new DateTime()); // ignored
        metadata.setModifiedOn(new DateTime()); // ignored
        metadata.setGuid("garbage"); // ignored
        when(mockFileDao.createFile(any())).thenReturn(metadata);
        
        FileMetadata returned = service.createFile(TEST_STUDY, metadata);
        
        verify(mockFileDao).createFile(metadataCaptor.capture());
        FileMetadata captured = metadataCaptor.getValue();
        assertSame(captured, returned);
        
        assertEquals(captured.getName(), "oneName");
        assertEquals(captured.getVersion(), 0L);
        assertFalse(captured.isDeleted());
        assertEquals(captured.getGuid(), GUID);
        assertEquals(captured.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(captured.getCreatedOn(), TIMESTAMP);
        assertEquals(captured.getModifiedOn(), TIMESTAMP);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*name is required.*")
    public void createFileValidated() {
        FileMetadata metadata = new FileMetadata();
        service.createFile(TEST_STUDY, metadata);
    }

    @Test
    public void updateFile() {
        FileMetadata persisted = new FileMetadata();
        persisted.setCreatedOn(TIMESTAMP.minusDays(1));
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.of(persisted));
        when(mockFileDao.updateFile(any())).thenReturn(persisted);
        
        FileMetadata metadata = new FileMetadata();
        metadata.setName(NAME);
        metadata.setGuid(GUID);
        FileMetadata returned = service.updateFile(TEST_STUDY, metadata);
        assertEquals(returned, persisted);
        
        verify(mockFileDao).updateFile(metadataCaptor.capture());
        FileMetadata captured = metadataCaptor.getValue();
        assertEquals(captured.getStudyId(), TEST_STUDY_IDENTIFIER);
        assertEquals(captured.getModifiedOn(), TIMESTAMP);
        assertEquals(captured.getCreatedOn(), TIMESTAMP.minusDays(1));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*name is required.*")
    public void updateFileValidated() {
        FileMetadata persisted = new FileMetadata();
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.of(persisted));
        
        FileMetadata metadata = new FileMetadata();
        metadata.setGuid(GUID);
        service.updateFile(TEST_STUDY, metadata);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateFileNoGuid() {
        FileMetadata metadata = new FileMetadata();
        service.updateFile(TEST_STUDY, metadata);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateFileMissing() { 
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.empty());

        FileMetadata metadata = new FileMetadata();
        metadata.setName(NAME);
        metadata.setGuid(GUID);
        service.updateFile(TEST_STUDY, metadata);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateFileFilesEditingLogicallyDeletedFile() {
        FileMetadata persisted = new FileMetadata();
        persisted.setDeleted(true);
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.of(persisted));

        FileMetadata metadata = new FileMetadata();
        metadata.setName(NAME);
        metadata.setGuid(GUID);
        metadata.setDeleted(true);
        service.updateFile(TEST_STUDY, metadata);
    }
    
    @Test
    public void deleteFile() {
        FileMetadata persisted = new FileMetadata();
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.of(persisted));
        
        service.deleteFile(TEST_STUDY, GUID);
        
        verify(mockFileDao).updateFile(metadataCaptor.capture());
        assertTrue(metadataCaptor.getValue().isDeleted());
        assertEquals(metadataCaptor.getValue().getModifiedOn(), TIMESTAMP);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteFileAlreadyDeleted() {
        FileMetadata persisted = new FileMetadata();
        persisted.setDeleted(true);
        when(mockFileDao.getFile(TEST_STUDY, GUID)).thenReturn(Optional.of(persisted));
        
        service.deleteFile(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteFilePermanently() {
        service.deleteFilePermanently(TEST_STUDY, GUID);
        
        verify(mockFileDao).deleteFilePermanently(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteAllStudyFiles() {
        service.deleteAllStudyFiles(TEST_STUDY);
        
        verify(mockFileDao).deleteAllStudyFiles(TEST_STUDY);
    }    
}
