package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.models.files.FileRevisionStatus.AVAILABLE;
import static org.sagebionetworks.bridge.models.files.FileRevisionStatus.PENDING;
import static org.sagebionetworks.bridge.services.FileService.EXPIRATION_IN_MINUTES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.URL;
import java.util.Optional;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.google.common.collect.ImmutableList;

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

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.FileMetadataDao;
import org.sagebionetworks.bridge.dao.FileRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.files.FileMetadata;
import org.sagebionetworks.bridge.models.files.FileRevision;

public class FileServiceTest extends Mockito {

    private static final String UPLOAD_BUCKET = "docs.sagebridge.org";
    private static final String NAME = "oneName";
    private static final String DOWNLOAD_URL_1 = "https://docs.sagebridge.org/oneGuid.1422319112486";
    private static final String DOWNLOAD_URL_2 = "https://docs.sagebridge.org/oneGuid.1422311912486";
    
    @Mock
    FileMetadataDao mockFileDao;
    
    @Mock
    FileRevisionDao mockFileRevisionDao;
    
    @Mock
    AmazonS3 mockS3Client;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Captor
    ArgumentCaptor<FileMetadata> metadataCaptor;
    
    @Captor
    ArgumentCaptor<FileRevision> revisionCaptor;
    
    @Captor
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor;
    
    @InjectMocks
    @Spy
    FileService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(mockConfig.getHostnameWithPostfix("docs")).thenReturn(UPLOAD_BUCKET);
        when(mockConfig.getEnvironment()).thenReturn(PROD);
        service.setConfig(mockConfig);
        
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
    
    @Test
    public void getFileRevisions() {
        FileMetadata metadata = new FileMetadata();
        doReturn(metadata).when(service).getFile(TEST_STUDY, GUID);
        
        FileRevision rev1 = new FileRevision();
        rev1.setFileGuid(GUID);
        rev1.setCreatedOn(TIMESTAMP);
        
        FileRevision rev2 = new FileRevision();
        rev2.setFileGuid(GUID);
        rev2.setCreatedOn(TIMESTAMP.minusHours(2));
        
        PagedResourceList<FileRevision> page = new PagedResourceList<>(ImmutableList.of(rev1, rev2), 2);
        when(mockFileRevisionDao.getFileRevisions(GUID, 100, 25)).thenReturn(page);
        
        ResourceList<FileRevision> captured = service.getFileRevisions(TEST_STUDY,GUID, 100, 25);
        assertEquals(2, captured.getItems().size());
        assertEquals(DOWNLOAD_URL_1, captured.getItems().get(0).getDownloadURL());
        assertEquals(DOWNLOAD_URL_2, captured.getItems().get(1).getDownloadURL());
        
        verify(mockFileRevisionDao).getFileRevisions(GUID, 100, 25);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getFileRevisionsPageSizeTooSmall() {
        FileMetadata metadata = new FileMetadata();
        doReturn(metadata).when(service).getFile(TEST_STUDY, GUID);
        
        service.getFileRevisions(TEST_STUDY,GUID, 0, 1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getFileRevisionsSizeTooLarge() {
        FileMetadata metadata = new FileMetadata();
        doReturn(metadata).when(service).getFile(TEST_STUDY, GUID);
        
        service.getFileRevisions(TEST_STUDY, GUID, 0, 1000);
    }
    
    @Test
    public void createFileRevision() throws Exception {
        FileMetadata metadata = new FileMetadata();
        doReturn(metadata).when(service).getFile(TEST_STUDY, GUID);
        
        URL url = new URL("https://" + UPLOAD_BUCKET);
        when(mockS3Client.generatePresignedUrl(any())).thenReturn(url);
        
        FileRevision revision = new FileRevision();
        revision.setName("name.pdf");
        revision.setFileGuid(GUID);
        revision.setMimeType("application/pdf");
        
        FileRevision returned = service.createFileRevision(TEST_STUDY, revision);
        assertEquals(returned.getCreatedOn(), TIMESTAMP);
        assertEquals(returned.getStatus(), PENDING);
        assertEquals(returned.getUploadURL(), "https://" + UPLOAD_BUCKET);
        assertEquals(returned.getDownloadURL(), "https://docs.sagebridge.org/oneGuid.1422319112486");
        
        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getExpiration().getTime(), TIMESTAMP.plusMinutes(EXPIRATION_IN_MINUTES).getMillis());
        assertEquals(request.getBucketName(), UPLOAD_BUCKET);
        assertEquals(request.getKey(), GUID + "." + TIMESTAMP.getMillis());
        assertEquals(request.getContentType(), "application/pdf");
        assertEquals(request.getResponseHeaders().getContentDisposition(), "attachment; filename=\"name.pdf\"");
        
        verify(mockFileRevisionDao).createFileRevision(revisionCaptor.capture());
        assertSame(revisionCaptor.getValue(), returned);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*FileMetadata not found.*")
    public void createFileRevisionNotFound() throws Exception {
        FileRevision revision = new FileRevision();
        revision.setFileGuid(GUID);
        
        service.createFileRevision(TEST_STUDY, revision);
    }
        
    @Test
    public void finishFileRevision() {
        FileRevision existing = new FileRevision();
        existing.setFileGuid(GUID);        
        existing.setCreatedOn(TIMESTAMP);
        when(mockFileRevisionDao.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.of(existing));
        
        when(mockS3Client.doesObjectExist(UPLOAD_BUCKET, GUID + "." + TIMESTAMP.getMillis())).thenReturn(true);
                
        service.finishFileRevision(TEST_STUDY, GUID, TIMESTAMP);
        
        verify(mockFileRevisionDao).updateFileRevision(revisionCaptor.capture());
        assertNull(revisionCaptor.getValue().getUploadURL());
        assertEquals(revisionCaptor.getValue().getStatus(), AVAILABLE);
        
        verify(mockFileRevisionDao).updateFileRevision(existing);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*FileRevision not found.*")
    public void finishFileRevisionNotFound() {
        when(mockFileRevisionDao.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.empty());
        
        service.finishFileRevision(TEST_STUDY, GUID, TIMESTAMP);
    }
    
    @Test
    public void finishFileRevisionS3FileNotFound() { 
        FileRevision existing = new FileRevision();
        existing.setFileGuid(GUID);        
        existing.setCreatedOn(TIMESTAMP);
        when(mockFileRevisionDao.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.of(existing));
        when(mockS3Client.doesObjectExist(UPLOAD_BUCKET, GUID + "." + TIMESTAMP.getMillis())).thenReturn(false);
        try {
            service.finishFileRevision(TEST_STUDY, GUID, TIMESTAMP);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockFileRevisionDao).deleteFileRevision(existing);
    }
    
    @Test
    public void getFileRevision() { 
        FileRevision revision = new FileRevision();
        when(mockFileRevisionDao.getFileRevision(GUID, TIMESTAMP)).thenReturn(Optional.of(revision));
        
        Optional<FileRevision> returned = service.getFileRevision(GUID, TIMESTAMP);
        assertSame(returned.get(), revision);
        
        verify(mockFileRevisionDao).getFileRevision(GUID, TIMESTAMP);
    }
}
