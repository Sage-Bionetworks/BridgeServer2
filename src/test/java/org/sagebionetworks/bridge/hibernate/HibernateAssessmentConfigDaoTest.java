package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Optional;
import java.util.function.Function;

import org.hibernate.Session;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;

public class HibernateAssessmentConfigDaoTest extends Mockito {

    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    Session mockSession;
    
    @InjectMocks
    HibernateAssessmentConfigDao dao;
    
    @Captor
    ArgumentCaptor<HibernateAssessmentConfig> configCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(mockHelper.executeWithExceptionHandling(any(), any())).then(answer -> {
            Function<Session,HibernateAssessment> func = answer.getArgument(1);
            return func.apply(mockSession);
        });
    }

    @Test
    public void getAssessmentConfig() {
        HibernateAssessmentConfig hibConfig = new HibernateAssessmentConfig();
        hibConfig.setGuid(GUID);
        hibConfig.setCreatedOn(CREATED_ON);
        hibConfig.setModifiedOn(MODIFIED_ON);
        hibConfig.setConfig(TestUtils.getClientData());
        when(mockHelper.getById(HibernateAssessmentConfig.class, GUID)).thenReturn(hibConfig);
        
        Optional<AssessmentConfig> optional = dao.getAssessmentConfig(GUID);
        AssessmentConfig config = optional.get();
        assertEquals(config.getCreatedOn(), CREATED_ON);
        assertEquals(config.getModifiedOn(), MODIFIED_ON);
        assertEquals(config.getConfig(), TestUtils.getClientData());
    }
    
    @Test
    public void getAssessmentConfigMissing() {
        when(mockHelper.getById(HibernateAssessmentConfig.class, GUID)).thenReturn(null);
        
        Optional<AssessmentConfig> optional = dao.getAssessmentConfig(GUID);
        assertFalse(optional.isPresent());
    }
    
    @Test
    public void updateAssessmentConfig() {
        ArgumentCaptor<Object> argCaptor = ArgumentCaptor.forClass(Object.class);
        
        Assessment assessment = new Assessment();
        assessment.setGuid(GUID);
        AssessmentConfig config = new AssessmentConfig();
        config.setVersion(3L);
        
        dao.updateAssessmentConfig(TEST_APP_ID, assessment, GUID, config);
        
        verify(mockSession, times(2)).merge(argCaptor.capture());
        
        HibernateAssessment hibAssessment = (HibernateAssessment)argCaptor.getAllValues().get(0);
        assertEquals(hibAssessment.getGuid(), GUID);
        HibernateAssessmentConfig hibConfig = (HibernateAssessmentConfig)argCaptor.getAllValues().get(1);
        assertEquals(hibConfig.getVersion(), 3L);
    }

    @Test
    public void customizeAssessmentConfig() {
        AssessmentConfig config = new AssessmentConfig();
        config.setVersion(3L);
        
        dao.customizeAssessmentConfig(GUID, config);
        
        verify(mockSession).merge(configCaptor.capture());
        
        HibernateAssessmentConfig hibConfig = configCaptor.getAllValues().get(0);
        assertEquals(hibConfig.getVersion(), 3L);
    }
    
    @Test
    public void deleteAssessmentConfig() {
        AssessmentConfig config = new AssessmentConfig();
        config.setVersion(3L);
        
        dao.deleteAssessmentConfig(GUID, config);
        
        verify(mockSession).remove(configCaptor.capture());
        
        HibernateAssessmentConfig hibConfig = configCaptor.getAllValues().get(0);
        assertEquals(hibConfig.getVersion(), 3L);
    }
}
