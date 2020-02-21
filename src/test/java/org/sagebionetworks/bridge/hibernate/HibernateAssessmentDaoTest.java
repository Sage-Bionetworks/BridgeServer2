package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;

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

public class HibernateAssessmentDaoTest extends Mockito {
    
    private static final String QUERY_SQL_EXC_DELETED = "FROM (   SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments   WHERE appId = "
            +":appId GROUP BY identifier ) AS latest_assessments INNER JOIN "
            +"Assessments AS a ON a.identifier = latest_assessments.id AND a.revision = "
            +"latest_assessments.rev WHERE a.deleted = 0 ORDER BY createdOn DESC";

    private static final String QUERY_SQL_INC_DELETED = "FROM (   SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments   WHERE appId = "
            +":appId GROUP BY identifier ) AS latest_assessments INNER JOIN "
            +"Assessments AS a ON a.identifier = latest_assessments.id AND a.revision = "
            +"latest_assessments.rev ORDER BY createdOn DESC";
    
    private static final String QUERY_GET_REVISIONS_EXC_DELETED = "FROM Assessment WHERE "
            +"appId = :appId AND identifier = :identifier AND deleted = 0 ORDER BY "
            +"revision DESC";

    private static final String QUERY_GET_REVISIONS_INC_DELETED = "FROM Assessment WHERE "
            +"appId = :appId AND identifier = :identifier ORDER BY revision DESC";
    
    private static final String APP_ID_VALUE = "appId";
    private static final String ID_VALUE = "identifier";
    private static final String GUID_VALUE = "guid";
    private static final int REV_VALUE = 3;
    private static final Assessment ASSESSMENT = new Assessment();
    
    @Mock
    HibernateHelper mockHelper;

    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<Function<Session, Assessment>> functionCaptor;
    
    @Mock
    Session mockSession;
    
    @InjectMocks
    @Spy
    HibernateAssessmentDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(mockHelper.executeWithExceptionHandling(any(), any())).then(answer -> {
            Function<Session,Assessment> func = answer.getArgument(1);
            return func.apply(mockSession);
        });
    }

    @Test
    public void getAssessmentsExcludeDeleted() {
        List<Assessment> list = ImmutableList.of(ASSESSMENT, ASSESSMENT, ASSESSMENT, ASSESSMENT,
                ASSESSMENT);
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(5);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
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
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
                .thenReturn(ImmutableList.of());
        
        dao.getAssessments(APP_ID_VALUE, 0, 20, null, true);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_INC_DELETED);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_INC_DELETED);
    }
    
    @Test
    public void getAssessmentRevisionsIncludeDeleted() {
        when(mockHelper.queryCount(queryCaptor.capture(), paramsCaptor.capture()))
            .thenReturn(100);
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
            .thenReturn(ImmutableList.of(ASSESSMENT, ASSESSMENT, ASSESSMENT));
        
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
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
            .thenReturn(ImmutableList.of());
        
        dao.getAssessmentRevisions(APP_ID_VALUE, ID_VALUE, 0, 20, false);
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_EXC_DELETED);
    }
    
    @Test
    public void getAssessmentByGuid() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(Assessment.class)))
            .thenReturn(ImmutableList.of(ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, GUID_VALUE);
        assertSame(retValue.get(), ASSESSMENT);
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_GUID);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("guid"), GUID_VALUE);
    }
    
    @Test
    public void getAssessmentByGuidNoEntity() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(Assessment.class)))
            .thenReturn(ImmutableList.of());
    
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, GUID_VALUE);
        assertFalse(retValue.isPresent());
    }


    @Test
    public void getAssessmentByIdAndRevision() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(Assessment.class)))
            .thenReturn(ImmutableList.of(ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, ID_VALUE, REV_VALUE);
        assertSame(retValue.get(), ASSESSMENT);
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_IDENTIFIER);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
        assertEquals(params.get("revision"), REV_VALUE);
    }
    
    @Test
    public void getAssessmentByIdAndRevisionNoEntity() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(Assessment.class)))
            .thenReturn(ImmutableList.of());
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, ID_VALUE, REV_VALUE);
        assertFalse(retValue.isPresent());
    }

    @Test
    public void createOrUpdateAssessment() throws Exception {
        when(mockSession.merge(any())).thenReturn(ASSESSMENT);
        
        Assessment returnValue = dao.saveAssessment(ASSESSMENT);
        assertSame(returnValue, ASSESSMENT);
        
        verify(mockSession).merge(ASSESSMENT);
    }

    @Test
    public void deleteAssessment() throws Exception {
        dao.deleteAssessment(ASSESSMENT);
        
        verify(mockSession).remove(ASSESSMENT);
    }  
    
    @Test
    public void publishAssessment() throws Exception {
        Assessment original = new Assessment();
        Assessment assessmentToPublish = new Assessment();
        when(mockSession.merge(original)).thenReturn(original);
        
        Assessment retValue = dao.publishAssessment(original, assessmentToPublish);
        assertEquals(retValue, original);
        
        verify(mockHelper).executeWithExceptionHandling(eq(original), any());
        verify(mockSession).saveOrUpdate(assessmentToPublish);
        verify(mockSession).merge(original);
    }
}
