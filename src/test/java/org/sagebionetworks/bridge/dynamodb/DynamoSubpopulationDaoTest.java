package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.dynamodb.DynamoSubpopulationDao.CANNOT_DELETE_DEFAULT_SUBPOP_MSG;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.sagebionetworks.bridge.models.OperatingSystem.IOS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class DynamoSubpopulationDaoTest extends Mockito {

    private static final Criteria CRITERIA = TestUtils.createCriteria(2, 10, 
            Sets.newHashSet("a", "b"), Sets.newHashSet("c", "d"));
    private static final String GUID = "oneGuid";
    private static final String CRITERIA_KEY = "subpopulation:" + GUID;
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(GUID);
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    CriteriaDao mockCriteriaDao;
    
    @Mock
    PaginatedQueryList<DynamoSubpopulation> mockQueryList;
    
    @Captor
    ArgumentCaptor<Subpopulation> subpopCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoSubpopulation>> queryCaptor;
    
    @Captor
    ArgumentCaptor<Criteria> criteriaCaptor;
    
    @InjectMocks
    @Spy
    DynamoSubpopulationDao dao;
    
    DynamoSubpopulation persistedSubpop;

    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
        when(dao.generateGuid()).thenReturn(GUID);
        
        dao.setMapper(mockMapper);
        dao.setCriteriaDao(mockCriteriaDao);
        
        List<DynamoSubpopulation> list = ImmutableList.of((DynamoSubpopulation)createSubpopulation());

        when(mockQueryList.stream()).thenReturn(list.stream());

        persistedSubpop = (DynamoSubpopulation)createSubpopulation();
        doReturn(persistedSubpop).when(mockMapper).load(any());
        doReturn(mockQueryList).when(mockMapper).query(eq(DynamoSubpopulation.class), any());
        
        when(mockCriteriaDao.getCriteria(any())).thenReturn(CRITERIA);
        when(mockCriteriaDao.createOrUpdateCriteria(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    
    private Subpopulation createSubpopulation() {
        Criteria criteria = TestUtils.copyCriteria(CRITERIA);
        criteria.setKey("subpopulation:"+SUBPOP_GUID);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        subpop.setAppId(TEST_APP_ID);
        subpop.setCriteria(criteria);
        return subpop;
    }
    
    @Test
    public void createSubpopulationWritesCriteria() {
        Subpopulation subpop = createSubpopulation();
        
        dao.createSubpopulation(subpop);
        
        Criteria criteria = subpop.getCriteria();
        verify(mockCriteriaDao).createOrUpdateCriteria(criteria);
    }
    
    @Test
    public void createDefaultSubpopulation() {
        when(mockCriteriaDao.createOrUpdateCriteria(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        Subpopulation result = dao.createDefaultSubpopulation(TEST_APP_ID, "study-identifier");
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation subpop = subpopCaptor.getValue();
        assertEquals(subpop.getAppId(), TEST_APP_ID);
        assertEquals(subpop.getGuidString(), TEST_APP_ID);
        assertEquals(subpop.getName(), "Default Consent Group");
        assertEquals(subpop.getStudyIdsAssignedOnConsent(), ImmutableSet.of("study-identifier"));
        assertTrue(subpop.isDefaultGroup());
        assertTrue(subpop.isRequired());
        assertSame(subpop, result);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria criteria = criteriaCaptor.getValue();
        assertEquals(criteria.getKey(), "subpopulation:" + TEST_APP_ID);
        assertEquals(criteria.getMinAppVersion(ANDROID), new Integer(0));
        assertEquals(criteria.getMinAppVersion(IOS), new Integer(0));
        assertSame(criteria, subpop.getCriteria());
    }
    
    @Test
    public void getSubpopulationRetrievesCriteria() {
        Subpopulation subpop = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        Criteria criteria = subpop.getCriteria();
        assertEquals(criteria, CRITERIA);
        
        verify(mockCriteriaDao).getCriteria(criteria.getKey());
        verifyNoMoreInteractions(mockCriteriaDao);
    }
    
    @Test
    public void getSubpopulationConstructsCriteriaIfNotSaved() {
        when(mockCriteriaDao.getCriteria(any())).thenReturn(null);
        
        Subpopulation subpop = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        Criteria criteria = subpop.getCriteria();
        assertNotNull(criteria);
        
        verify(mockCriteriaDao).getCriteria(criteria.getKey());
    }
    
    @Test
    public void physicalDeleteSubpopulationDeletesCriteria() {
        dao.deleteSubpopulationPermanently(TEST_APP_ID, SUBPOP_GUID);
        
        verify(mockCriteriaDao).deleteCriteria(createSubpopulation().getCriteria().getKey());
    }
    
    @Test
    public void logicalDeleteSubpopulationDoesNotDeleteCriteria() {
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        verify(mockCriteriaDao, never()).deleteCriteria(createSubpopulation().getCriteria().getKey());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void doNotAllowLogicalDeleteOfDefault() {
        Subpopulation defaultSubpop = Subpopulation.create();
        defaultSubpop.setGuid(SUBPOP_GUID);
        defaultSubpop.setDefaultGroup(true);
        
        doReturn(defaultSubpop).when(dao).getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
    }
    
    @Test
    public void allowDeleteOfDefault() {
        Subpopulation defaultSubpop = Subpopulation.create();
        defaultSubpop.setGuid(SUBPOP_GUID);
        defaultSubpop.setDefaultGroup(true);
        
        doReturn(defaultSubpop).when(dao).getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        dao.deleteSubpopulationPermanently(TEST_APP_ID, SUBPOP_GUID);
        verify(mockCriteriaDao).deleteCriteria(defaultSubpop.getCriteria().getKey());
        verify(mockMapper).delete(defaultSubpop);
    }
    
    @Test
    public void updateSubpopulationUpdatesCriteriaFromSubpop() {
        // This subpopulation has the criteria fields, but no object
        Subpopulation subpop = createSubpopulation();
        subpop.setVersion(1L);
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpop);
        assertEquals(updatedSubpop.getCriteria(), CRITERIA);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria savedCriteria = criteriaCaptor.getValue();
        assertEquals(savedCriteria, CRITERIA);
    }
    
    @Test
    public void updateSubpopulationUpdatesCriteriaFromObject() {
        Subpopulation subpopWithCritObject = Subpopulation.create();
        subpopWithCritObject.setAppId(TEST_APP_ID);
        subpopWithCritObject.setGuidString(BridgeUtils.generateGuid());
        subpopWithCritObject.setVersion(1L);
        subpopWithCritObject.setCriteria(CRITERIA);
        
        reset(mockMapper);
        doReturn(subpopWithCritObject).when(mockMapper).load(any());
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpopWithCritObject);
        assertEquals(updatedSubpop.getCriteria(), CRITERIA);
        
        verify(mockCriteriaDao).getCriteria(subpopWithCritObject.getCriteria().getKey());
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria savedCriteria = criteriaCaptor.getValue();
        assertEquals(savedCriteria, CRITERIA);
    }
    
    @Test
    public void updateSubpopulationCanUndelete() {
        persistedSubpop.setDeleted(true);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setDeleted(false);
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setVersion(1L);
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpop);
        assertFalse(updatedSubpop.isDeleted());
        
        verify(mockMapper).save(subpopCaptor.capture());
        assertFalse(subpopCaptor.getValue().isDeleted());
    }
    
    @Test
    public void updateSubpopulationCanDelete() {
        persistedSubpop.setDeleted(false);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setDeleted(true);
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setVersion(1L);
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpop);
        assertTrue(updatedSubpop.isDeleted());
        
        verify(mockMapper).save(subpopCaptor.capture());
        assertTrue(subpopCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateSubpopulationCannotDeleteDefaultSubpopulation() {
        persistedSubpop.setDefaultGroup(true);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setDeleted(true);
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString(BridgeUtils.generateGuid());
        subpop.setVersion(1L);
        
        dao.updateSubpopulation(subpop);
    }
    
    @Test
    public void getSubpopulationsCreatesCriteria() {
        // The test subpopulation in the list that is returned from the mock mapper does not have
        // a criteria object. So it will be created as part of loading.
        List<Subpopulation> list = dao.getSubpopulations(TEST_APP_ID, true);
        assertEquals(list.get(0).getCriteria(), CRITERIA);
        
        verify(mockCriteriaDao).getCriteria(list.get(0).getCriteria().getKey());
    }
    
    @Test
    public void getSubpopulationsRetrievesCriteria() {
        // The test subpopulation in the list that is returned from the mock mapper does not have
        // a criteria object. So it will be created as part of loading.
        List<Subpopulation> list = dao.getSubpopulations(TEST_APP_ID, true);
        assertEquals(list.get(0).getCriteria(), CRITERIA);
        
        // In this case it actually returns a criteria object.
        verify(mockCriteriaDao).getCriteria(list.get(0).getCriteria().getKey());
    }
    
    @Test
    public void criteriaTableTakesPrecedenceOnGet() {
        reset(mockCriteriaDao);
        doReturn(CRITERIA).when(mockCriteriaDao).getCriteria(any());
        
        Subpopulation subpop = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        Criteria retrievedCriteria = subpop.getCriteria();
        assertEquals(retrievedCriteria, CRITERIA);
    }
    
    @Test
    public void criteriaTableTakesPrecedenceOnGetList() {
        reset(mockCriteriaDao);
        doReturn(CRITERIA).when(mockCriteriaDao).getCriteria(any());
        
        List<Subpopulation> subpops = dao.getSubpopulations(TEST_APP_ID, true);
        Criteria retrievedCriteria = subpops.get(0).getCriteria();
        assertEquals(retrievedCriteria, CRITERIA);
    }    
    
    @Test
    public void createSubpopulationTest() {
        Subpopulation subpop = Subpopulation.create();
        // We cannot set any of these values on create, they are all defaulted
        subpop.setGuidString("some nonsense");
        subpop.setDeleted(true); 
        subpop.setDefaultGroup(true);
        subpop.setVersion(1L);
        subpop.setPublishedConsentCreatedOn(100L);
        subpop.setAppId(TEST_APP_ID);
        
        dao.createSubpopulation(subpop);
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation savedSubpop = subpopCaptor.getValue();
        assertNotNull(savedSubpop.getGuidString());
        assertFalse(savedSubpop.isDeleted());
        assertNull(savedSubpop.getVersion());
        assertFalse(savedSubpop.isDefaultGroup());
        assertEquals(savedSubpop.getPublishedConsentCreatedOn(), 0L);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria savedCriteria = criteriaCaptor.getValue();
        assertEquals(savedCriteria.getKey(), CRITERIA_KEY);
    }
    
    @Test
    public void getSubpopulationsIncludeDeleted() {
        DynamoSubpopulation subpop1 = new DynamoSubpopulation();
        subpop1.setDeleted(false);
        
        DynamoSubpopulation subpop2 = new DynamoSubpopulation();
        subpop2.setDeleted(true);
        List<DynamoSubpopulation> subpopList = ImmutableList.of(subpop1, subpop2);
        
        when(mockMapper.query(eq(DynamoSubpopulation.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(subpopList.isEmpty());
        when(mockQueryList.stream()).thenReturn(subpopList.stream());
        
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, true);
        assertEquals(result.size(), 2);
        
        verify(mockMapper).query(eq(DynamoSubpopulation.class), queryCaptor.capture());
        assertEquals(queryCaptor.getValue().getHashKeyValues().getAppId(), TEST_APP_ID);        
    }

    @Test
    public void getSubpopulationsExcludeDeleted() {
        DynamoSubpopulation subpop1 = new DynamoSubpopulation();
        subpop1.setDeleted(false);
        
        DynamoSubpopulation subpop2 = new DynamoSubpopulation();
        subpop2.setDeleted(true);
        List<DynamoSubpopulation> subpopList = ImmutableList.of(subpop1, subpop2);
        
        when(mockMapper.query(eq(DynamoSubpopulation.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(subpopList.isEmpty());
        when(mockQueryList.stream()).thenReturn(subpopList.stream());
        
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, false);
        assertEquals(result.size(), 1);
        assertFalse(result.get(0).isDeleted());
    }

    @Test
    public void getSubpopulation() {
        Subpopulation saved = Subpopulation.create();
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation result = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        assertSame(result, saved);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getSubpopulationNotFound() {
        reset(mockMapper);
        dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
    }

    @Test
    public void updateSubpopulation() {
        Subpopulation saved = Subpopulation.create();
        saved.setDefaultGroup(true); // this cannot be changed
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        subpop.setVersion(2L);
        subpop.setDefaultGroup(false);
        
        Subpopulation result = dao.updateSubpopulation(subpop);
        assertTrue(result.isDefaultGroup());
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation updated = subpopCaptor.getValue();
        assertSame(updated, subpop);
        assertTrue(updated.isDefaultGroup());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateSubpopulationNoVersion() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        
        dao.updateSubpopulation(subpop);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateSubpopulationNoGuid() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setVersion(2L);
        
        dao.updateSubpopulation(subpop);
    }

    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateSubpopulationCannotUpdateLogicallyDeletedSubpop() {
        Subpopulation saved = Subpopulation.create();
        saved.setDeleted(true);
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        subpop.setVersion(2L);
        subpop.setDeleted(true);
        
        dao.updateSubpopulation(subpop);
    }
    
    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = CANNOT_DELETE_DEFAULT_SUBPOP_MSG)
    public void updateSubpopulationCannotDeleteDefaultSubpop() {
        Subpopulation saved = Subpopulation.create();
        saved.setDefaultGroup(true);
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        subpop.setVersion(2L);
        subpop.setDeleted(true);
        
        dao.updateSubpopulation(subpop);        
    }
    
    @Test
    public void deleteSubpopulation() {
        Subpopulation saved = Subpopulation.create();
        when(mockMapper.load(any())).thenReturn(saved);
        
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        verify(mockMapper).save(subpopCaptor.capture());
        assertTrue(subpopCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteSubpopulationNotFound() {
        reset(mockMapper);
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteSubpopulationAlreadyDeleted() {
        Subpopulation saved = Subpopulation.create();
        saved.setDeleted(true);
        when(mockMapper.load(any())).thenReturn(saved);
        
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void deleteSubpopulationCannotDeleteDefaultGroup() {
        Subpopulation saved = Subpopulation.create();
        saved.setDefaultGroup(true);
        when(mockMapper.load(any())).thenReturn(saved);
        
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
    }
    
    @Test
    public void deleteSubpopulationPermanently() {
        Subpopulation saved = Subpopulation.create();
        saved.setGuid(SUBPOP_GUID);
        when(mockMapper.load(any())).thenReturn(saved);
        
        dao.deleteSubpopulationPermanently(TEST_APP_ID, SUBPOP_GUID);
        
        verify(mockCriteriaDao).deleteCriteria("subpopulation:oneGuid");
        verify(mockMapper).delete(any());
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteSubpopulationPermanentlyNotFound() {
        reset(mockMapper);
        dao.deleteSubpopulationPermanently(TEST_APP_ID, SUBPOP_GUID);
    }
}
