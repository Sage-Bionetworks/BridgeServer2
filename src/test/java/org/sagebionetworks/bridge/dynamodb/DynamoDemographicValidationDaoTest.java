package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.DemographicValuesValidationConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;

public class DynamoDemographicValidationDaoTest {
    private static final String CATEGORY_NAME = "category1";

    @Mock
    DynamoDBMapper mockMapper;

    @Captor
    ArgumentCaptor<DemographicValuesValidationConfig> configCaptor;

    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoDemographicValuesValidationConfig>> queryCaptor;

    @InjectMocks
    DynamoDemographicValidationDao dao;

    DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void save() {
        DemographicValuesValidationConfig savedConfig = dao.saveDemographicValuesValidationConfig(config);
        verify(mockMapper).save(config);
        assertSame(savedConfig, config);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void save_nullConfig() {
        dao.saveDemographicValuesValidationConfig(null);
    }

    @Test
    public void get_appLevel() {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        when(mockMapper.load(any())).thenReturn(config);

        Optional<DemographicValuesValidationConfig> fetchedConfig = dao
                .getDemographicValuesValidationConfig(TEST_APP_ID, null, CATEGORY_NAME);

        verify(mockMapper).load(configCaptor.capture());
        assertEquals(configCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(configCaptor.getValue().getStudyId(), null);
        assertEquals(configCaptor.getValue().getCategoryName(), CATEGORY_NAME);
        assertTrue(fetchedConfig.isPresent());
        assertSame(fetchedConfig.get(), config);
    }

    @Test
    public void get_studyLevel() {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        when(mockMapper.load(any())).thenReturn(config);

        Optional<DemographicValuesValidationConfig> fetchedConfig = dao
                .getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, CATEGORY_NAME);

        verify(mockMapper).load(configCaptor.capture());
        assertEquals(configCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(configCaptor.getValue().getStudyId(), TEST_STUDY_ID);
        assertEquals(configCaptor.getValue().getCategoryName(), CATEGORY_NAME);
        assertTrue(fetchedConfig.isPresent());
        assertSame(fetchedConfig.get(), config);
    }

    @Test
    public void get_nonexistent() {
        when(mockMapper.load(any())).thenReturn(null);

        Optional<DemographicValuesValidationConfig> fetchedConfig = dao.getDemographicValuesValidationConfig(TEST_APP_ID, null, CATEGORY_NAME);

        verify(mockMapper).load(configCaptor.capture());
        assertEquals(configCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(configCaptor.getValue().getStudyId(), null);
        assertEquals(configCaptor.getValue().getCategoryName(), CATEGORY_NAME);
        assertFalse(fetchedConfig.isPresent());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void get_nullAppId() {
        dao.getDemographicValuesValidationConfig(null, TEST_STUDY_ID, CATEGORY_NAME);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void get_nullCategory() {
        dao.getDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, null);
    }

    @Test
    public void delete_appLevel() {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        when(mockMapper.load(any())).thenReturn(config);

        dao.deleteDemographicValuesValidationConfig(TEST_APP_ID, null, CATEGORY_NAME);

        verify(mockMapper).load(configCaptor.capture());
        assertEquals(configCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(configCaptor.getValue().getStudyId(), null);
        assertEquals(configCaptor.getValue().getCategoryName(), CATEGORY_NAME);
        verify(mockMapper).delete(config);
    }

    @Test
    public void delete_studyLevel() {
        DemographicValuesValidationConfig config = DemographicValuesValidationConfig.create();
        when(mockMapper.load(any())).thenReturn(config);

        dao.deleteDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, CATEGORY_NAME);

        verify(mockMapper).load(configCaptor.capture());
        assertEquals(configCaptor.getValue().getAppId(), TEST_APP_ID);
        assertEquals(configCaptor.getValue().getStudyId(), TEST_STUDY_ID);
        assertEquals(configCaptor.getValue().getCategoryName(), CATEGORY_NAME);
        verify(mockMapper).delete(config);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void delete_nonexistent() {
        when(mockMapper.load(any())).thenReturn(null);

        dao.deleteDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, CATEGORY_NAME);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void delete_nullAppId() {
        dao.deleteDemographicValuesValidationConfig(null, TEST_STUDY_ID, CATEGORY_NAME);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void delete_nullCategory() {
        dao.deleteDemographicValuesValidationConfig(TEST_APP_ID, TEST_STUDY_ID, null);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deleteAll_appLevel() {
        PaginatedQueryList<Object> mockQueryResult = (PaginatedQueryList<Object>) mock(PaginatedQueryList.class);
        when(mockMapper.query(any(), any())).thenReturn(mockQueryResult);

        dao.deleteAllValidationConfigs(TEST_APP_ID, null);

        verify(mockMapper).query(eq(DynamoDemographicValuesValidationConfig.class), queryCaptor.capture());
        DemographicValuesValidationConfig hashKey = queryCaptor.getValue().getHashKeyValues();
        assertEquals(hashKey.getAppId(), TEST_APP_ID);
        assertEquals(hashKey.getStudyId(), null);
        verify(mockMapper).batchDelete(mockQueryResult);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deleteAll_studyLevel() {
        PaginatedQueryList<Object> mockQueryResult = (PaginatedQueryList<Object>) mock(PaginatedQueryList.class);
        when(mockMapper.query(any(), any())).thenReturn(mockQueryResult);

        dao.deleteAllValidationConfigs(TEST_APP_ID, TEST_STUDY_ID);

        verify(mockMapper).query(eq(DynamoDemographicValuesValidationConfig.class), queryCaptor.capture());
        DemographicValuesValidationConfig hashKey = queryCaptor.getValue().getHashKeyValues();
        assertEquals(hashKey.getAppId(), TEST_APP_ID);
        assertEquals(hashKey.getStudyId(), TEST_STUDY_ID);
        verify(mockMapper).batchDelete(mockQueryResult);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void deleteAll_nullAppId() {
        dao.deleteAllValidationConfigs(null, TEST_STUDY_ID);
    }
}
