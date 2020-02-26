package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;

import org.hibernate.Session;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;

public class HibernateAssessmentDaoTest extends Mockito {
    
    private static final String QUERY_SQL_EXC_DELETED = "FROM (   SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments   GROUP BY "
            +"identifier) AS latest_assessments INNER JOIN Assessments AS a ON "
            +"a.identifier = latest_assessments.id AND a.revision = latest_assessments.rev "
            +"WHERE appId = :appId AND a.deleted = 0 ORDER BY createdOn DESC";

    private static final String QUERY_SQL_INC_DELETED = "FROM (   SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments   GROUP BY "
            +"identifier) AS latest_assessments INNER JOIN Assessments AS a ON "+
            "a.identifier = latest_assessments.id AND a.revision = latest_assessments.rev "
            +"WHERE appId = :appId ORDER BY createdOn DESC";
    
    private static final String QUERY_GET_REVISIONS_EXC_DELETED = "FROM HibernateAssessment WHERE "
            +"appId = :appId AND identifier = :identifier AND deleted = 0 ORDER BY "
            +"revision DESC";

    private static final String QUERY_GET_REVISIONS_INC_DELETED = "FROM HibernateAssessment WHERE "
            +"appId = :appId AND identifier = :identifier ORDER BY revision DESC";
    
    private static final String APP_ID_VALUE = "appId";
    private static final String ID_VALUE = "identifier";
    private static final String GUID_VALUE = "guid";
    private static final int REV_VALUE = 3;
    private static final HibernateAssessment HIBERNATE_ASSESSMENT = new HibernateAssessment();
    
    @Mock
    HibernateHelper mockHelper;

    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<Function<Session, HibernateAssessment>> functionCaptor;
    
    @Mock
    Session mockSession;
    
    @InjectMocks
    @Spy
    HibernateAssessmentDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(mockHelper.executeWithExceptionHandling(any(), any())).then(answer -> {
            Function<Session,HibernateAssessment> func = answer.getArgument(1);
            return func.apply(mockSession);
        });
    }

    @Test
    public void getAssessmentsExcludeDeleted() {
        List<HibernateAssessment> list = ImmutableList.of(HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT,
                HIBERNATE_ASSESSMENT);
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(5);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
                .thenReturn(list);
        
        PagedResourceList<Assessment> page = dao.getAssessments(APP_ID_VALUE, 0, 20, null, false);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_EXC_DELETED);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_EXC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(page.getItems().size(), 5);
        assertEquals(page.getTotal(), Integer.valueOf(5));
    }
    
    @Test
    public void getAssessmentsIncludeDeleted() {
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(0);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
                .thenReturn(ImmutableList.of());
        
        dao.getAssessments(APP_ID_VALUE, 0, 20, null, true);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_INC_DELETED);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_INC_DELETED);
    }
    
    @Test
    public void getAssessmentRevisionsIncludeDeleted() {
        when(mockHelper.queryCount(queryCaptor.capture(), paramsCaptor.capture()))
            .thenReturn(100);
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT));
        
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(APP_ID_VALUE, ID_VALUE, 0, 20, true);
        assertEquals(page.getItems().size(), 3);
        assertEquals(page.getTotal(), Integer.valueOf(100));
        
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_INC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
    }

    @Test
    public void getAssessmentRevisionsExcludeDeleted() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of());
        
        dao.getAssessmentRevisions(APP_ID_VALUE, ID_VALUE, 0, 20, false);
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_EXC_DELETED);
    }
    
    @Test
    public void getAssessmentByGuid() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, GUID_VALUE);
        assertTrue(retValue.isPresent());
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_GUID);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("guid"), GUID_VALUE);
    }
    
    @Test
    public void getAssessmentByGuidNoEntity() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of());
    
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, GUID_VALUE);
        assertFalse(retValue.isPresent());
    }


    @Test
    public void getAssessmentByIdAndRevision() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, ID_VALUE, REV_VALUE);
        assertTrue(retValue.isPresent());
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_IDENTIFIER);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
        assertEquals(params.get("revision"), REV_VALUE);
    }
    
    @Test
    public void getAssessmentByIdAndRevisionNoEntity() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of());
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, ID_VALUE, REV_VALUE);
        assertFalse(retValue.isPresent());
    }

    @Test
    public void createOrUpdateAssessment() throws Exception {
        when(mockSession.merge(any())).thenReturn(HIBERNATE_ASSESSMENT);
        
        Assessment returnValue = dao.saveAssessment(APP_ID_VALUE, new Assessment());
        assertNotNull(returnValue);
        
        verify(mockSession).merge(any(HibernateAssessment.class));
    }

    @Test
    public void deleteAssessment() throws Exception {
        dao.deleteAssessment(APP_ID_VALUE, new Assessment());
        
        verify(mockSession).remove(any());
    }  
    
    @Test
    public void publishAssessment() throws Exception {
        Assessment original = new Assessment();
        Assessment assessmentToPublish = new Assessment();
        when(mockSession.merge(any())).thenReturn(new HibernateAssessment());
        
        Assessment retValue = dao.publishAssessment(APP_ID_VALUE, original, assessmentToPublish);
        assertNotNull(retValue);
        
        verify(mockHelper).executeWithExceptionHandling(any(HibernateAssessment.class), any());
        verify(mockSession).saveOrUpdate(any(HibernateAssessment.class));
        verify(mockSession).merge(any(HibernateAssessment.class));
    }
}
