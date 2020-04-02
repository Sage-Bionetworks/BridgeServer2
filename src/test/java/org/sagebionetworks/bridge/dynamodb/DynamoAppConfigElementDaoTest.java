package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DynamoAppConfigElementDaoTest {

    private static final String ID_2 = "id2";

    private static final String ID_1 = "id1";

    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private PaginatedQueryList<DynamoAppConfigElement> mockResults;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoAppConfigElement>> queryCaptor;
    
    @Captor
    private ArgumentCaptor<AppConfigElement> appConfigElementCaptor;
    
    private DynamoAppConfigElementDao dao;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao = new DynamoAppConfigElementDao();
        dao.setMapper(mockMapper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getMostRecentElementsIncludeDeleted() {
        DynamoAppConfigElement ace1 = new DynamoAppConfigElement();
        ace1.setId(ID_1);
        ace1.setRevision(3L);
        DynamoAppConfigElement ace2 = new DynamoAppConfigElement();
        ace2.setId(ID_2);
        ace2.setRevision(3L);
        
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockMapper.batchLoad(any(List.class))).thenReturn(appConfigElementMapId1And2());
        
        List<AppConfigElement> returned = dao.getMostRecentElements(TEST_STUDY_IDENTIFIER, true);
        assertEquals(returned.size(), 2);
        assertIdAndRevision(returned.get(0), ID_1, 3L);
        assertIdAndRevision(returned.get(1), ID_2, 3L);
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals(query.getIndexName(), DynamoAppConfigElementDao.STUDY_ID_INDEX_NAME);
        assertFalse(query.isConsistentRead());
        assertFalse(query.isScanIndexForward());
        assertEquals(query.getHashKeyValues().getStudyId(), TestConstants.TEST_STUDY_IDENTIFIER);
        assertNull(query.getHashKeyValues().getId());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getMostRecentElementsExcludeDeleted() {
        DynamoAppConfigElement ace1 = new DynamoAppConfigElement();
        ace1.setId(ID_1);
        ace1.setRevision(2L);
        DynamoAppConfigElement ace2 = new DynamoAppConfigElement();
        ace2.setId(ID_2);
        ace2.setRevision(3L);
        
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockMapper.batchLoad(any(List.class))).thenReturn(appConfigElementMapId1And2());
        
        List<AppConfigElement> returned = dao.getMostRecentElements(TEST_STUDY_IDENTIFIER, false);
        assertEquals(returned.size(), 2);
        assertIdAndRevision(returned.get(0), ID_1, 2L);
        assertIdAndRevision(returned.get(1), ID_2, 3L);
    }
    
    @Test
    public void getMostRecentElementsNoResults() {
        List<AppConfigElement> returned = dao.getMostRecentElements(TEST_STUDY_IDENTIFIER, false);
        assertTrue(returned.isEmpty());
    }

    
    @Test
    public void getMostRecentElement() {
        DynamoAppConfigElement element = new DynamoAppConfigElement();
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.get(0)).thenReturn(element);
        
        AppConfigElement returned = dao.getMostRecentElement(TEST_STUDY_IDENTIFIER, "id");
        assertEquals(returned, element);
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals(query.getHashKeyValues().getKey(), "api:id");
        assertFalse(query.isScanIndexForward());
        assertEquals(query.getLimit(), new Integer(1));
        
        Condition deleteCondition = query.getQueryFilter().get("deleted");
        assertEquals(deleteCondition.getComparisonOperator(), "EQ");
        assertFalse(deleteCondition.getAttributeValueList().get(0).getBOOL());
    }

    @Test
    public void getMostRecentElementNotFound() {
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.get(0)).thenReturn(null);
        
        AppConfigElement returned = dao.getMostRecentElement(TEST_STUDY_IDENTIFIER, "id");
        assertNull(returned);
    }
    
    @Test
    public void getElementRevisionsIncludesDeleted() {
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.stream()).thenReturn(appConfigElementListId1().stream());
        
        List<AppConfigElement> returned = dao.getElementRevisions(TEST_STUDY_IDENTIFIER, ID_1, true);
        assertEquals(returned, appConfigElementListId1());
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals(query.getHashKeyValues().getKey(), "api:id1");
        assertFalse(query.isScanIndexForward());
        assertNull(query.getQueryFilter());
    }
    
    @Test
    public void getElementRevisionsExcludesDeleted() {
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.stream()).thenReturn(appConfigElementListId1().stream());
        
        List<AppConfigElement> returned = dao.getElementRevisions(TEST_STUDY_IDENTIFIER, ID_1, false);
        assertEquals(returned, appConfigElementListId1());
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals(query.getHashKeyValues().getKey(), "api:id1");
        assertFalse(query.isScanIndexForward());
        
        Condition deleteCondition = query.getQueryFilter().get("deleted");
        assertEquals(deleteCondition.getComparisonOperator(), "EQ");
        assertFalse(deleteCondition.getAttributeValueList().get(0).getBOOL());
    }
    
    @Test
    public void getElementRevision() {
        AppConfigElement element = AppConfigElement.create();
        when(mockMapper.load(any())).thenReturn(element);
        
        AppConfigElement returned = dao.getElementRevision(TEST_STUDY_IDENTIFIER, "id", 3L);
        assertEquals(returned, element);
        
        verify(mockMapper).load(appConfigElementCaptor.capture());
        assertEquals(appConfigElementCaptor.getValue().getKey(), "api:id");
        assertEquals(appConfigElementCaptor.getValue().getRevision(), new Long(3));
    }
    
    @Test
    public void saveElementRevision() {
        AppConfigElement element = AppConfigElement.create();
        element.setVersion(1L);
        
        VersionHolder returned = dao.saveElementRevision(element);
        assertEquals(returned.getVersion(), new Long(1));
        
        verify(mockMapper).save(element);
    }
    
    @Test
    public void deleteElementRevisionPermanently() {
        AppConfigElement key = new DynamoAppConfigElement();
        key.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        key.setId("id");
        key.setRevision(3L);
        when(mockMapper.load(key)).thenReturn(key);
        
        dao.deleteElementRevisionPermanently(TEST_STUDY_IDENTIFIER, "id", 3L);
        
        verify(mockMapper).delete(appConfigElementCaptor.capture());
        assertEquals(key.getKey(), "api:id");
        assertEquals(key.getRevision(), new Long(3));
    }
    
    @Test
    public void deleteElementRevisionPermanentlyNotFound() {
        when(mockMapper.load(any())).thenReturn(null);
        
        dao.deleteElementRevisionPermanently(TEST_STUDY_IDENTIFIER, "id", 3L);
        
        verify(mockMapper, never()).delete(any());
    }
    
    // As will happen if version attribute isn't returned or is wrong
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void saveElementRevisionThrowsConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        
        AppConfigElement element = TestUtils.getAppConfigElement();
        
        dao.saveElementRevision(element);
    }
    
    // As will happen if version attribute isn't returned or is wrong
    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void deleteElementRevisionPermanentlyThrowsConditionalCheckFailedException() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(mockMapper.load(any())).thenReturn(element);
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).delete(any());
        
        dao.deleteElementRevisionPermanently(TEST_STUDY_IDENTIFIER, "id", 1L);        
    }
    
    private List<DynamoAppConfigElement> appConfigElementListId1() {
        DynamoAppConfigElement el1V1 = new DynamoAppConfigElement();
        el1V1.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el1V1.setId(ID_1);
        el1V1.setRevision(1L);
        
        DynamoAppConfigElement el1V2 = new DynamoAppConfigElement();
        el1V2.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el1V2.setId(ID_1);
        el1V2.setRevision(2L);

        
        return ImmutableList.of(el1V1, el1V2);
    }
    
    private List<DynamoAppConfigElement> appConfigElementListId1And2() {
        DynamoAppConfigElement el1V1 = new DynamoAppConfigElement();
        el1V1.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el1V1.setId(ID_1);
        el1V1.setRevision(1L);
        
        DynamoAppConfigElement el1V2 = new DynamoAppConfigElement();
        el1V2.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el1V2.setId(ID_1);
        el1V2.setRevision(2L);
        
        DynamoAppConfigElement el1V3 = new DynamoAppConfigElement();
        el1V3.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el1V3.setId(ID_1);
        el1V3.setRevision(3L);
        
        DynamoAppConfigElement el2V1 = new DynamoAppConfigElement();
        el2V1.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el2V1.setId(ID_2);
        el2V1.setRevision(1L);
        
        DynamoAppConfigElement el2V2 = new DynamoAppConfigElement();
        el2V2.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el2V2.setId(ID_2);
        el2V2.setRevision(2L);
        
        DynamoAppConfigElement el2V3 = new DynamoAppConfigElement();
        el2V3.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        el2V3.setId(ID_2);
        el2V3.setRevision(3L);
        
        el1V3.setDeleted(true);
        el2V2.setDeleted(true);
        return ImmutableList.of(el1V1, el1V2, el1V3, el2V1, el2V2, el2V3);
    }
    
    private Map<String,List<DynamoAppConfigElement>> appConfigElementMapId1And2() {
        // Regarding the map structure, from the javadocs: "A map of the loaded objects. Each key in the map 
        // is the name of a DynamoDB table. Each value in the map is a list of objects that have been loaded 
        // from that table. All objects for each table can be cast to the associated user defined type that 
        // is annotated as mapping that table." Given our queries on a single table, this is going to be a map
        // with a single key.
        return new ImmutableMap.Builder<String, List<DynamoAppConfigElement>>()
                .put(DynamoAppConfigElement.class.getSimpleName(), appConfigElementListId1And2()).build();
    }
    
    private void assertIdAndRevision(AppConfigElement element, String id, long revision) {
        assertTrue(element.getId().equals(id) && element.getRevision() == revision, "Missing: " + id + ", " + revision);
    }    
}
