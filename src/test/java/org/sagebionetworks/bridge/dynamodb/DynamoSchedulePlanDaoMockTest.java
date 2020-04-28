package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.EQ;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.schedules.CriteriaScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleCriteria;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class DynamoSchedulePlanDaoMockTest extends Mockito {
    
    static final Set<String> ALL_OF_GROUPS = ImmutableSet.of("a","b");
    static final Set<String> NONE_OF_GROUPS = ImmutableSet.of("c","d");
    static final DateTime TIMESTAMP = DateTime.now();
    static final String GUID = "oneGuid";
    static final String SCHEDULE_CRITERIA_KEY = "scheduleCriteria:"+GUID+":0";
    
    @InjectMocks
    @Spy
    DynamoSchedulePlanDao dao;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    CriteriaDao mockCriteriaDao;
    
    @Mock
    QueryResultPage<DynamoSchedulePlan> queryResultsPage;
    
    @Captor
    ArgumentCaptor<SchedulePlan> schedulePlanCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoSchedulePlan>> queryCaptor;
    
    @Captor
    ArgumentCaptor<Criteria> criteriaCaptor;

    DynamoSchedulePlan schedulePlan;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        
        schedulePlan = (DynamoSchedulePlan)constructSchedulePlan();
        
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy) schedulePlan.getStrategy();
        ScheduleCriteria scheduleCriteria = strategy.getScheduleCriteria().get(0);
        Criteria criteria = scheduleCriteria.getCriteria();
        
        when(mockCriteriaDao.getCriteria(SCHEDULE_CRITERIA_KEY)).thenReturn(criteria);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    private void mockSchedulePlanQuery() {
        List<DynamoSchedulePlan> list = Lists.newArrayList(schedulePlan);
        when(queryResultsPage.getResults()).thenReturn(list);
        when(mockMapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
    }
    
    private SchedulePlan constructSchedulePlan() {
        SchedulePlan schedulePlan = new DynamoSchedulePlan();
        schedulePlan.setGuid(GUID);
        schedulePlan.setLabel("Schedule Plan");
        schedulePlan.setStudyKey(TEST_APP_ID);
        schedulePlan.setDeleted(false);
        
        Schedule schedule = TestUtils.getSchedule("My Schedule");
        Criteria criteria = TestUtils.createCriteria(2, 10, ALL_OF_GROUPS, NONE_OF_GROUPS);
        criteria.setKey(SCHEDULE_CRITERIA_KEY);
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.getScheduleCriteria().add(scheduleCriteria);
        
        schedulePlan.setStrategy(strategy);
        return schedulePlan;
    }
    
    @Test
    public void getSchedulePlans() {
        mockSchedulePlanQuery();
        List<SchedulePlan> plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_APP_ID, false);
        assertEquals(plans.size(), 1);
        
        verify(mockMapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        
        assertEquals(queryCaptor.getValue().getHashKeyValues().getStudyKey(), TEST_APP_ID);
    }
    
    @Test
    public void getSchedulePlansRetrievesCriteria() {
        mockSchedulePlanQuery();
        List<SchedulePlan> plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_APP_ID, false);
        
        SchedulePlan plan = plans.get(0);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(mockCriteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(IOS, 1);
        persistedCriteria.setMaxAppVersion(IOS, 65);
        when(mockCriteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plans = dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_APP_ID, false);
        plan = plans.get(0);
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(1));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(65));
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void getSchedulePlanRetrievesCriteria() {
        mockSchedulePlanQuery();
        SchedulePlan plan = dao.getSchedulePlan(TEST_APP_ID, schedulePlan.getGuid());
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        String key = criteria.getKey();
        verify(mockCriteriaDao).getCriteria(key);
        
        // now have criteriaDao return a different criteria object, that should update the plan
        Criteria persistedCriteria = Criteria.create();
        persistedCriteria.setMinAppVersion(IOS, 1);
        persistedCriteria.setMaxAppVersion(IOS, 65);
        when(mockCriteriaDao.getCriteria(key)).thenReturn(persistedCriteria);
        
        plan = dao.getSchedulePlan(TEST_APP_ID, plan.getGuid());
        strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(1));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(65));
        assertTrue(criteria.getAllOfGroups().isEmpty());
        assertTrue(criteria.getNoneOfGroups().isEmpty());
    }
    
    @Test
    public void createSchedulePlanWritesCriteria() {
        SchedulePlan plan = dao.createSchedulePlan(TEST_APP_ID, schedulePlan);
        CriteriaScheduleStrategy strategy = (CriteriaScheduleStrategy)plan.getStrategy();
        
        Criteria criteria = strategy.getScheduleCriteria().get(0).getCriteria();
        assertCriteria(criteria);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria crit = criteriaCaptor.getValue();
        assertCriteria(crit);        
    }
    
    @Test
    public void updateSchedulePlanWritesCriteria() {
        mockSchedulePlanQuery();
        // Create a copy of the schedulePlan or this test does not work
        SchedulePlan update = SchedulePlan.create();
        update.setGuid(schedulePlan.getGuid());
        update.setLabel(schedulePlan.getLabel());
        update.setStudyKey(schedulePlan.getStudyKey());
        
        // But modify the criteria...
        Schedule schedule = TestUtils.getSchedule("My Schedule");
        Criteria criteria = TestUtils.createCriteria(100, 200, ALL_OF_GROUPS, NONE_OF_GROUPS);
        criteria.setKey(SCHEDULE_CRITERIA_KEY);
        CriteriaScheduleStrategy strategy = new CriteriaScheduleStrategy();
        ScheduleCriteria scheduleCriteria = new ScheduleCriteria(schedule, criteria);
        strategy.getScheduleCriteria().add(scheduleCriteria);
        
        update.setStrategy(strategy);
        
        ArgumentCaptor<Criteria> criteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        
        SchedulePlan updated = dao.updateSchedulePlan(TEST_APP_ID, update);
        
        // Verify returned object still has changes
        strategy = (CriteriaScheduleStrategy)updated.getStrategy();
        scheduleCriteria = strategy.getScheduleCriteria().get(0);
        Criteria returnedCriteria = scheduleCriteria.getCriteria();
        assertEquals(returnedCriteria.getMinAppVersion(IOS), new Integer(100));
        assertEquals(returnedCriteria.getMaxAppVersion(IOS), new Integer(200));        
        
        // Verify they were persisted
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria updatedCriteria = criteriaCaptor.getValue();
        assertEquals(updatedCriteria.getMinAppVersion(IOS), new Integer(100));
        assertEquals(updatedCriteria.getMaxAppVersion(IOS), new Integer(200));        
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateMissingSchedulePlan() {
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        when(mockMapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        
        SchedulePlan update = SchedulePlan.create();
        update.setGuid("doesNotExist");
        dao.updateSchedulePlan(TEST_APP_ID, update);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateDeletedSchedulePlan() {
        schedulePlan.setDeleted(true);
        mockSchedulePlanQuery();
        
        SchedulePlan update = SchedulePlan.create();
        update.setDeleted(true);
        update.setGuid(schedulePlan.getGuid());
        dao.updateSchedulePlan(TEST_APP_ID, update);
    }
    
    @Test
    public void createSchedulePlanNeverDeleted() {
        SchedulePlan plan = SchedulePlan.create();
        plan.setDeleted(true);
        
        dao.createSchedulePlan(TEST_APP_ID, plan);
        
        verify(mockMapper).save(schedulePlanCaptor.capture());
        assertFalse(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test
    public void getSchedulePlansExcludesLogicallyDeleted() {
        mockSchedulePlanQuery();
        dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_APP_ID, false);
        
        verify(mockMapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        
        Condition condition = new Condition().withComparisonOperator(ComparisonOperator.NE)
                .withAttributeValueList(new AttributeValue().withN("1"));
        
        assertEquals(queryCaptor.getValue().getQueryFilter().get("deleted"), condition);
    }
    
    @Test
    public void getSchedulePlansIncludesLogicallyDeleted() {
        mockSchedulePlanQuery();
        dao.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_APP_ID, true);
        
        verify(mockMapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        
        assertNull(queryCaptor.getValue().getQueryFilter());
    }

    @Test
    public void deleteSchedulePlan() {
        mockSchedulePlanQuery();
        assertFalse(schedulePlan.isDeleted());
        
        dao.deleteSchedulePlan(TEST_APP_ID, schedulePlan.getGuid());
        
        verify(mockMapper).save(schedulePlanCaptor.capture());
        verify(mockCriteriaDao, never()).deleteCriteria(any());
        
        assertTrue(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test
    public void deleteSchedulePlanPermanently() {
        mockSchedulePlanQuery();
        dao.deleteSchedulePlanPermanently(TEST_APP_ID, schedulePlan.getGuid());
        
        verify(mockMapper).delete(schedulePlanCaptor.capture());
        verify(mockCriteriaDao).deleteCriteria(any());
    }
    
    @Test
    public void deleteSchedulePlanPermanentlyDeletesCriteria() {
        mockSchedulePlanQuery();
        dao.deleteSchedulePlanPermanently(TEST_APP_ID, schedulePlan.getGuid());
        
        verify(mockCriteriaDao).deleteCriteria(SCHEDULE_CRITERIA_KEY);
    }
    
    @Test
    public void logicallyDeletingMissingSchedulePlanThrows() {
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        when(mockMapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        
        try {
            dao.deleteSchedulePlan(TEST_APP_ID, "does-not-exist");
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockMapper, never()).save(any());
    }

    @Test
    public void permanentlyDeletingMissingSchedulePlanThrows() {
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        when(mockMapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        try {
            dao.deleteSchedulePlanPermanently(TEST_APP_ID, "does-not-exist");
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockMapper, never()).delete(any());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateLogicallyDeletedSchedulePlanThrows() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        // Both are deleted, so this should throw.
        dao.updateSchedulePlan(TEST_APP_ID, schedulePlan);
    }
    
    @Test
    public void updateCanUndeleteSchedulePlan() { 
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        SchedulePlan update = constructSchedulePlan();
        update.setDeleted(false);
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_APP_ID, update);
        assertFalse(returned.isDeleted());
        verify(mockMapper).save(schedulePlanCaptor.capture());
        
        assertFalse(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test
    public void updateCanDeleteSchedulePlan() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(false);
        
        SchedulePlan update = constructSchedulePlan();
        update.setDeleted(true);
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_APP_ID, update);
        assertTrue(returned.isDeleted());
        verify(mockMapper).save(schedulePlanCaptor.capture());
        
        assertTrue(schedulePlanCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteLogicallyDeletedSchedulePlanThrows() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        dao.deleteSchedulePlan(TEST_APP_ID, schedulePlan.getGuid());
    }
    
    @Test
    public void deletePermenantlyLogicallyDeletedSchedulePlanWorks() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        dao.deleteSchedulePlanPermanently(TEST_APP_ID, schedulePlan.getGuid());
        
        verify(mockMapper).delete(any());
    }
    
    @Test
    public void createSchedulePlan() {
        when(dao.generateGuid()).thenReturn(GUID);
        
        // This creates a criteria schedule plan which is the most complicated to persist.
        SchedulePlan plan = constructSchedulePlan();
        plan.setStudyKey(null); // not allowed, should be set to app argument
        plan.setDeleted(true); // not allowed, should be set to false
        plan.setVersion(1L); // not allowed, should be set to null
        Criteria criteria = ((CriteriaScheduleStrategy)plan.getStrategy()).getScheduleCriteria().get(0).getCriteria();
        criteria.setKey(null); // verify this is set by the dao
        
        SchedulePlan returned = dao.createSchedulePlan(TEST_APP_ID, plan);
        
        // Verify the criteria were persisted
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        assertSame(criteriaCaptor.getValue(), criteria);
        assertEquals(criteriaCaptor.getValue().getKey(), SCHEDULE_CRITERIA_KEY);
        
        verify(mockMapper).save(schedulePlanCaptor.capture());
        SchedulePlan persisted = schedulePlanCaptor.getValue();

        assertEquals(persisted.getStudyKey(), TEST_APP_ID);
        assertEquals(persisted.getGuid(), GUID);
        assertEquals(persisted.getModifiedOn(), TIMESTAMP.getMillis());
        assertFalse(persisted.isDeleted());
        assertNull(persisted.getVersion());
        
        // verify as well that this is all returned to the caller
        assertSame(returned, plan);
    }
    
    @Test
    public void updateSchedulePlan() {
        Criteria criteria = ((CriteriaScheduleStrategy) schedulePlan.getStrategy()).getScheduleCriteria().get(0)
                .getCriteria();
        
        mockSchedulePlanQuery();

        when(mockCriteriaDao.createOrUpdateCriteria(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        SchedulePlan plan = constructSchedulePlan();
        plan.setStudyKey(null); // this will be set
        plan.setModifiedOn(0L); // this will be set
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_APP_ID, plan);
        assertEquals(returned.getStudyKey(), TEST_APP_ID);
        assertEquals(returned.getModifiedOn(), TIMESTAMP.getMillis());
        
        verify(mockMapper).queryPage(eq(DynamoSchedulePlan.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoSchedulePlan> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getStudyKey(), TEST_APP_ID);
        Condition cond = query.getRangeKeyConditions().get("guid");
        assertEquals(cond.getComparisonOperator(), EQ.name());
        assertEquals(cond.getAttributeValueList().get(0).getS(), GUID);
        assertFalse(query.isScanIndexForward());
        
        // Verify the criteria were persisted
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        assertEquals(criteriaCaptor.getValue(), criteria);
        
        verify(mockMapper).save(schedulePlanCaptor.capture());
        SchedulePlan persisted = schedulePlanCaptor.getValue();

        // verify the schedule plan was updated with key fields
        assertEquals(persisted.getStudyKey(), TEST_APP_ID);
        assertEquals(persisted.getModifiedOn(), TIMESTAMP.getMillis());
        assertSame(returned, plan);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, expectedExceptionsMessageRegExp = "SchedulePlan not found.")
    public void updateScheduleNotFound() {
        when(mockMapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());
        
        SchedulePlan plan = constructSchedulePlan();
        
        dao.updateSchedulePlan(TEST_APP_ID, plan);
    }
    
    @Test
    public void updateScheduleCanUndelete() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        DynamoSchedulePlan plan = (DynamoSchedulePlan)constructSchedulePlan();
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_APP_ID, plan);
        assertFalse(returned.isDeleted());
        
        verify(mockMapper).save(schedulePlanCaptor.capture());
        SchedulePlan persisted = schedulePlanCaptor.getValue();
        assertFalse(persisted.isDeleted());
    }
    
    @Test
    public void updateScheduleCanDelete() {
        mockSchedulePlanQuery();
        
        DynamoSchedulePlan plan = (DynamoSchedulePlan)constructSchedulePlan();
        plan.setDeleted(true);
        
        SchedulePlan returned = dao.updateSchedulePlan(TEST_APP_ID, plan);
        assertTrue(returned.isDeleted());
        
        verify(mockMapper).save(schedulePlanCaptor.capture());
        SchedulePlan persisted = schedulePlanCaptor.getValue();
        assertTrue(persisted.isDeleted());        
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateScheduleCannotUpdateLogicallyDeletedPlan() {
        mockSchedulePlanQuery();
        schedulePlan.setDeleted(true);
        
        DynamoSchedulePlan plan = (DynamoSchedulePlan)constructSchedulePlan();
        plan.setDeleted(true);
        
        dao.updateSchedulePlan(TEST_APP_ID, plan);
    }
    
    @Test
    public void getSchedulePlan() {
        mockSchedulePlanQuery();
        
        SchedulePlan plan = dao.getSchedulePlan(TEST_APP_ID, GUID);
        assertSame(plan, schedulePlan);
        
        verify(mockMapper).queryPage(eq(DynamoSchedulePlan.class), any());
        verify(mockCriteriaDao).getCriteria(SCHEDULE_CRITERIA_KEY);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getSchedulePlanNotFound() { 
        when(mockMapper.queryPage(eq(DynamoSchedulePlan.class), any())).thenReturn(queryResultsPage);
        when(queryResultsPage.getResults()).thenReturn(ImmutableList.of());

        SchedulePlan result = dao.getSchedulePlan(TEST_APP_ID, GUID);
        assertEquals(result, schedulePlan);
        
        verify(mockMapper).queryPage(eq(DynamoSchedulePlan.class), any());
        verify(mockCriteriaDao).getCriteria(SCHEDULE_CRITERIA_KEY);
    }
    
    private void assertCriteria(Criteria criteria) {
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(2));
        assertEquals(criteria.getMaxAppVersion(IOS), new Integer(10));
        assertEquals(criteria.getAllOfGroups(), ALL_OF_GROUPS);
        assertEquals(criteria.getNoneOfGroups(), NONE_OF_GROUPS);
    }
}
