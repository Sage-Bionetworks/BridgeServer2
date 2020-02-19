package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.STRING_CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.STRING_TAGS;
import static org.sagebionetworks.bridge.TestConstants.TAGS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
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

public class HibernateAssessmentDaoTest extends Mockito {
    
    private static final String QUERY_SQL_EXC_DELETED = "SELECT * FROM (SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments WHERE appId = "
            +":appId AND deleted = 0 GROUP BY identifier) AS latest_assessments INNER JOIN "
            +"Assessments a ON a.identifier = latest_assessments.id AND a.revision = "
            +"latest_assessments.rev ORDER BY createdOn DESC";

    private static final String QUERY_SQL_INC_DELETED = "SELECT * FROM (SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments WHERE appId = "
            +":appId GROUP BY identifier) AS latest_assessments INNER JOIN "
            +"Assessments a ON a.identifier = latest_assessments.id AND a.revision = "
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
    
    @Mock
    HibernateHelper mockHelper;

    @Mock
    Assessment mockAssessment1;
    
    @Mock
    Assessment mockAssessment2;
    
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
        when(mockAssessment2.getCategories()).thenReturn(CATEGORIES);
        when(mockAssessment2.getTags()).thenReturn(TAGS);
        
        List<Assessment> list = ImmutableList.of(mockAssessment1, mockAssessment2, mockAssessment1, mockAssessment2,
                mockAssessment2);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
                .thenReturn(list);
        
        PagedResourceList<Assessment> page = dao.getAssessments(APP_ID_VALUE, 0, 20, STRING_CATEGORIES, STRING_TAGS, false);
        assertEquals(queryCaptor.getValue(), QUERY_SQL_EXC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        // Tags are only matched on mockAssessment2 which occurs 3 times in the mock list
        assertEquals(page.getItems().size(), 3);
        assertEquals(page.getTotal(), Integer.valueOf(3));
    }
    
    @Test
    public void getAssessmentsIncludeDeleted() {
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
                .thenReturn(ImmutableList.of());
        
        dao.getAssessments(APP_ID_VALUE, 0, 20, null, null, true);
        assertEquals(queryCaptor.getValue(), QUERY_SQL_INC_DELETED);
    }
    
    @Test
    public void getAssessmentsOffsetDoesNotExceedSubListSize() {
        when(mockHelper.nativeQueryGet(any(), any(), eq(3), eq(20), eq(Assessment.class)))
            .thenReturn(ImmutableList.of(mockAssessment1, mockAssessment2));
        
        PagedResourceList<Assessment> page = dao.getAssessments(APP_ID_VALUE, 3, 20, null, null, true);
        assertTrue(page.getItems().isEmpty());
        assertEquals(page.getTotal(), Integer.valueOf(2));
    }

    @Test
    public void getAssessmentRevisionsIncludeDeleted() {
        when(mockHelper.queryCount(queryCaptor.capture(), paramsCaptor.capture()))
            .thenReturn(100);
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(Assessment.class)))
            .thenReturn(ImmutableList.of(mockAssessment1, mockAssessment1, mockAssessment1));
        
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
            .thenReturn(ImmutableList.of(mockAssessment1));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, GUID_VALUE);
        assertSame(retValue.get(), mockAssessment1);
        
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
            .thenReturn(ImmutableList.of(mockAssessment1));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, ID_VALUE, REV_VALUE);
        assertSame(retValue.get(), mockAssessment1);
        
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
    public void createAssessment() throws Exception {
        when(mockSession.merge(any())).thenReturn(mockAssessment1);
        
        Assessment returnValue = dao.createAssessment(mockAssessment1);
        assertSame(returnValue, mockAssessment1);
        
        verify(mockSession).merge(mockAssessment1);
    }

    @Test
    public void updateAssessment() throws Exception {
        when(mockSession.merge(mockAssessment1)).thenReturn(mockAssessment1);
        
        Assessment returnValue = dao.updateAssessment(mockAssessment1);
        assertEquals(returnValue, mockAssessment1);
        
        verify(mockSession).merge(mockAssessment1);
    }

    @Test
    public void deleteAssessment() throws Exception {
        dao.deleteAssessment(mockAssessment1);
        
        verify(mockSession).remove(mockAssessment1);
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
