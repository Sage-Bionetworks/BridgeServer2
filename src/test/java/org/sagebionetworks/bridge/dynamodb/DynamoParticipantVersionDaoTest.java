package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;

@SuppressWarnings("unchecked")
public class DynamoParticipantVersionDaoTest {
    private static final int PARTICIPANT_VERSION = 23;

    @Mock
    private DynamoDBMapper mockMapper;

    @InjectMocks
    @Spy
    private DynamoParticipantVersionDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void create() {
        // Set the version so we can make sure we clear it before creating it.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        participantVersion.setVersion(42L);

        // Execute and validate.
        dao.createParticipantVersion(participantVersion);
        assertNull(participantVersion.getVersion());
        verify(mockMapper).save(same(participantVersion));
    }

    @Test
    public void deleteAllForHealthCode() {
        // Mock dependencies.
        List<DynamoParticipantVersion> participantVersionList = ImmutableList.of(new DynamoParticipantVersion());
        doReturn(participantVersionList).when(dao).queryHelper(any());

        // Execute and validate.
        dao.deleteParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE);
        verify(mockMapper).batchDelete(same(participantVersionList));
    }

    @Test
    public void deleteAllForHealthCode_NoVersions() {
        // Mock dependencies.
        doReturn(ImmutableList.of()).when(dao).queryHelper(any());

        // Execute and validate.
        dao.deleteParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE);
        verify(mockMapper, never()).batchDelete(any(Iterable.class));
    }

    @Test
    public void getAllForHealthCode() {
        // Mock dependencies.
        ParticipantVersion participantVersion = new DynamoParticipantVersion();
        doReturn(ImmutableList.of(participantVersion)).when(dao).queryHelper(any());

        // Execute and validate.
        List<ParticipantVersion> resultList = dao.getAllParticipantVersionsForHealthCode(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE);
        assertEquals(resultList.size(), 1);
        assertSame(resultList.get(0), participantVersion);

        ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantVersion>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoParticipantVersion> query = queryCaptor.getValue();
        ParticipantVersion queryHashKey = query.getHashKeyValues();
        assertEquals(queryHashKey.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(queryHashKey.getHealthCode(), TestConstants.HEALTH_CODE);
    }

    @Test
    public void getLatestForHealthCode() {
        // Mock dependencies.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        QueryResultPage<DynamoParticipantVersion> queryResultPage = new QueryResultPage<>();
        queryResultPage.setResults(ImmutableList.of(participantVersion));
        when(mockMapper.queryPage(eq(DynamoParticipantVersion.class), any())).thenReturn(queryResultPage);

        // Execute and validate.
        Optional<ParticipantVersion> resultOpt = dao.getLatestParticipantVersionForHealthCode(
                TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE);
        assertTrue(resultOpt.isPresent());
        assertSame(resultOpt.get(), participantVersion);

        ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantVersion>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(mockMapper).queryPage(eq(DynamoParticipantVersion.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoParticipantVersion> query = queryCaptor.getValue();
        assertFalse(query.isScanIndexForward());
        assertEquals(query.getLimit().intValue(), 1);

        ParticipantVersion queryHashKey = query.getHashKeyValues();
        assertEquals(queryHashKey.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(queryHashKey.getHealthCode(), TestConstants.HEALTH_CODE);
    }

    @Test
    public void getLatestForHealthCode_NoVersions() {
        // Mock dependencies.
        QueryResultPage<DynamoParticipantVersion> queryResultPage = new QueryResultPage<>();
        queryResultPage.setResults(ImmutableList.of());
        when(mockMapper.queryPage(eq(DynamoParticipantVersion.class), any())).thenReturn(queryResultPage);

        // Execute and validate.
        Optional<ParticipantVersion> resultOpt = dao.getLatestParticipantVersionForHealthCode(
                TestConstants.TEST_APP_ID, TestConstants.HEALTH_CODE);
        assertFalse(resultOpt.isPresent());
    }

    @Test
    public void getParticipantVersion() {
        // Mock dependencies.
        DynamoParticipantVersion participantVersion = new DynamoParticipantVersion();
        when(mockMapper.load(any(DynamoParticipantVersion.class))).thenReturn(participantVersion);

        // Execute and validate.
        Optional<ParticipantVersion>resultOpt = dao.getParticipantVersion(TestConstants.TEST_APP_ID,
                TestConstants.HEALTH_CODE, PARTICIPANT_VERSION);
        assertTrue(resultOpt.isPresent());
        assertSame(resultOpt.get(), participantVersion);

        ArgumentCaptor<ParticipantVersion> hashKeyCaptor = ArgumentCaptor.forClass(ParticipantVersion.class);
        verify(mockMapper).load(hashKeyCaptor.capture());
        ParticipantVersion hashKey = hashKeyCaptor.getValue();
        assertEquals(hashKey.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(hashKey.getHealthCode(), TestConstants.HEALTH_CODE);
        assertEquals(hashKey.getParticipantVersion(), PARTICIPANT_VERSION);
    }
}
