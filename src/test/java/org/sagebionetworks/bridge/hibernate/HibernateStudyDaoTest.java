package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyDao.FROM_PHRASE;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyDao.SELECT_PHRASE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class HibernateStudyDaoTest extends Mockito {
    private static final PagedResourceList<HibernateStudy> STUDIES = new PagedResourceList<>(
            ImmutableList.of(new HibernateStudy(), new HibernateStudy()), 2);
    
    @Mock
    private HibernateHelper hibernateHelper;
    
    @Mock
    private SessionFactory mockSessionFactory;
    
    @Mock
    private Session mockSession;
    
    @Mock
    private NativeQuery<?> mockNativeQuery;
    
    @InjectMocks
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
    }
    
    @Test
    public void removeScheduleFromStudies() {
        dao.removeScheduleFromStudies(TEST_APP_ID, SCHEDULE_GUID);
        
        verify(hibernateHelper).nativeQueryUpdate(queryCaptor.capture(), paramsCaptor.capture());
        
        assertEquals(queryCaptor.getValue(), "UPDATE Substudies SET scheduleGuid = NULL "
                + "WHERE studyId = :appId AND scheduleGuid = :scheduleGuid AND phase IN ('LEGACY','DESIGN')");

        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("scheduleGuid"), SCHEDULE_GUID);
    }
    
    @Test
    public void getStudiesIncludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(5), eq(10), eq(HibernateStudy.class)))
                .thenReturn(STUDIES.getItems());
        when(hibernateHelper.queryCount(any(), any())).thenReturn(10);
        
        PagedResourceList<Study> list = dao.getStudies(TEST_APP_ID, null, 5, 10, true);
        assertEquals(list.getItems(), STUDIES.getItems());
        assertEquals(list.getTotal(), (Integer)10);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(5), eq(10), eq(HibernateStudy.class));
        
        assertEquals(queryCaptor.getValue(), SELECT_PHRASE + FROM_PHRASE);
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getStudiesExcludeDeleted() {
        when(hibernateHelper.queryGet(any(), any(), eq(0), eq(100), eq(HibernateStudy.class)))
            .thenReturn(STUDIES.getItems());
        when(hibernateHelper.queryCount(any(), any())).thenReturn(10);
        
        PagedResourceList<Study> list = dao.getStudies(TEST_APP_ID, null, 0, 100, false);
        assertEquals(list.getItems(), STUDIES.getItems());
        assertEquals(list.getTotal(), (Integer)10);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(0), eq(100), eq(HibernateStudy.class));
        
        assertEquals(queryCaptor.getValue(), SELECT_PHRASE + FROM_PHRASE + " and deleted != 1");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("appId"), TEST_APP_ID);
    }
    
    @Test
    public void getStudiesWithStudyScoping() {
        when(hibernateHelper.queryGet(any(), any(), eq(5), eq(10), eq(HibernateStudy.class)))
            .thenReturn(STUDIES.getItems());
        when(hibernateHelper.queryCount(any(), any())).thenReturn(10);
        
        PagedResourceList<Study> list = dao.getStudies(TEST_APP_ID, ImmutableSet.of("studyA"), 5, 10, true);
        assertEquals(list.getItems(), STUDIES.getItems());
        assertEquals(list.getTotal(), (Integer)10);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(5), eq(10), eq(HibernateStudy.class));
        
        assertEquals(queryCaptor.getValue(), SELECT_PHRASE + FROM_PHRASE + " and identifier in (:studies)");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("appId"), TEST_APP_ID);
        assertEquals(parameters.get("studies"), ImmutableSet.of("studyA"));
    }
    
    @Test
    public void getStudiesWithEmptyStudyScoping() {
        when(hibernateHelper.queryGet(any(), any(), eq(5), eq(10), eq(HibernateStudy.class)))
            .thenReturn(STUDIES.getItems());
        when(hibernateHelper.queryCount(any(), any())).thenReturn(10);
        
        PagedResourceList<Study> list = dao.getStudies(TEST_APP_ID, ImmutableSet.of(), 5, 10, true);
        assertEquals(list.getItems(), STUDIES.getItems());
        assertEquals(list.getTotal(), (Integer)10);
        
        verify(hibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(5), eq(10), eq(HibernateStudy.class));
        
        assertEquals(queryCaptor.getValue(), SELECT_PHRASE + FROM_PHRASE + " and identifier in (:studies)");
        Map<String,Object> parameters = paramsCaptor.getValue();
        assertEquals(parameters.get("appId"), TEST_APP_ID);
        assertEquals(parameters.get("studies"), ImmutableSet.of());
    }

    @Test
    public void getStudy() {
        HibernateStudy study = new HibernateStudy();
        when(hibernateHelper.getById(eq(HibernateStudy.class), any())).thenReturn(study);
        
        Study returnedValue = dao.getStudy(TEST_APP_ID, "id");
        assertEquals(returnedValue, study);
        
        verify(hibernateHelper).getById(eq(HibernateStudy.class), studyIdCaptor.capture());
        
        StudyId studyId = studyIdCaptor.getValue();
        assertEquals(studyId.getIdentifier(), "id");
        assertEquals(studyId.getAppId(), TEST_APP_ID);
    }
    
    @Test
    public void createStudy() {
        HibernateStudy study = new HibernateStudy();
        study.setIdentifier(TEST_STUDY_ID);
        study.setAppId(TEST_APP_ID);
        study.setVersion(2L);

        VersionHolder holder = dao.createStudy(study);
        assertEquals(holder.getVersion(), new Long(2));
        
        verify(hibernateHelper).create(studyCaptor.capture());
        
        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getVersion(), new Long(2));
    }

    @Test
    public void updateStudy() {
        Study study = Study.create();
        study.setVersion(2L);
        
        VersionHolder holder = dao.updateStudy(study);
        assertEquals(holder.getVersion(), new Long(2));
        
        verify(hibernateHelper).update(studyCaptor.capture());
        
        Study persisted = studyCaptor.getValue();
        assertEquals(persisted.getVersion(), new Long(2));
    }

    @Test
    public void deleteStudyPermanently() {
        dao.deleteStudyPermanently(TEST_APP_ID, "oneId");
        
        verify(hibernateHelper).deleteById(eq(HibernateStudy.class), studyIdCaptor.capture());
        StudyId studyId = studyIdCaptor.getValue();
        assertEquals(studyId.getIdentifier(), "oneId");
        assertEquals(studyId.getAppId(), TEST_APP_ID);
    }    

    @Test(expectedExceptions = PersistenceException.class)
    public void deleteStudyPermanentlyNotFound() {
        doThrow(new PersistenceException()).when(hibernateHelper).deleteById(eq(HibernateStudy.class), any());
            
        dao.deleteStudyPermanently(TEST_APP_ID, "oneId");
    }
    
    @Test
    public void deleteAllStudies() {
        dao.deleteAllStudies(TEST_APP_ID);
        
        verify(hibernateHelper).queryUpdate(queryCaptor.capture(), paramsCaptor.capture());
        assertEquals(queryCaptor.getValue(), "delete from HibernateStudy where appId = :appId");
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
    }
    
    @Test
    public void getStudyIdsUsingSchedule() {
        List<?> studyIds = ImmutableList.of("studyA", "studyB");
        
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        when(mockSession.createNativeQuery(any())).thenReturn(mockNativeQuery);
        doReturn(studyIds).when(mockNativeQuery).list();
        
        List<String> retValue = dao.getStudyIdsUsingSchedule(TEST_APP_ID, SCHEDULE_GUID);
        assertSame(retValue, studyIds);

        verify(mockSession).createNativeQuery(queryCaptor.capture());
        verify(mockNativeQuery).addScalar("id", StandardBasicTypes.STRING);
        verify(mockNativeQuery).setParameter("appId", TEST_APP_ID);
        verify(mockNativeQuery).setParameter("scheduleGuid", SCHEDULE_GUID);
        
        assertEquals(queryCaptor.getValue(), "SELECT id FROM Substudies WHERE studyId = "
                +":appId AND scheduleGuid = :scheduleGuid");
    }
}
