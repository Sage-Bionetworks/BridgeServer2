package org.sagebionetworks.bridge.services;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.AmazonS3Exception;
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
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

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
        when(mockConfig.getInt("participant-file.rate-limiter.test.initial-bytes")).thenReturn(1000);
        when(mockConfig.getInt("participant-file.rate-limiter.test.maximum-bytes")).thenReturn(1000);
        when(mockConfig.getInt("participant-file.rate-limiter.test.refill-interval-seconds")).thenReturn(5);
        when(mockConfig.getInt("participant-file.rate-limiter.test.refill-bytes")).thenReturn(1000);
        service.setConfig(mockConfig);

        when(mockS3Client.generatePresignedUrl(any())).thenAnswer(i -> {
            GeneratePresignedUrlRequest request = i.getArgument(0);
            String filePath = request.getKey();
            return new URL("https://" + UPLOAD_BUCKET + "/" + filePath);
        });

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(100); // 100 B
        when(mockS3Client.getObjectMetadata(any(), any())).thenReturn(metadata);

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

    @Test(expectedExceptions = {LimitExceededException.class})
    public void getParticipantFilesRateLimited() {
        List<ParticipantFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            files.add(ParticipantFile.create());
        }
        when(mockFileDao.getParticipantFiles("userid", null, 100))
                .thenReturn(new ForwardCursorPagedResourceList<>(files, null, true));

        service.getParticipantFiles("userid", null, 100);
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
        assertEquals(result.getExpiresOn().getMillis(), TestConstants.TIMESTAMP.plusDays(1).getMillis());
        assertNull(result.getUploadUrl());

        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET);
        assertEquals(request.getMethod(), HttpMethod.GET);
        assertEquals(request.getKey(), "test_user/file_id");
        assertEquals(request.getExpiration(), TestConstants.TIMESTAMP.plusDays(1).toDate());
    }

    @Test(expectedExceptions = { LimitExceededException.class })
    public void getParticipantFileRateLimited() {
        ParticipantFile file = ParticipantFile.create();
        when(mockFileDao.getParticipantFile("userid", "fileid")).thenReturn(Optional.of(file));

        for (int i = 0; i < 10; i++) {
            try {
                service.getParticipantFile("userid", "fileid");
            } catch (LimitExceededException e) {
                fail(String.format(
                        "RateLimiter should not have rejected download %d of 100 KB with initial of 1 MB", i + 1));
            }
        }
        service.getParticipantFile("userid", "fileid");
    }

    @Test
    public void getParticipantFileS3NotFound() {
        AmazonS3Exception notFoundException = new AmazonS3Exception("404 not found");
        notFoundException.setStatusCode(404);
        when(mockS3Client.getObjectMetadata(any(), any())).thenThrow(notFoundException);

        ParticipantFile file = ParticipantFile.create();
        when(mockFileDao.getParticipantFile("userid", "fileid")).thenReturn(Optional.of(file));
        // should be allowed because 404 from S3 = 0 bytes uploaded = 0 bytes to download
        service.getParticipantFile("userid", "fileid");
    }

    @Test(expectedExceptions = AmazonS3Exception.class)
    public void getParticipantFileS3UnknownException() {
        AmazonS3Exception notFoundException = new AmazonS3Exception("500 internal server error");
        notFoundException.setStatusCode(500);
        when(mockS3Client.getObjectMetadata(any(), any())).thenThrow(notFoundException);

        ParticipantFile file = ParticipantFile.create();
        when(mockFileDao.getParticipantFile("userid", "fileid")).thenReturn(Optional.of(file));
        // should throw AmazonS3Exception
        service.getParticipantFile("userid", "fileid");
    }


    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getParticipantFileNoSuchFile() {
        when(mockFileDao.getParticipantFile(any(), any())).thenReturn(Optional.empty());

        service.getParticipantFile("test_user", "file_id");
    }

    @Test
    public void createParticipantFile() {
        String upload = "https://" + UPLOAD_BUCKET + "/test_user/file_id";

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
        assertEquals(result.getCreatedOn().getMillis(), TestConstants.TIMESTAMP.getMillis());
        assertEquals(result.getExpiresOn().getMillis(), TestConstants.TIMESTAMP.plusDays(1).getMillis());
        assertNull(result.getDownloadUrl());
        
        verify(mockS3Client).deleteObject(eq(UPLOAD_BUCKET), eq("test_user/file_id"));

        verify(mockS3Client).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET);
        assertEquals(request.getMethod(), HttpMethod.PUT);
        assertEquals(request.getContentType(), file.getMimeType());
        assertEquals(request.getKey(), "test_user/file_id");
        assertEquals(request.getRequestParameters().get(Headers.SERVER_SIDE_ENCRYPTION),
                ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        assertEquals(request.getExpiration(), TestConstants.TIMESTAMP.plusDays(1).toDate());

        verify(mockFileDao).uploadParticipantFile(eq(file));
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createParticipantFileInvalidFile() {
        ParticipantFile file = ParticipantFile.create();
        service.createParticipantFile("api", "test_user", file);
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
