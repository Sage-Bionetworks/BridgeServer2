package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateAssessmentDao.DELETE_CONFIG_SQL;
import static org.sagebionetworks.bridge.hibernate.HibernateAssessmentDao.DELETE_RESOURCES_SQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.OptimisticLockException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;

public class HibernateAssessmentDaoTest extends Mockito {
    
    private static final String QUERY_SQL_EXC_DELETED = "FROM ( SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments WHERE appId = :appId "
            +"GROUP BY identifier) AS latest_assessments INNER JOIN Assessments AS a ON "
            +"a.identifier = latest_assessments.id AND a.revision = latest_assessments.rev "
            +"WHERE appId = :appId AND deleted = 0 ORDER BY createdOn DESC";

    private static final String QUERY_SQL_WITH_OWNERID_EXC_DELETED = "FROM ( SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments WHERE appId = :appId "
            +"AND ownerId = :ownerId GROUP BY identifier) AS latest_assessments INNER JOIN Assessments AS a ON "
            +"a.identifier = latest_assessments.id AND a.revision = latest_assessments.rev "
            +"WHERE appId = :appId AND ownerId = :ownerId AND deleted = 0 ORDER BY createdOn DESC";
    
    private static final String QUERY_SQL_INC_DELETED = "FROM ( SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments WHERE appId = :appId "
            +"GROUP BY identifier) AS latest_assessments INNER JOIN Assessments AS a ON "+
            "a.identifier = latest_assessments.id AND a.revision = latest_assessments.rev "
            +"WHERE appId = :appId ORDER BY createdOn DESC";
    
    private static final String QUERY_SQL_WITH_TAGS = "FROM ( SELECT DISTINCT "
            +"identifier as id, MAX(revision) AS rev FROM Assessments WHERE appId = :appId "
            +"GROUP BY identifier) AS latest_assessments INNER JOIN Assessments AS a ON "
            +"a.identifier = latest_assessments.id AND a.revision = latest_assessments.rev "
            +"WHERE appId = :appId AND guid IN (SELECT DISTINCT assessmentGuid FROM "
            +"AssessmentTags WHERE tagValue IN :tags) AND deleted = 0 ORDER BY createdOn DESC";
    
    private static final String QUERY_GET_REVISIONS_EXC_DELETED = "FROM HibernateAssessment WHERE "
            +"appId = :appId AND identifier = :identifier AND deleted = 0 ORDER BY "
            +"revision DESC";

    private static final String QUERY_GET_REVISIONS_WITH_OWNERID_EXC_DELETED = "FROM HibernateAssessment WHERE "
            +"appId = :appId AND identifier = :identifier AND ownerId = :ownerId AND deleted = 0 ORDER BY "
            +"revision DESC";
    
    private static final String QUERY_GET_REVISIONS_INC_DELETED = "FROM HibernateAssessment WHERE "
            +"appId = :appId AND identifier = :identifier ORDER BY revision DESC";

    private static final String QUERY_GET_REVISIONS_WITH_OWNERID_INC_DELETED = "FROM HibernateAssessment WHERE "
            +"appId = :appId AND identifier = :identifier AND ownerId = :ownerId ORDER BY revision DESC";
    
    private static final String QUERY_COUNT_FROM_ORG = "SELECT COUNT(*) FROM HibernateAssessment WHERE " +
            "(appId = :appId AND ownerId = :ownerId)";
    
    private static final String APP_ID_VALUE = "appId";
    private static final String ID_VALUE = "identifier";
    private static final String GUID_VALUE = "guid";
    private static final String ORG_ID = "test-org";
    private static final int REV_VALUE = 3;
    private static final HibernateAssessment HIBERNATE_ASSESSMENT = new HibernateAssessment();
    
    @Mock
    HibernateHelper mockHelper;

    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<HibernateAssessment> assessmentCaptor;
    
    @Captor
    ArgumentCaptor<HibernateAssessmentConfig> configCaptor;
    
    @Mock
    Session mockSession;
    
    @Mock
    NativeQuery<?> mockDelResourcesQuery;
    
    @Mock
    NativeQuery<?> mockDelConfigQuery;
    
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
    public void getAssessmentsExcludeDeleted_withoutOwnerId() {
        List<HibernateAssessment> list = ImmutableList.of(HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT,
                HIBERNATE_ASSESSMENT);
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(5);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
                .thenReturn(list);
        
        PagedResourceList<Assessment> page = dao.getAssessments(APP_ID_VALUE, null, 0, 20, null, false);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_EXC_DELETED);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_EXC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(page.getItems().size(), 5);
        assertEquals(page.getTotal(), Integer.valueOf(5));
    }
    
    @Test
    public void getAssessmentsExcludeDeleted_withOwnerId() {
        List<HibernateAssessment> list = ImmutableList.of(HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT,
                HIBERNATE_ASSESSMENT);
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(5);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
                .thenReturn(list);
        
        PagedResourceList<Assessment> page = dao.getAssessments(APP_ID_VALUE, TEST_ORG_ID, 0, 20, null, false);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_WITH_OWNERID_EXC_DELETED);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_WITH_OWNERID_EXC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(page.getItems().size(), 5);
        assertEquals(page.getTotal(), Integer.valueOf(5));
    }
    
    @Test
    public void getAssessmentsIncludeDeleted_withoutOwnerId() {
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(0);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
                .thenReturn(ImmutableList.of());
        
        dao.getAssessments(APP_ID_VALUE, null, 0, 20, null, true);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_INC_DELETED);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_INC_DELETED);
    }
    
    @Test
    public void getAssessmentsWithTags() {
        when(mockHelper.nativeQueryCount(queryCaptor.capture(), any())).thenReturn(0);
        when(mockHelper.nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
                .thenReturn(ImmutableList.of());
        
        dao.getAssessments(APP_ID_VALUE, null, 0, 20, ImmutableSet.of("tagA", "tagB"), false);
        assertEquals(queryCaptor.getAllValues().get(0), "SELECT count(*) " + QUERY_SQL_WITH_TAGS);
        assertEquals(queryCaptor.getAllValues().get(1), "SELECT * " + QUERY_SQL_WITH_TAGS);
    }
    
    @Test
    public void getAssessmentRevisionsIncludeDeleted_withoutOwnerId() {
        when(mockHelper.queryCount(queryCaptor.capture(), paramsCaptor.capture()))
            .thenReturn(100);
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT));
        
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(APP_ID_VALUE, null, ID_VALUE, 0, 20, true);
        assertEquals(page.getItems().size(), 3);
        assertEquals(page.getTotal(), Integer.valueOf(100));
        
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_INC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
        assertNull(params.get("ownerId"));
    }

    @Test
    public void getAssessmentRevisionsIncludeDeleted_withOwnerId() {
        when(mockHelper.queryCount(queryCaptor.capture(), paramsCaptor.capture()))
            .thenReturn(100);
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT, HIBERNATE_ASSESSMENT));
        
        PagedResourceList<Assessment> page = dao.getAssessmentRevisions(APP_ID_VALUE, TEST_ORG_ID, ID_VALUE, 0, 20, true);
        assertEquals(page.getItems().size(), 3);
        assertEquals(page.getTotal(), Integer.valueOf(100));
        
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_WITH_OWNERID_INC_DELETED);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
        assertEquals(params.get("ownerId"), TEST_ORG_ID);
    }
    
    @Test
    public void getAssessmentRevisionsExcludeDeleted_withoutOwnerId() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of());
        
        dao.getAssessmentRevisions(APP_ID_VALUE, null, ID_VALUE, 0, 20, false);
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_EXC_DELETED);
        assertNull(paramsCaptor.getValue().get("ownerId"));
    }

    @Test
    public void getAssessmentRevisionsIdExcludeDeleted_withOwner() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(20), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of());
        
        dao.getAssessmentRevisions(APP_ID_VALUE, TEST_ORG_ID, ID_VALUE, 0, 20, false);
        assertEquals(queryCaptor.getValue(), QUERY_GET_REVISIONS_WITH_OWNERID_EXC_DELETED);
        assertEquals(paramsCaptor.getValue().get("ownerId"), TEST_ORG_ID);
    }
    
    @Test
    public void getAssessmentByGuid_withOwnerId() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, TEST_ORG_ID, GUID_VALUE);
        assertTrue(retValue.isPresent());
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_GUID + " AND ownerId = :ownerId");
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("guid"), GUID_VALUE);
        assertEquals(params.get("ownerId"), TEST_ORG_ID);
    }

    @Test
    public void getAssessmentByGuid_withoutOwnerId() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, null, GUID_VALUE);
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
    
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, null, GUID_VALUE);
        assertFalse(retValue.isPresent());
    }

    @Test
    public void getAssessmentByIdAndRevision_withoutOwnerId() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, null, ID_VALUE, REV_VALUE);
        assertTrue(retValue.isPresent());
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_IDENTIFIER);
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
        assertEquals(params.get("revision"), REV_VALUE);
    }
    
    @Test
    public void getAssessmentByIdAndRevision_withOwnerId() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of(HIBERNATE_ASSESSMENT));
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, TEST_ORG_ID, ID_VALUE, REV_VALUE);
        assertTrue(retValue.isPresent());
        
        assertEquals(queryCaptor.getValue(), HibernateAssessmentDao.GET_BY_IDENTIFIER + " AND ownerId = :ownerId");
        
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get("appId"), APP_ID_VALUE);
        assertEquals(params.get("identifier"), ID_VALUE);
        assertEquals(params.get("revision"), REV_VALUE);
        assertEquals(params.get("ownerId"), TEST_ORG_ID);
    }
    
    @Test
    public void getAssessmentByIdAndRevisionNoEntity() {
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(null), eq(null), eq(HibernateAssessment.class)))
            .thenReturn(ImmutableList.of());
        
        Optional<Assessment> retValue = dao.getAssessment(APP_ID_VALUE, null, ID_VALUE, REV_VALUE);
        assertFalse(retValue.isPresent());
    }
    
    @Test
    public void createAssessment() throws Exception {
        Assessment assessment = AssessmentTest.createAssessment();
        AssessmentConfig config = new AssessmentConfig();
        
        when(mockSession.merge(any())).thenReturn(
                HibernateAssessment.create(APP_ID_VALUE, assessment));
        
        Assessment retValue = dao.createAssessment(APP_ID_VALUE, assessment, config);
        assertEquals(retValue.getGuid(), GUID);
        
        verify(mockSession).persist(configCaptor.capture());
        assertEquals(configCaptor.getValue().getGuid(), GUID);
        
        verify(mockSession).merge(assessmentCaptor.capture());
        assertEquals(assessmentCaptor.getValue().getGuid(), GUID);
    }

    @Test
    public void updateAssessment() throws Exception {
        when(mockSession.merge(any())).thenReturn(HIBERNATE_ASSESSMENT);
        
        Assessment returnValue = dao.updateAssessment(APP_ID_VALUE, new Assessment());
        assertNotNull(returnValue);
        
        verify(mockSession).merge(any(HibernateAssessment.class));
    }
    
    // I discovered a ClassCastException because we're not converting and returning
    // a BridgeEntity within the lambda. Test verifies this is fixed.
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void createOptimisticLockException() throws Exception {
        SessionFactory mockSessionFactory = mock(SessionFactory.class);
        PersistenceExceptionConverter exceptionConverter = new BasicPersistenceExceptionConverter();
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        
        HibernateHelper helper = new HibernateHelper(mockSessionFactory, exceptionConverter);
        dao.setHibernateHelper(helper);
        
        when(mockSession.merge(any())).thenThrow(new OptimisticLockException());
        
        dao.createAssessment(APP_ID_VALUE, new Assessment(), new AssessmentConfig());
    }

    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void updateOptimisticLockException() throws Exception {
        SessionFactory mockSessionFactory = mock(SessionFactory.class);
        PersistenceExceptionConverter exceptionConverter = new BasicPersistenceExceptionConverter();
        when(mockSessionFactory.openSession()).thenReturn(mockSession);
        
        HibernateHelper helper = new HibernateHelper(mockSessionFactory, exceptionConverter);
        dao.setHibernateHelper(helper);
        
        when(mockSession.merge(any())).thenThrow(new OptimisticLockException());
        
        dao.updateAssessment(APP_ID_VALUE, new Assessment());
    }
    
    @Test
    public void deleteAssessmentLeaveResources() throws Exception {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 2);
        doReturn(page).when(dao).getAssessmentRevisions(APP_ID_VALUE, null, IDENTIFIER, 0, 1, true);
        
        when(mockSession.createNativeQuery(DELETE_CONFIG_SQL)).thenReturn(mockDelConfigQuery);
        
        Assessment assessment = AssessmentTest.createAssessment();
        
        dao.deleteAssessment(APP_ID_VALUE, assessment);
        
        verify(mockDelResourcesQuery, never()).executeUpdate();
        verify(mockDelConfigQuery).setParameter("guid", GUID);
        verify(mockDelConfigQuery).executeUpdate();
        verify(mockSession).remove(any());
    }

    @Test
    public void deleteAssessmentWithResources() throws Exception {
        PagedResourceList<Assessment> page = new PagedResourceList<>(ImmutableList.of(), 1);
        doReturn(page).when(dao).getAssessmentRevisions(APP_ID_VALUE, null, IDENTIFIER, 0, 1, true);
        
        when(mockSession.createNativeQuery(DELETE_RESOURCES_SQL)).thenReturn(mockDelResourcesQuery);
        when(mockSession.createNativeQuery(DELETE_CONFIG_SQL)).thenReturn(mockDelConfigQuery);
        Assessment assessment = AssessmentTest.createAssessment();
        
        dao.deleteAssessment(APP_ID_VALUE, assessment);
        
        verify(mockDelResourcesQuery).setParameter("appId", APP_ID_VALUE);
        verify(mockDelResourcesQuery).setParameter("assessmentId", IDENTIFIER);
        verify(mockDelResourcesQuery).executeUpdate();
        
        verify(mockDelConfigQuery).setParameter("guid", GUID);
        verify(mockDelConfigQuery).executeUpdate();
        
        verify(mockSession).remove(any());
    }
    
    @Test
    public void publishAssessment() throws Exception {
        Assessment original = new Assessment();
        Assessment assessmentToPublish = AssessmentTest.createAssessment();
        AssessmentConfig originConfig = new AssessmentConfig();
        when(mockSession.merge(any())).thenReturn(new HibernateAssessment());
        
        Assessment retValue = dao.publishAssessment(APP_ID_VALUE, original, assessmentToPublish, originConfig);
        assertNotNull(retValue);
        
        verify(mockHelper).executeWithExceptionHandling(any(HibernateAssessment.class), any());
        verify(mockSession).saveOrUpdate(any(HibernateAssessmentConfig.class));
        verify(mockSession).saveOrUpdate(any(HibernateAssessment.class));
    }
    
    @Test
    public void importAssessment() throws Exception {
        Assessment assessmentToImport = AssessmentTest.createAssessment();
        AssessmentConfig configToImport = new AssessmentConfig();
        
        Assessment retValue = dao.importAssessment(APP_ID_VALUE, assessmentToImport, configToImport);
        assertNotNull(retValue);
        
        verify(mockHelper).executeWithExceptionHandling(any(HibernateAssessment.class), any());
        verify(mockSession).saveOrUpdate(any(HibernateAssessmentConfig.class));
        verify(mockSession).merge(any(HibernateAssessment.class));
    }

    @Test
    public void hasAssessmentFromOrg() {
        when(mockHelper.queryCount(any(), any())).thenReturn(1);
        assertTrue(dao.hasAssessmentFromOrg(APP_ID_VALUE, ORG_ID));
        verify(mockHelper).queryCount(any(), any());

        when(mockHelper.queryCount(any(), any())).thenReturn(0, 0);
        assertFalse(dao.hasAssessmentFromOrg(APP_ID_VALUE, ORG_ID));
        // Mockito remembers the total count of times method called, so here's 1 + 2 = 3.
        verify(mockHelper, times(3)).queryCount(any(), any());

        when(mockHelper.queryCount(any(), any())).thenReturn(0, 3);
        assertTrue(dao.hasAssessmentFromOrg(APP_ID_VALUE, ORG_ID));
        // Mockito remembers the total count of times method called, so here's 1 + 2 = 3.
        verify(mockHelper, times(5)).queryCount(queryCaptor.capture(), paramsCaptor.capture());

        List<String> queries = queryCaptor.getAllValues();
        List<Map<String, Object>> paramsList = paramsCaptor.getAllValues();
        for (String query : queries) {
            assertEquals(QUERY_COUNT_FROM_ORG, query);
        }
        Map<String, Object> privateMap = paramsList.get(0);
        Map<String, Object> publishedMap = paramsList.get(1);
        assertEquals(APP_ID_VALUE, privateMap.get("appId"));
        assertEquals(ORG_ID, privateMap.get("ownerId"));
        assertEquals("shared", publishedMap.get("appId"));
        assertEquals(APP_ID_VALUE + ":" + ORG_ID, publishedMap.get("ownerId"));
    }
}
