package org.sagebionetworks.bridge.services;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.sagebionetworks.bridge.dao.ParticipantDataDao;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantData;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantDataDao;
import org.sagebionetworks.bridge.models.ParticipantData;
import org.testng.annotations.Test;

import java.util.List;

public class ParticipantDataServiceTest {

    static final String USER_ID = "aUserId";
    static final String IDENTIFIER = "anIdentifier";
    static final String OFFSET_KEY = "anOffsetKey";
    static final int PAGE_SIZE = 10;

    @Mock
    ParticipantDataDao mockDao;

    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantData>> queryCaptor;

    @Captor
    ArgumentCaptor<ParticipantData> participantDataCaptor;

    @Captor
    ArgumentCaptor<List<DynamoParticipantData>> dataListCaptor;

    @InjectMocks
    DynamoParticipantDataDao dao;

    @Test
    public void testSetParticipantDataDao() {
    }

    @Test
    public void testGetParticipantData() {
    }

    @Test
    public void testGetParticipantDataRecord() {
    }

    @Test
    public void testSaveParticipantData() {
    }

    @Test
    public void testDeleteParticipantData() {
    }

    @Test
    public void testDeleteParticipantDataRecord() {
    }
}