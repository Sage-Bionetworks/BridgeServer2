package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class DynamoParticipantFileDaoTest {
    private static final ParticipantFile KEY =
            new DynamoParticipantFile("test_user", "test_file");

    private static final DynamoParticipantFile RESULT =
            new DynamoParticipantFile("test_user", "test_file");

    @Mock
    DynamoDBMapper mapper;

    @Mock
    PaginatedQueryList<ParticipantFile> paginatedQueryList;

    @Captor
    ArgumentCaptor<ParticipantFile> fileCaptor;

    @InjectMocks
    @Spy
    DynamoParticipantFileDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        RESULT.setCreatedOn(TestConstants.TIMESTAMP);
        RESULT.setAppId("api");
        RESULT.setMimeType("dummy-type");
        RESULT.setDownloadUrl("fake_url");
    }

    @Test
    public void getParticipantFiles() {
        when(paginatedQueryList.size()).thenReturn(1);
        when(paginatedQueryList.get(0)).thenReturn(RESULT);
        when(mapper.query(eq(ParticipantFile.class), any())).thenReturn(paginatedQueryList);

        ForwardCursorPagedResourceList<ParticipantFile> result = dao.getParticipantFiles(KEY.getUserId(), null, 5);
        assertNotNull(result);
        List<ParticipantFile> resultList = result.getItems();
        assertEquals(resultList.size(), 1);
        ParticipantFile resultFile = resultList.get(0);
        assertEquals(resultFile, RESULT);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantFilesPageSizeTooSmall() {
        dao.getParticipantFiles("test_user", "dummy-key", 1);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getParticipantFilesPageSizeTooLarge() {
        dao.getParticipantFiles("test_user", "dummy-key", 5000);
    }

    @Test
    public void getParticipantFile() {
        when(mapper.load(any())).thenReturn(RESULT);

        Optional<ParticipantFile> result = dao.getParticipantFile(KEY.getUserId(), KEY.getFileId());
        assertTrue(result.isPresent());
        ParticipantFile fileResult = result.get();
        assertEquals(fileResult.getUserId(), RESULT.getUserId());
        assertEquals(fileResult.getFileId(), RESULT.getFileId());
        assertEquals(fileResult.getMimeType(), RESULT.getMimeType());
        assertEquals(fileResult.getCreatedOn(), RESULT.getCreatedOn());
        assertEquals(fileResult.getDownloadUrl(), RESULT.getDownloadUrl());

        verify(mapper).load(fileCaptor.capture());
        ParticipantFile loadedFile = fileCaptor.getValue();
        assertEquals(loadedFile.getUserId(), KEY.getUserId());
        assertEquals(loadedFile.getFileId(), KEY.getFileId());
    }

    @Test
    public void uploadParticipantFile() {
        dao.uploadParticipantFile(KEY);
        verify(mapper).save(KEY);
    }

    @Test
    public void deleteParticipantFile() {
        when(mapper.load(any())).thenReturn(RESULT);
        dao.deleteParticipantFile(KEY.getUserId(), KEY.getFileId());
        verify(mapper).delete(any());
    }

    @Test
    public void deleteParticipantFileNoSuchFile() {
        when(mapper.load(any())).thenReturn(null);
        dao.deleteParticipantFile(KEY.getUserId(), KEY.getFileId());
        verify(mapper, never()).delete(any());
    }
}
