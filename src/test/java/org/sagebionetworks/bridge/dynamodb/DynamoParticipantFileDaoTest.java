package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;

public class DynamoParticipantFileDaoTest {
    private static final ParticipantFile KEY =
            new DynamoParticipantFile("test_user", "test_file");

    private static final DynamoParticipantFile RESULT =
            new DynamoParticipantFile("test_user", "test_file");

    @Mock
    DynamoDBMapper mapper;

    @Captor
    ArgumentCaptor<ParticipantFile> fileCaptor;

    @InjectMocks
    DynamoParticipantFileDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        RESULT.setCreatedOn(TestConstants.TIMESTAMP);
    }

    @Test
    public void getParticipantFile() {
        when(mapper.load(KEY)).thenReturn(RESULT);

        dao.getParticipantFile(KEY.getUserId(), KEY.getFileId());

        // TODO
    }
}
