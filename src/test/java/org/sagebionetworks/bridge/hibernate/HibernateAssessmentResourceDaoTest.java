package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.RESOURCE_CATEGORIES;
import static org.sagebionetworks.bridge.models.assessments.HibernateAssessmentResourceTest.createHibernateAssessmentResource;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.AssessmentResource;
import org.sagebionetworks.bridge.models.assessments.AssessmentResourceTest;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessmentResource;

public class HibernateAssessmentResourceDaoTest extends Mockito {
    private static final String ASSESSMENT_ID = "oneAssessmentId";
    private static final HibernateAssessmentResource ASSESSMENT_RESOURCE = createHibernateAssessmentResource();
    
    private static String FULL_PAGE_QUERY = "from HibernateAssessmentResource WHERE assessmentId = :assessmentId "+
            "AND createdAtRevision >= :minRevision AND createdAtRevision <= :maxRevision AND deleted = 0 AND "+
            "category in :categories ORDER BY title ASC";
    
    private static String FULL_PAGE_COUNT_QUERY = "SELECT COUNT(DISTINCT guid) " + FULL_PAGE_QUERY;
    
    private static String MIN_PAGE_QUERY = "from HibernateAssessmentResource WHERE assessmentId = :assessmentId "+
            "ORDER BY title ASC";
    
    private static String MIN_PAGE_COUNT_QUERY = "SELECT COUNT(DISTINCT guid) " + MIN_PAGE_QUERY;
    
    private static String GET_QUERY = "from HibernateAssessmentResource WHERE "
            +"assessmentId = :assessmentId AND guid = :guid";

    @Mock
    HibernateHelper mockHelper;
    
    @Mock
    Session mockSession;
    
    @InjectMocks
    HibernateAssessmentResourceDao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @Captor
    ArgumentCaptor<String> countQueryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> countParamsCaptor;
    
    @Captor
    ArgumentCaptor<HibernateAssessmentResource> hibernateResourceCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(mockHelper.executeWithExceptionHandling(any(), any())).then(answer -> {
            Function<Session,HibernateAssessmentResource> func = answer.getArgument(1);
            return func.apply(mockSession);
        });
    }
    
    @Test
    public void getResources() {
        List<HibernateAssessmentResource> list = ImmutableList.of(ASSESSMENT_RESOURCE);
        when(mockHelper.queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(10), eq(50), eq(HibernateAssessmentResource.class))).thenReturn(list);
        
        PagedResourceList<AssessmentResource> retValue = dao.getResources(ASSESSMENT_ID, 10, 50, 
                RESOURCE_CATEGORIES, 1, 100, false);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0).getGuid(), GUID);

        verify(mockHelper).queryCount(countQueryCaptor.capture(), countParamsCaptor.capture());
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(10), eq(50), eq(HibernateAssessmentResource.class));
        
        Map<String, Object> params = countParamsCaptor.getValue();
        assertEquals(countQueryCaptor.getValue(), FULL_PAGE_COUNT_QUERY);
        assertEquals(params.get("assessmentId"), ASSESSMENT_ID);
        assertEquals(params.get("minRevision"), 1);
        assertEquals(params.get("maxRevision"), 100);
        assertEquals(params.get("categories"), RESOURCE_CATEGORIES);
        
        params = paramsCaptor.getValue();
        assertEquals(queryCaptor.getValue(), FULL_PAGE_QUERY);
        assertEquals(params.get("assessmentId"), ASSESSMENT_ID);
        assertEquals(params.get("minRevision"), 1);
        assertEquals(params.get("maxRevision"), 100);
        assertEquals(params.get("categories"), RESOURCE_CATEGORIES);
    }
    
    @Test
    public void getResourcesNullArguments() {
        List<HibernateAssessmentResource> list = ImmutableList.of(ASSESSMENT_RESOURCE);
        when(mockHelper.queryGet(any(), any(), isNull(), isNull(), eq(HibernateAssessmentResource.class)))
                .thenReturn(list);
        
        PagedResourceList<AssessmentResource> retValue = dao.getResources(ASSESSMENT_ID, null, null, 
                null, null, null, true);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0).getGuid(), GUID);

        verify(mockHelper).queryCount(countQueryCaptor.capture(), countParamsCaptor.capture());
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                isNull(), isNull(), eq(HibernateAssessmentResource.class));
        
        Map<String, Object> params = countParamsCaptor.getValue();
        assertEquals(countQueryCaptor.getValue(), MIN_PAGE_COUNT_QUERY);
        assertEquals(params.get("assessmentId"), ASSESSMENT_ID);
        
        params = paramsCaptor.getValue();
        assertEquals(queryCaptor.getValue(), MIN_PAGE_QUERY);
        assertEquals(params.get("assessmentId"), ASSESSMENT_ID);
    }
    
    @Test
    public void getResource() {
        List<HibernateAssessmentResource> resources = ImmutableList.of(new HibernateAssessmentResource());
        when(mockHelper.queryGet(any(), any(), eq(0), eq(1), eq(HibernateAssessmentResource.class)))
                .thenReturn(resources);
        
        Optional<AssessmentResource> optional = dao.getResource(ASSESSMENT_ID, GUID);
        assertTrue(optional.isPresent());
        
        verify(mockHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), 
                eq(0), eq(1), eq(HibernateAssessmentResource.class));
        
        assertEquals(queryCaptor.getValue(), GET_QUERY);
        assertEquals(paramsCaptor.getValue().get("assessmentId"), ASSESSMENT_ID);
        assertEquals(paramsCaptor.getValue().get("guid"), GUID);
    }
    
    @Test
    public void getResourceNoResult() {
        List<HibernateAssessmentResource> resources = ImmutableList.of();
        when(mockHelper.queryGet(any(), any(), eq(0), eq(1), eq(HibernateAssessmentResource.class)))
                .thenReturn(resources);
        
        Optional<AssessmentResource> optional = dao.getResource(ASSESSMENT_ID, GUID);
        assertFalse(optional.isPresent());
    }

    @Test
    public void saveResource() {
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        when(mockSession.merge(any())).thenReturn(ASSESSMENT_RESOURCE);
        
        AssessmentResource retValue = dao.saveResource(ASSESSMENT_ID, resource);
        assertEquals(retValue.getGuid(), GUID);
        
        verify(mockSession).merge(hibernateResourceCaptor.capture());
        assertEquals(hibernateResourceCaptor.getValue().getGuid(), GUID);
    }

    @Test
    public void deleteResource() {
        AssessmentResource resource = AssessmentResourceTest.createAssessmentResource();
        
        dao.deleteResource(ASSESSMENT_ID, resource);
        
        verify(mockSession).remove(hibernateResourceCaptor.capture());
        assertEquals(hibernateResourceCaptor.getValue().getGuid(), GUID);
    }    
}
