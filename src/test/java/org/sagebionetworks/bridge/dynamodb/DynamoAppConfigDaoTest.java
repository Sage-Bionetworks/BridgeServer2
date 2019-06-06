package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
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
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;

public class DynamoAppConfigDaoTest extends Mockito {

    private static final String GUID = "oneGuid";
    
    private static final DynamoAppConfig KEY = new DynamoAppConfig();
    static {
        KEY.setStudyId(TEST_STUDY_IDENTIFIER);
        KEY.setGuid(GUID);
    }
    
    private static final String CRITERIA_KEY = "appconfig:" + GUID;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    CriteriaDao mockCriteriaDao;
    
    @Mock
    PaginatedQueryList<DynamoAppConfig> mockResults;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoAppConfig>> queryCaptor;
    
    @Captor
    ArgumentCaptor<Criteria> criteriaCaptor;
    
    @Captor
    ArgumentCaptor<AppConfig> configCaptor;
    
    @InjectMocks
    DynamoAppConfigDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void getAppConfigsIncludeDeleted() {
        List<DynamoAppConfig> configs = ImmutableList.of(new DynamoAppConfig(), new DynamoAppConfig());
        when(mockResults.size()).thenReturn(configs.size());
        when(mockResults.iterator()).thenReturn(configs.iterator());
        when(mockMapper.query(eq(DynamoAppConfig.class), any())).thenReturn(mockResults);
        
        List<AppConfig> results = dao.getAppConfigs(TEST_STUDY, true);
        assertEquals(results.size(), 2);
        
        verify(mockMapper).query(eq(DynamoAppConfig.class), queryCaptor.capture());
        
        DynamoDBQueryExpression<DynamoAppConfig> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getStudyId(), TEST_STUDY_IDENTIFIER);
        // Regardless of the state of the deleted flag
        assertNull(query.getQueryFilter());
    }
    
    @Test
    public void getAppConfigsExcludeDeleted() {
        List<DynamoAppConfig> configs = ImmutableList.of(new DynamoAppConfig(), new DynamoAppConfig());
        when(mockResults.size()).thenReturn(configs.size());
        when(mockResults.iterator()).thenReturn(configs.iterator());
        when(mockMapper.query(eq(DynamoAppConfig.class), any())).thenReturn(mockResults);
        
        List<AppConfig> results = dao.getAppConfigs(TEST_STUDY, false);
        assertEquals(results.size(), 2);
        
        verify(mockMapper).query(eq(DynamoAppConfig.class), queryCaptor.capture());
        
        DynamoDBQueryExpression<DynamoAppConfig> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getStudyId(), TEST_STUDY_IDENTIFIER);
        
        // where the deleted flag is not set to 1 (deleted)
        Condition condition = query.getQueryFilter().get("deleted");
        assertEquals(condition.getComparisonOperator(), NE.name());
        assertEquals(condition.getAttributeValueList().get(0).getN(), "1");
    }
    
    @Test
    public void getAppConfig() {
        DynamoAppConfig config = new DynamoAppConfig();
        config.setGuid(GUID);
        when(mockMapper.load(KEY)).thenReturn(config);
        
        Criteria criteria = new DynamoCriteria();
        when(mockCriteriaDao.getCriteria(CRITERIA_KEY)).thenReturn(criteria);
        
        AppConfig result = dao.getAppConfig(TEST_STUDY, GUID);
        
        assertSame(result, config);
        assertSame(result.getCriteria(), criteria);
    }
    
    @Test
    public void getAppConfigWithNoCriteria() {
        DynamoAppConfig config = new DynamoAppConfig();
        config.setGuid(GUID);
        when(mockMapper.load(KEY)).thenReturn(config);
        
        AppConfig result = dao.getAppConfig(TEST_STUDY, GUID);
        assertNotNull(result.getCriteria());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getAppConfigNotFound() {
        dao.getAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void createAppConfig() {
        AppConfig config = AppConfig.create();
        config.setDeleted(true); // verify this cannot be created true
        config.setGuid(GUID); // set in AppConfigService
        Criteria criteria = Criteria.create();
        config.setCriteria(criteria);
        
        AppConfig result = dao.createAppConfig(config);
        assertSame(result, config);
        assertFalse(result.isDeleted());
        assertEquals(criteria.getKey(), CRITERIA_KEY);
        
        verify(mockMapper).save(config);
        verify(mockCriteriaDao).createOrUpdateCriteria(criteria);
    }
    
    @Test
    public void updateAppConfig() {
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setLanguage("fr");
        
        AppConfig config = AppConfig.create();
        config.setStudyId(TEST_STUDY_IDENTIFIER);
        config.setGuid(GUID);
        config.setCriteria(criteria);
        
        DynamoAppConfig saved = new DynamoAppConfig();
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        // whether there is a criteria object or not is not relevant, it will
        // always be replaced by the submitted criteria. The code currently loads
        // it but it doesn't need to.
        
        AppConfig result = dao.updateAppConfig(config);
        assertSame(result, config);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        verify(mockMapper).save(config);
        
        assertFalse(result.isDeleted());
        assertEquals(criteriaCaptor.getValue().getKey(), CRITERIA_KEY);
        assertEquals(criteriaCaptor.getValue().getLanguage(), "fr"); // it is the object we submitted
    }

    @Test
    public void updateAppConfigDeletes() {
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setLanguage("fr");
        
        AppConfig config = AppConfig.create();
        config.setStudyId(TEST_STUDY_IDENTIFIER);
        config.setGuid(GUID);
        config.setDeleted(true); // can be deleted as part of an update
        config.setCriteria(criteria);
        
        DynamoAppConfig saved = new DynamoAppConfig();
        saved.setDeleted(false);
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        AppConfig result = dao.updateAppConfig(config);
        assertSame(result, config);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        verify(mockMapper).save(config);
        
        assertTrue(result.isDeleted());
        assertEquals(criteriaCaptor.getValue().getKey(), CRITERIA_KEY);
        assertEquals(criteriaCaptor.getValue().getLanguage(), "fr"); // it is the object we submitted
    }
    
    @Test
    public void updateAppConfigUndeletes() {
        DynamoCriteria criteria = new DynamoCriteria();
        criteria.setLanguage("fr");
        
        AppConfig config = AppConfig.create();
        config.setStudyId(TEST_STUDY_IDENTIFIER);
        config.setGuid(GUID);
        config.setDeleted(false); // can be undeleted as part of an update
        config.setCriteria(criteria);
        
        DynamoAppConfig saved = new DynamoAppConfig();
        saved.setDeleted(true);
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        AppConfig result = dao.updateAppConfig(config);
        assertSame(result, config);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        verify(mockMapper).save(config);
        
        assertFalse(result.isDeleted());
        assertEquals(criteriaCaptor.getValue().getKey(), CRITERIA_KEY);
        assertEquals(criteriaCaptor.getValue().getLanguage(), "fr"); // it is the object we submitted
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAppConfigLogicallyDeleted() {
        AppConfig config = AppConfig.create();
        config.setStudyId(TEST_STUDY_IDENTIFIER);
        config.setGuid(GUID);
        config.setDeleted(true);
        config.setCriteria(Criteria.create());
        
        DynamoAppConfig saved = new DynamoAppConfig();
        saved.setDeleted(true);
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        dao.updateAppConfig(config);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAppConfigNotFound() {
        AppConfig config = AppConfig.create();
        config.setStudyId(TEST_STUDY_IDENTIFIER);
        config.setGuid(GUID);
        
        dao.updateAppConfig(config);
    }
    
    @Test
    public void deleteAppConfig() {
        DynamoAppConfig saved = new DynamoAppConfig();
        saved.setStudyId(TEST_STUDY_IDENTIFIER);
        saved.setGuid(GUID);
        saved.setCriteria(Criteria.create());
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        dao.deleteAppConfig(TEST_STUDY, GUID);
        
        verify(mockMapper).save(configCaptor.capture());
        assertTrue(configCaptor.getValue().isDeleted());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteAppConfigNotFound() {
        dao.deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteAppConfigAlreadyDeleted() {
        DynamoAppConfig saved = new DynamoAppConfig();
        saved.setDeleted(true);
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        dao.deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteAppConfigPermanently() {
        DynamoAppConfig saved = new DynamoAppConfig();
        saved.setGuid(GUID);
        when(mockMapper.load(KEY)).thenReturn(saved);
        
        dao.deleteAppConfigPermanently(TEST_STUDY, GUID);
        
        verify(mockMapper).delete(saved);
        verify(mockCriteriaDao).deleteCriteria(CRITERIA_KEY);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteAppConfigPermanentlyNotFound() {
        dao.deleteAppConfigPermanently(TEST_STUDY, GUID);
    }
}
