package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateSponsorDao.ADD_SPONSOR_SQL;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.persistence.PersistenceException;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyId;

import com.google.common.collect.ImmutableList;

public class HibernateStudyDaoTest extends Mockito {
    private static final List<HibernateStudy> STUDIES = ImmutableList.of(new HibernateStudy(),
            new HibernateStudy());
    
    @Mock
    private HibernateHelper hibernateHelper;
    
    private HibernateStudyDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;

    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<StudyId> studyIdCaptor;
    
    @Captor
    ArgumentCaptor<HibernateStudy> studyCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao = new HibernateStudyDao();
        dao.setHibernateHelper(hibernateHelper);
    }
    
    @Test
    public void getStudiesIncludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateStudy.class)))
                .thenReturn(STUDIES);
        
        List<Study> list = dao.getStudies(TEST_APP_ID, true);
        assertEquals(list.size(), 2);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(null), eq(null), eq(HibernateStudy.class));
        
        assertEquals(queryCaptor.getValue(), "from HibernateStudy as study where appId=:appId");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getStudiesExcludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(null), eq(null), eq(HibernateStudy.class)))
            .thenReturn(STUDIES);

        List<Study> list = dao.getStudies(TEST_APP_ID, false);
        assertEquals(list.size(), 2);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(null), eq(null), eq(HibernateStudy.class));
        
        assertEquals(queryCaptor.getValue(),
                "from HibernateStudy as study where appId=:appId and deleted != 1");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("appId"), TEST_APP_ID);
    }
    
    @Test
    public void getStudy() {
        HibernateStudy study = new HibernateStudy();
        when(hibernateHelper.getById(eq(HibernateStudy.class), any())).thenReturn(study);
        
        Study returnedValue = dao.getStudy(TEST_APP_ID, "id");
        assertEquals(returnedValue, study);
        
        verify(hibernateHelper).getById(eq(HibernateStudy.class), studyIdCaptor.capture());
        
        StudyId studyId = studyIdCaptor.getValue();
        assertEquals(studyId.getId(), "id");
        assertEquals(studyId.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void createStudy() {
        HibernateStudy study = new HibernateStudy();
        study.setId(TEST_STUDY_ID);
        study.setAppId(TEST_APP_ID);
        study.setVersion(2L);

        doAnswer((invocation) -> {
            Consumer<HibernateStudy> cons = invocation.getArgument(1);
            cons.accept(study);
            return study;
        }).when(hibernateHelper).create(any(), any());
        
        VersionHolder holder = dao.createStudy(study, TEST_ORG_ID);
        assertEquals(holder.getVersion(), new Long(2));
        
        verify(hibernateHelper).create(studyCaptor.capture(), any());
        
        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getVersion(), new Long(2));
        
        verify(hibernateHelper).nativeQueryUpdate(queryCaptor.capture(), paramsCaptor.capture());
        assertEquals(queryCaptor.getValue(), ADD_SPONSOR_SQL);
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("studyId"), TEST_STUDY_ID);
        assertEquals(paramsCaptor.getValue().get("orgId"), TEST_ORG_ID);
    }

    @Test
    public void updateStudy() {
        Study study = Study.create();
        study.setVersion(2L);
        
        VersionHolder holder = dao.updateStudy(study);
        assertEquals(holder.getVersion(), new Long(2));
        
        verify(hibernateHelper).update(studyCaptor.capture(), eq(null));
        
        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getVersion(), new Long(2));
    }

    @Test
    public void deleteStudyPermanently() {
        dao.deleteStudyPermanently(TEST_APP_ID, "oneId");
        
        verify(hibernateHelper).deleteById(eq(HibernateStudy.class), studyIdCaptor.capture());
        StudyId studyId = studyIdCaptor.getValue();
        assertEquals(studyId.getId(), "oneId");
        assertEquals(studyId.getAppId(), TEST_APP_ID);
    }    

    @Test(expectedExceptions = PersistenceException.class)
    public void deleteStudyPermanentlyNotFound() {
        doThrow(new PersistenceException()).when(hibernateHelper).deleteById(eq(HibernateStudy.class), any());
            
        dao.deleteStudyPermanently(TEST_APP_ID, "oneId");
    }    
}
