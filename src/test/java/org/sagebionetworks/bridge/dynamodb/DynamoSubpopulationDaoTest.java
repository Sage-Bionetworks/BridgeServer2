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

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

public class DynamoSubpopulationDaoTest extends Mockito {

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
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
        when(dao.generateGuid()).thenReturn(GUID);
    }
    
    @Test
    public void createSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        // We cannot set any of these values on create, they are all defaulted
        subpop.setGuidString("some nonsense");
        subpop.setDeleted(true); 
        subpop.setDefaultGroup(true);
        subpop.setVersion(1L);
        subpop.setPublishedConsentCreatedOn(100L);
        subpop.setStudyIdentifier(TEST_APP_ID);
        
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
    public void createDefaultSubpopulation() {
        when(mockCriteriaDao.createOrUpdateCriteria(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        Subpopulation result = dao.createDefaultSubpopulation(TEST_APP_ID);
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation subpop = subpopCaptor.getValue();
        assertEquals(subpop.getStudyIdentifier(), TEST_APP_ID);
        assertEquals(subpop.getGuidString(), TEST_APP_ID);
        assertEquals(subpop.getName(), "Default Consent Group");
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
    public void getSubpopulationsIncludeDeleted() {
        DynamoSubpopulation subpop1 = new DynamoSubpopulation();
        subpop1.setDeleted(false);
        
        DynamoSubpopulation subpop2 = new DynamoSubpopulation();
        subpop2.setDeleted(true);
        List<DynamoSubpopulation> subpopList = ImmutableList.of(subpop1, subpop2);
        
        when(mockMapper.query(eq(DynamoSubpopulation.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(subpopList.isEmpty());
        when(mockQueryList.stream()).thenReturn(subpopList.stream());
        
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, false, true);
        assertEquals(result.size(), 2);
        
        verify(mockMapper).query(eq(DynamoSubpopulation.class), queryCaptor.capture());
        assertEquals(queryCaptor.getValue().getHashKeyValues().getStudyIdentifier(), TEST_APP_ID);        
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
        
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, false, false);
        assertEquals(result.size(), 1);
        assertFalse(result.get(0).isDeleted());
    }

    @Test
    public void getSubpopulationsDoesNotCreateDefaultIfThereAreExistingSubpops() {
        DynamoSubpopulation subpop1 = new DynamoSubpopulation();
        List<DynamoSubpopulation> subpopList = ImmutableList.of(subpop1);
        when(mockMapper.query(eq(DynamoSubpopulation.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(subpopList.isEmpty());
        when(mockQueryList.stream()).thenReturn(subpopList.stream());
        
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, true, true);
        assertEquals(result.size(), 1);
        assertFalse(result.get(0).isDefaultGroup());
    }
    
    @Test
    public void getSubpopulationsDoesNotCreateDefaultIfFlagIsFalse() {
        // No subopulation
        List<DynamoSubpopulation> subpopList = ImmutableList.of();
        when(mockMapper.query(eq(DynamoSubpopulation.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(subpopList.isEmpty());
        when(mockQueryList.stream()).thenReturn(subpopList.stream());
        
        // but don't create a default
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, false, true);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void getSubpopulationsCreatesDefault() {
        List<DynamoSubpopulation> subpopList = ImmutableList.of();
        when(mockMapper.query(eq(DynamoSubpopulation.class), any())).thenReturn(mockQueryList);
        when(mockQueryList.isEmpty()).thenReturn(subpopList.isEmpty());
        when(mockQueryList.stream()).thenReturn(subpopList.stream());
        
        List<Subpopulation> result = dao.getSubpopulations(TEST_APP_ID, true, true);
        assertEquals(result.size(), 1);
        assertTrue(result.get(0).isDefaultGroup());
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
        dao.getSubpopulation(TEST_APP_ID, SUBPOP_GUID);
    }

    @Test
    public void updateSubpopulation() {
        Subpopulation saved = Subpopulation.create();
        saved.setDefaultGroup(true); // this cannot be changed
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier(TEST_APP_ID);
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
        subpop.setStudyIdentifier(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        
        dao.updateSubpopulation(subpop);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateSubpopulationNoGuid() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier(TEST_APP_ID);
        subpop.setVersion(2L);
        
        dao.updateSubpopulation(subpop);
    }
    
    @Test
    public void updateSubpopulationCanUndelete() {
        Subpopulation saved = Subpopulation.create();
        saved.setDeleted(true);
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        subpop.setVersion(2L);
        // not deleted
        
        dao.updateSubpopulation(subpop);
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation updated = subpopCaptor.getValue();
        assertFalse(updated.isDeleted());        
    }

    @Test
    public void updateSubpopulationCanDelete() {
        Subpopulation saved = Subpopulation.create();
        // not deleted
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier(TEST_APP_ID);
        subpop.setGuid(SUBPOP_GUID);
        subpop.setVersion(2L);
        subpop.setDeleted(true);
        
        dao.updateSubpopulation(subpop);
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation updated = subpopCaptor.getValue();
        assertTrue(updated.isDeleted());        
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateSubpopulationCannotUpdateLogicallyDeletedSubpop() {
        Subpopulation saved = Subpopulation.create();
        saved.setDeleted(true);
        when(mockMapper.load(any())).thenReturn(saved);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier(TEST_APP_ID);
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
        subpop.setStudyIdentifier(TEST_APP_ID);
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
        dao.deleteSubpopulationPermanently(TEST_APP_ID, SUBPOP_GUID);
    }
}
