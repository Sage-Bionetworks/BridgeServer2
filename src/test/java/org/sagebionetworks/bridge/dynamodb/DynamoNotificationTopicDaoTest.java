package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.google.common.collect.Lists;

public class DynamoNotificationTopicDaoTest {
    private static final Set<String> ALL_OF_GROUP_SET = ImmutableSet.of("group1", "group2");
    private static final String GUID_WITH_CRITERIA = "topic-guid-with-criteria";
    private static final String GUID_WITHOUT_CRITERIA = "topic-guid-without-criteria";
    private static final String TOPIC_ARN = "topic-arn";

    private static void assertCriteria(String expectedTopicGuid, Criteria criteria) {
        assertEquals(criteria.getKey(), DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX + expectedTopicGuid);
        assertEquals(criteria.getAllOfGroups(), ALL_OF_GROUP_SET);
    }

    // Helper method to make a criteria. Note that we can't just make this a constant, because the DAO actually
    // modifies this.
    private static Criteria makeCriteria() {
        Criteria criteria = Criteria.create();
        criteria.setKey(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX + GUID_WITH_CRITERIA);
        criteria.setAllOfGroups(ImmutableSet.of("group1", "group2"));
        return criteria;
    }
    
    @Mock
    private CriteriaDao mockCriteriaDao;
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private AmazonSNSClient mockSnsClient;

    @Mock
    private BridgeConfig mockConfig;
    
    @Mock
    private QueryResultPage<DynamoNotificationTopic> mockQueryResultPage;
    
    @Captor
    private ArgumentCaptor<DynamoNotificationTopic> topicCaptor;
    
    @Captor
    private ArgumentCaptor<DeleteTopicRequest> deleteTopicRequestCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoNotificationTopic>> queryExpressionCaptor;
    
    private DynamoNotificationTopicDao dao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        // Mock criteria DAO.
        when(mockCriteriaDao.getCriteria(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX + GUID_WITH_CRITERIA))
                .thenReturn(makeCriteria());

        // Set up topic DAO.
        dao = new DynamoNotificationTopicDao();
        dao.setCriteriaDao(mockCriteriaDao);
        dao.setNotificationTopicMapper(mockMapper);
        dao.setSnsClient(mockSnsClient);
        dao.setBridgeConfig(mockConfig);

        // Mock config.
        doReturn(Environment.LOCAL).when(mockConfig).getEnvironment();
    }
    
    @Test
    public void createTopic() {
        // Mock SNS client.
        when(mockSnsClient.createTopic(anyString())).thenReturn(new CreateTopicResult().withTopicArn(TOPIC_ARN));

        // Execute.
        NotificationTopic topic = getNotificationTopic();
        topic.setCriteria(null);
        NotificationTopic saved = dao.createTopic(topic);

        // Verify SNS calls.
        verify(mockSnsClient).createTopic("api-local-" + saved.getGuid());
        verify(mockSnsClient).setTopicAttributes(TOPIC_ARN, DynamoNotificationTopicDao.ATTR_DISPLAY_NAME,
                "Short Name");

        // Verify DDB mapper.
        verify(mockMapper).save(topicCaptor.capture());
        DynamoNotificationTopic captured = topicCaptor.getValue();
        assertEquals(captured.getName(), topic.getName());
        assertEquals(captured.getDescription(), topic.getDescription());
        assertEquals(captured.getStudyId(), topic.getStudyId());
        assertEquals(captured.getTopicARN(), TOPIC_ARN);
        assertEquals(captured.getGuid(), topic.getGuid());
        assertTrue(captured.getCreatedOn() > 0);
        assertTrue(captured.getModifiedOn() > 0);
        // This was set by the methods. Can't be set by the caller.
        assertNotEquals(captured.getGuid(), getNotificationTopic().getGuid());

        // This topic has no criteria.
        assertNull(captured.getCriteria());
        verifyZeroInteractions(mockCriteriaDao);
    }
    
    @Test
    public void createTopicWithCriteria() {
        // Mock SNS client.
        when(mockSnsClient.createTopic(anyString())).thenReturn(new CreateTopicResult().withTopicArn(TOPIC_ARN));

        // Execute.
        NotificationTopic topic = getNotificationTopic();
        topic.setCriteria(makeCriteria());
        dao.createTopic(topic);

        // Verify that the saved topic has the right criteria. (Everything else is tested elsewhere.)
        verify(mockMapper).save(topicCaptor.capture());
        DynamoNotificationTopic captured = topicCaptor.getValue();
        assertCriteria(captured.getGuid(), captured.getCriteria());

        // Verify the saved criteria is correct.
        ArgumentCaptor<Criteria> savedCriteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        verify(mockCriteriaDao).createOrUpdateCriteria(savedCriteriaCaptor.capture());
        assertCriteria(captured.getGuid(), savedCriteriaCaptor.getValue());
    }

    @Test
    public void deleteAllTopics() {
        NotificationTopic topic1 = getNotificationTopic();
        NotificationTopic topic2 = getNotificationTopic();
        topic2.setGuid("GHI-JKL");
        topic2.setTopicARN("other:topic:arn");
        
        when(mockMapper.load(any())).thenReturn(topic1, topic2);
        
        List<NotificationTopic> results = Lists.newArrayList(topic1, topic2);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockMapper).queryPage(eq(DynamoNotificationTopic.class), any());
        
        dao.deleteAllTopics(API_APP_ID);
        
        verify(mockMapper, times(2)).delete(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getAllValues().get(0);
        assertEquals(captured.getStudyId(), topic1.getStudyId());
        assertEquals(captured.getGuid(), topic1.getGuid());
        
        captured = topicCaptor.getAllValues().get(1);
        assertEquals(captured.getStudyId(), topic2.getStudyId());
        assertEquals(captured.getGuid(), topic2.getGuid());
        
        verify(mockSnsClient, times(2)).deleteTopic(deleteTopicRequestCaptor.capture());
        DeleteTopicRequest request = deleteTopicRequestCaptor.getAllValues().get(0);
        assertEquals(request.getTopicArn(), topic1.getTopicARN());
        
        request = deleteTopicRequestCaptor.getAllValues().get(1);
        assertEquals(request.getTopicArn(), topic2.getTopicARN());
    }
    
    @Test
    public void deleteTopic() {
        NotificationTopic existingTopic = getNotificationTopic();
        when(mockMapper.load(any())).thenReturn(existingTopic);
        
        dao.deleteTopic(API_APP_ID, "ABC-DEF");
        
        verify(mockMapper).save(topicCaptor.capture());
        assertTrue(topicCaptor.getValue().isDeleted());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTopicAlreadyDeleted() {
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setDeleted(true);
        when(mockMapper.load(any())).thenReturn(existingTopic);
        
        dao.deleteTopic(API_APP_ID, "ABC-DEF");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTopicNotFound() {
        dao.deleteTopic(API_APP_ID, "ABC-DEF");
    }
    
    @Test
    public void deleteTopicPermanently() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockMapper).load(any());

        dao.deleteTopicPermanently(API_APP_ID, "anything");
        
        verify(mockSnsClient).deleteTopic(deleteTopicRequestCaptor.capture());
        DeleteTopicRequest capturedRequest = deleteTopicRequestCaptor.getValue();
        assertEquals(capturedRequest.getTopicArn(), topic.getTopicARN());
        
        verify(mockMapper).delete(topicCaptor.capture());
        NotificationTopic capturedTopic = topicCaptor.getValue();
        assertEquals(capturedTopic.getStudyId(), API_APP_ID);
        assertEquals(capturedTopic.getGuid(), "anything");
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteTopicPermanentlyNotFound() {
        dao.deleteTopicPermanently(API_APP_ID, "anything");
    }

    @Test
    public void deleteTopicPermanentlyWithCriteria() {
        // Mock existing topic with criteria. Note that criteria is loaded in a separate table.
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setGuid(GUID_WITH_CRITERIA);
        existingTopic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(existingTopic);

        // Execute.
        dao.deleteTopicPermanently(API_APP_ID, GUID_WITH_CRITERIA);

        // Verify criteria DAO.
        verify(mockCriteriaDao).deleteCriteria(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX +
                GUID_WITH_CRITERIA);
    }
    
    @Test
    public void getTopic() {
        NotificationTopic topic = getNotificationTopic();
        topic.setCriteria(null);
        doReturn(topic).when(mockMapper).load(any());
        
        NotificationTopic updated = dao.getTopic(API_APP_ID, topic.getGuid());
        assertEquals(updated.getStudyId(), API_APP_ID);
        assertEquals(updated.getGuid(), "topicGuid");
        assertNull(updated.getCriteria());
        
        verify(mockMapper).load(topicCaptor.capture());
        DynamoNotificationTopic capturedTopic = topicCaptor.getValue();
        assertEquals(capturedTopic.getStudyId(), updated.getStudyId());
        assertEquals(capturedTopic.getGuid(), updated.getGuid());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getTopicNotFound() {
        dao.getTopic(API_APP_ID, getNotificationTopic().getGuid());
    }
    
    @Test
    public void getTopicWithCriteria() {
        // Mock mapper.
        NotificationTopic topic = getNotificationTopic();
        topic.setGuid(GUID_WITH_CRITERIA);
        topic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(topic);

        // Execute and validate.
        NotificationTopic result = dao.getTopic(API_APP_ID, GUID_WITH_CRITERIA);
        assertEquals(result.getStudyId(), API_APP_ID);
        assertEquals(result.getGuid(), GUID_WITH_CRITERIA);
        assertCriteria(GUID_WITH_CRITERIA, result.getCriteria());
    }
    
    @Test
    public void listTopicsExcludeDeleted() {
        mockListCall();

        // Execute and verify.
        dao.listTopics(API_APP_ID, false);

        // Verify query.
        verify(mockMapper).queryPage(eq(DynamoNotificationTopic.class), queryExpressionCaptor.capture());
        
        // Query expression does include filter of deleted items.
        DynamoDBQueryExpression<DynamoNotificationTopic> capturedQuery = queryExpressionCaptor.getValue();
        assertEquals(capturedQuery.getQueryFilter().get("deleted").toString(),
                "{AttributeValueList: [{N: 1,}],ComparisonOperator: NE}");
    }
    
    @Test
    public void listTopicsIncludeDeleted() {
        // Mock DDB to return topics.
        DynamoNotificationTopic topicWithoutCriteria = (DynamoNotificationTopic) getNotificationTopic();
        topicWithoutCriteria.setGuid(GUID_WITHOUT_CRITERIA);
        topicWithoutCriteria.setCriteria(null);

        DynamoNotificationTopic topicWithCriteria = (DynamoNotificationTopic) getNotificationTopic();
        topicWithCriteria.setGuid(GUID_WITH_CRITERIA);
        topicWithCriteria.setCriteria(null);

        mockListCall(topicWithoutCriteria, topicWithCriteria);

        // Execute and verify.
        List<NotificationTopic> topics = dao.listTopics(API_APP_ID, true);
        assertEquals(topics.size(), 2);

        assertEquals(topics.get(0).getGuid(), GUID_WITHOUT_CRITERIA);
        assertNull(topics.get(0).getCriteria());

        assertEquals(topics.get(1).getGuid(), GUID_WITH_CRITERIA);
        assertCriteria(GUID_WITH_CRITERIA, topics.get(1).getCriteria());

        // Verify query.
        verify(mockMapper).queryPage(eq(DynamoNotificationTopic.class), queryExpressionCaptor.capture());
        
        DynamoDBQueryExpression<DynamoNotificationTopic> capturedQuery = queryExpressionCaptor.getValue();
        DynamoNotificationTopic capturedTopic = capturedQuery.getHashKeyValues();
        assertEquals(capturedTopic.getStudyId(), API_APP_ID);
        assertNull(capturedTopic.getGuid());
        assertNull(capturedQuery.getQueryFilter()); // deleted are not being filtered out
    }

    // Helper method to mock the list API.
    private void mockListCall(DynamoNotificationTopic... topics) {
        List<DynamoNotificationTopic> topicList = ImmutableList.copyOf(topics);
        when(mockQueryResultPage.getResults()).thenReturn(topicList);
        when(mockMapper.queryPage(eq(DynamoNotificationTopic.class), any())).thenReturn(mockQueryResultPage);
    }
    
    // If the SNS topic creation fails, no DDB record will exist.
    // (okay if SNS topic exists, but DDB record doesn't, so don't need to test that path)
    @Test
    public void noOrphanOnPartialCreate() {
        doThrow(new RuntimeException()).when(mockSnsClient).createTopic(any(String.class));
        
        try {
            dao.createTopic(getNotificationTopic());
            fail("Should have thrown exception");
        } catch(RuntimeException e) {
            // expected exception
        }
        verify(mockMapper, never()).save(any());
    }
    
    
    // If the DDB record fails to delete, the SNS record will still be there.
    @Test
    public void noOrphanOnPermanentDeleteWhereDDBFails() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        doThrow(new RuntimeException()).when(mockMapper).delete(any());
        
        try {
            dao.deleteTopicPermanently(API_APP_ID, "guid");
            fail("Should have thrown exception");
        } catch(RuntimeException e) {
            // expected exception
        }
        verify(mockSnsClient, never()).deleteTopic(any(DeleteTopicRequest.class));
    }
    
    // If the SNS record fails to delete, the DDB record will still be there.
    @Test
    public void noOrphanOnPermanentDeleteWhereSNSFails() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        doThrow(new AmazonServiceException("error")).when(mockSnsClient).deleteTopic(any(DeleteTopicRequest.class));
        
        dao.deleteTopicPermanently(API_APP_ID, "guid");
        
        verify(mockMapper).delete(any());
    }
    
    @Test
    public void updateTopic() throws Exception {
        long timestamp = DateUtils.getCurrentMillisFromEpoch();
        
        NotificationTopic persistedTopic = getNotificationTopic();
        persistedTopic.setCreatedOn(timestamp);
        persistedTopic.setModifiedOn(timestamp);
        doReturn(persistedTopic).when(mockMapper).load(any());
        
        NotificationTopic topic = getNotificationTopic();
        topic.setName("The updated name");
        topic.setDescription("The updated description");
        
        Thread.sleep(3); // forced modifiedOn to be different from timestamp
        
        NotificationTopic updated = dao.updateTopic(topic);
        assertEquals(updated.getName(), "The updated name");
        assertEquals(updated.getDescription(), "The updated description");
        assertEquals(updated.getCreatedOn(), timestamp);
        assertNotEquals(updated.getModifiedOn(), timestamp);
        
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getValue();
        assertEquals(captured.getName(), "The updated name");
        assertEquals(captured.getDescription(), "The updated description");
        assertEquals(captured.getTopicARN(), topic.getTopicARN());
    }

    @Test
    public void updateTopicCanAddCriteria() {
        // Mock existing topic with no criteria. Note that criteria is loaded in a separate table (but is not present
        // in the ohter table).
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setGuid(GUID_WITHOUT_CRITERIA);
        existingTopic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(existingTopic);

        // Execute. Submit a topic with criteria.
        NotificationTopic topicToUpdate = getNotificationTopic();
        topicToUpdate.setGuid(GUID_WITHOUT_CRITERIA);
        topicToUpdate.setCriteria(makeCriteria());
        dao.updateTopic(topicToUpdate);

        // Verify that the saved topic has the right criteria. (Everything else is tested elsewhere.)
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic savedTopic = topicCaptor.getValue();
        assertCriteria(GUID_WITHOUT_CRITERIA, savedTopic.getCriteria());

        // Verify the saved criteria is correct.
        ArgumentCaptor<Criteria> savedCriteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        verify(mockCriteriaDao).createOrUpdateCriteria(savedCriteriaCaptor.capture());
        assertCriteria(GUID_WITHOUT_CRITERIA, savedCriteriaCaptor.getValue());
    }

    @Test
    public void updateTopicCanRemoveCriteria() {
        // Mock existing topic with criteria. Note that criteria is loaded in a separate table.
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setGuid(GUID_WITH_CRITERIA);
        existingTopic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(existingTopic);

        // Execute. The topic we submit has no criteria.
        NotificationTopic topicToUpdate = getNotificationTopic();
        topicToUpdate.setGuid(GUID_WITH_CRITERIA);
        topicToUpdate.setCriteria(null);
        dao.updateTopic(topicToUpdate);

        // Saved topic has no criteria.
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic savedTopic = topicCaptor.getValue();
        assertNull(savedTopic.getCriteria());

        // Criteria DAO is never called to save the criteria.
        verify(mockCriteriaDao, never()).createOrUpdateCriteria(any());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateTopicNotFound() {
        dao.updateTopic(getNotificationTopic());
    }
}
