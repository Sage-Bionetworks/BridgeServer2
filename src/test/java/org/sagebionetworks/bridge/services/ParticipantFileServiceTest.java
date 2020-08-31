package org.sagebionetworks.bridge.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

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

        ParticipantFile file = ParticipantFile.create();
        file.setFileId("file_id");
        file.setUserId("test_user");
        file.setMimeType("dummy-type");
        file.setAppId("api");
        file.setCreatedOn(TestConstants.TIMESTAMP);
        ParticipantFile result = service.createParticipantFile(file);
        assertEquals(result.getUserId(), "test_user");
        assertEquals(result.getFileId(), "file_id");
        assertEquals(result.getMimeType(), "dummy-type");
        assertEquals(result.getCreatedOn(), TestConstants.TIMESTAMP);
        assertEquals(result.getUploadUrl(), upload);
        assertEquals(result.getAppId(), "api");
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createParticipantFileInvalidFile() {
        ParticipantFile file = ParticipantFile.create();
        service.createParticipantFile(file);
    }

    @Test(expectedExceptions = EntityAlreadyExistsException.class)
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
        newFile.setCreatedOn(TestConstants.TIMESTAMP);

        when(mockFileDao.getParticipantFile(eq("test_user"), eq("file_id"))).thenReturn(Optional.of(file));
        service.createParticipantFile(newFile);
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
