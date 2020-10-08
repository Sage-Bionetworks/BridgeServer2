package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSubpopulationDaoMockTest {
    
    private static final Criteria CRITERIA = TestUtils.createCriteria(2, 10, 
            Sets.newHashSet("a", "b"), Sets.newHashSet("c", "d"));
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("AAA");

    @Spy
    private DynamoSubpopulationDao dao;
    
    @Mock
    private DynamoDBMapper mapper;
    
    @Mock
    private CriteriaDao criteriaDao;
    
    @Captor
    private ArgumentCaptor<Subpopulation> subpopCaptor;
    
    @Captor
    private ArgumentCaptor<Criteria> criteriaCaptor;
    
    private DynamoSubpopulation persistedSubpop;

    @SuppressWarnings("unchecked")
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao.setMapper(mapper);
        dao.setCriteriaDao(criteriaDao);
        
        List<DynamoSubpopulation> list = Lists.newArrayList((DynamoSubpopulation)createSubpopulation());

        PaginatedQueryList<DynamoSubpopulation> page = mock(PaginatedQueryList.class);
        when(page.stream()).thenReturn(list.stream());

        persistedSubpop = (DynamoSubpopulation)createSubpopulation();
        doReturn(persistedSubpop).when(mapper).load(any());
        doReturn(page).when(mapper).query(eq(DynamoSubpopulation.class), any());
        
        when(criteriaDao.getCriteria(any())).thenReturn(CRITERIA);
        when(criteriaDao.createOrUpdateCriteria(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    
    @Test
    public void createSubpopulationWritesCriteria() {
        Subpopulation subpop = createSubpopulation();
        
        dao.createSubpopulation(subpop);
        
        Criteria criteria = subpop.getCriteria();
        verify(criteriaDao).createOrUpdateCriteria(criteria);
    }
    
    @Test
    public void createDefaultSubpopulation() {
        Subpopulation subpop = dao.createDefaultSubpopulation(TEST_APP_ID, TEST_STUDY_ID);
        assertEquals(subpop.getStudyIdsAssignedOnConsent(), ImmutableSet.of(TEST_STUDY_ID));
        assertEquals(subpop.getAppId(), TEST_APP_ID);
        assertEquals(subpop.getGuidString(), TEST_APP_ID);
        assertEquals(subpop.getName(), "Default Consent Group");
        assertTrue(subpop.isDefaultGroup());
        assertTrue(subpop.isRequired());
        
        Criteria criteria = subpop.getCriteria();
        assertEquals(criteria.getMinAppVersion(OperatingSystem.IOS), new Integer(0));
        assertEquals(criteria.getMinAppVersion(OperatingSystem.ANDROID), new Integer(0));
        
        verify(mapper).save(subpop);
        verify(criteriaDao).createOrUpdateCriteria(criteria);
    }
    
    @Test
    public void getSubpopulationRetrievesCriteria() {
        Subpopulation subpop = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        Criteria criteria = subpop.getCriteria();
        assertEquals(criteria, CRITERIA);
        
        verify(criteriaDao).getCriteria(criteria.getKey());
        verifyNoMoreInteractions(criteriaDao);
    }
    
    @Test
    public void getSubpopulationConstructsCriteriaIfNotSaved() {
        when(criteriaDao.getCriteria(any())).thenReturn(null);
        
        Subpopulation subpop = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        Criteria criteria = subpop.getCriteria();
        assertNotNull(criteria);
        
        verify(criteriaDao).getCriteria(criteria.getKey());
    }
    
    @Test
    public void physicalDeleteSubpopulationDeletesCriteria() {
        dao.deleteSubpopulationPermanently(TEST_APP_ID, SUBPOP_GUID);
        
        verify(criteriaDao).deleteCriteria(createSubpopulation().getCriteria().getKey());
    }
    
    @Test
    public void logicalDeleteSubpopulationDoesNotDeleteCriteria() {
        dao.deleteSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        
        verify(criteriaDao, never()).deleteCriteria(createSubpopulation().getCriteria().getKey());
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
        verify(criteriaDao).deleteCriteria(defaultSubpop.getCriteria().getKey());
        verify(mapper).delete(defaultSubpop);
    }
    
    @Test
    public void updateSubpopulationUpdatesCriteriaFromSubpop() {
        // This subpopulation has the criteria fields, but no object
        Subpopulation subpop = createSubpopulation();
        subpop.setVersion(1L);
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpop);
        assertEquals(updatedSubpop.getCriteria(), CRITERIA);
        
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
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
        
        reset(mapper);
        doReturn(subpopWithCritObject).when(mapper).load(any());
        
        Subpopulation updatedSubpop = dao.updateSubpopulation(subpopWithCritObject);
        assertEquals(updatedSubpop.getCriteria(), CRITERIA);
        
        verify(criteriaDao).getCriteria(subpopWithCritObject.getCriteria().getKey());
        verify(criteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
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
        
        verify(mapper).save(subpopCaptor.capture());
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
        
        verify(mapper).save(subpopCaptor.capture());
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
        
        verify(criteriaDao).getCriteria(list.get(0).getCriteria().getKey());
    }
    
    @Test
    public void getSubpopulationsRetrievesCriteria() {
        // The test subpopulation in the list that is returned from the mock mapper does not have
        // a criteria object. So it will be created as part of loading.
        List<Subpopulation> list = dao.getSubpopulations(TEST_APP_ID, true);
        assertEquals(list.get(0).getCriteria(), CRITERIA);
        
        // In this case it actually returns a criteria object.
        verify(criteriaDao).getCriteria(list.get(0).getCriteria().getKey());
    }
    
    @Test
    public void criteriaTableTakesPrecedenceOnGet() {
        reset(criteriaDao);
        doReturn(CRITERIA).when(criteriaDao).getCriteria(any());
        
        Subpopulation subpop = dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
        Criteria retrievedCriteria = subpop.getCriteria();
        assertEquals(retrievedCriteria, CRITERIA);
    }
    
    @Test
    public void criteriaTableTakesPrecedenceOnGetList() {
        reset(criteriaDao);
        doReturn(CRITERIA).when(criteriaDao).getCriteria(any());
        
        List<Subpopulation> subpops = dao.getSubpopulations(TEST_APP_ID, true);
        Criteria retrievedCriteria = subpops.get(0).getCriteria();
        assertEquals(retrievedCriteria, CRITERIA);
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
}
