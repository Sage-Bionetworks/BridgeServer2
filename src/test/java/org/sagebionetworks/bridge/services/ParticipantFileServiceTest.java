package org.sagebionetworks.bridge.services;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.ParticipantFileDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class ParticipantFileServiceTest {

    private static final String UPLOAD_BUCKET = "file-bucket";

    @Mock
    ParticipantFileDao mockFileDao;

    @Mock
    BridgeConfig mockConfig;

    @Mock
    AmazonS3 mockS3Client;

    @InjectMocks
    ParticipantFileService service;

    @Captor
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        when(mockConfig.get("participant-file.bucket")).thenReturn(UPLOAD_BUCKET);
        service.setConfig(mockConfig);

        when(mockS3Client.generatePresignedUrl(any())).thenAnswer(i -> {
            GeneratePresignedUrlRequest request = i.getArgument(0);
            String filePath = request.getKey();
            return new URL("https://" + UPLOAD_BUCKET + "/" + filePath);
        });

        DateTimeUtils.setCurrentMillisFixed(TestConstants.TIMESTAMP.getMillis());
    }

    @AfterClass
    public void afterClass() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void getParticipantFiles() {
        service.getParticipantFiles("test_user", "dummy-key", 20);

        verify(mockFileDao).getParticipantFiles("test_user", "dummy-key", 20);
    }

    @Test
    public void getParticipantFilesNoOffsetKey() {
        service.getParticipantFiles("test_user", null, 20);

        verify(mockFileDao).getParticipantFiles("test_user", null, 20);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantFilesPageSizeTooSmall() {
        service.getParticipantFiles("test_user", null, 1);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantFilesPageSizeTooLarge() {
        service.getParticipantFiles("test_user", null, 5000);
    }

    @Test
    public void getParticipantFile() {
        String downloadUrl = "https://" + UPLOAD_BUCKET + "/test_user/file_id";
        ParticipantFile file = ParticipantFile.create();
        file.setFileId("file_id");
        file.setUserId("test_user");
        file.setAppId("api");
        file.setMimeType("dummy-type");
        file.setCreatedOn(TestConstants.TIMESTAMP);

        when(mockFileDao.getParticipantFile("test_user", "file_id")).thenReturn(Optional.of(file));

        ParticipantFile result = service.getParticipantFile("test_user", "file_id");
        assertEquals(result.getUserId(), "test_user");
        assertEquals(result.getFileId(), "file_id");
        assertEquals(result.getCreatedOn(), TestConstants.TIMESTAMP);
        assertEquals(result.getMimeType(), "dummy-type");
        assertEquals(result.getDownloadUrl(), downloadUrl);
        assertEquals(result.getAppId(), "api");
        assertNull(result.getExpires());
        assertNull(result.getUploadUrl());

        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET);
        assertEquals(request.getMethod(), HttpMethod.GET);
        assertEquals(request.getKey(), "test_user/file_id");
        assertEquals(request.getExpiration(), TestConstants.TIMESTAMP.plusDays(1).toDate());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantFileNoSuchFile() {
        when(mockFileDao.getParticipantFile(any(), any())).thenReturn(Optional.empty());

        service.getParticipantFile("test_user", "file_id");
    }

    @Test
    public void createParticipantFile() {
        String upload = "https://" + UPLOAD_BUCKET + "/test_user/file_id";

        when(mockFileDao.getParticipantFile(any(), any())).thenReturn(Optional.empty());

        // UserId and AppId should not depend on file, it should be manually set by the Service.
        ParticipantFile file = ParticipantFile.create();
        file.setFileId("file_id");
        file.setUserId("wrong_user");
        file.setMimeType("dummy-type");
        file.setAppId("wrong_api");
        ParticipantFile result = service.createParticipantFile("api", "test_user", file);
        assertEquals(result.getUserId(), "test_user");
        assertEquals(result.getFileId(), "file_id");
        assertEquals(result.getMimeType(), "dummy-type");
        assertNotNull(result.getCreatedOn());
        assertEquals(result.getUploadUrl(), upload);
        assertEquals(result.getAppId(), "api");
        assertEquals(TestConstants.TIMESTAMP.compareTo(result.getCreatedOn()), 0);
        assertEquals(TestConstants.TIMESTAMP.plusDays(1).toDate(), result.getExpires().toDate());
        assertNull(result.getDownloadUrl());

        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET);
        assertEquals(request.getMethod(), HttpMethod.PUT);
        assertEquals(request.getContentType(), file.getMimeType());
        assertEquals(request.getKey(), "test_user/file_id");
        assertEquals(request.getRequestParameters().get(Headers.SERVER_SIDE_ENCRYPTION),
                ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        assertEquals(request.getExpiration(), TestConstants.TIMESTAMP.plusDays(1).toDate());

//        verify(mockFileDao).getParticipantFile(eq("test_user"), eq("file_id"));
        verify(mockFileDao).uploadParticipantFile(eq(file));

    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createParticipantFileInvalidFile() {
        ParticipantFile file = ParticipantFile.create();
        service.createParticipantFile("api", "test_user", file);
    }

    @Test
    public void createParticipantFileAlreadyExists() {
        ParticipantFile file = ParticipantFile.create();
        file.setFileId("file_id");
        file.setUserId("test_user");
        file.setAppId("api");
        file.setMimeType("dummy-type");
        file.setCreatedOn(TestConstants.TIMESTAMP);

        ParticipantFile newFile = ParticipantFile.create();
        newFile.setFileId("file_id");
        newFile.setUserId("test_user");
        newFile.setAppId("not_api");
        newFile.setMimeType("new-dummy-type");
        newFile.setCreatedOn(TestConstants.TIMESTAMP.plusDays(1));

        when(mockFileDao.getParticipantFile(eq("test_user"), eq("file_id"))).thenReturn(Optional.of(file));
        ParticipantFile result = service.createParticipantFile("not_api", "test_user", newFile);

//        verify(mockFileDao, never()).uploadParticipantFile(any());
        verify(mockFileDao).uploadParticipantFile(any());
        assertEquals("file_id", result.getFileId());
        assertEquals("test_user", result.getUserId());
        assertEquals("not_api", result.getAppId());
        assertEquals("new-dummy-type", result.getMimeType());
        assertEquals(TestConstants.TIMESTAMP.toDate(), result.getCreatedOn().toDate());
        assertNotNull(result.getUploadUrl());
        assertNull(result.getDownloadUrl());
    }

    @Test
    public void deleteParticipantFile() {
        ParticipantFile file = ParticipantFile.create();
        file.setFileId("file_id");
        file.setUserId("test_user");
        file.setAppId("api");
        file.setMimeType("dummy-type");
        file.setCreatedOn(TestConstants.TIMESTAMP);

        when(mockFileDao.getParticipantFile(eq("test_user"), eq("file_id"))).thenReturn(Optional.of(file));
        service.deleteParticipantFile("test_user", "file_id");

        verify(mockFileDao).deleteParticipantFile(eq("test_user"), eq("file_id"));
        verify(mockS3Client).deleteObject(UPLOAD_BUCKET, "test_user/file_id");
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteParticipantFileButNoSuchFile() {
        when(mockFileDao.getParticipantFile(eq("test_user"), eq("file_id"))).thenReturn(Optional.empty());
        service.deleteParticipantFile("test_user", "file_id");
    }
}
