package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.dynamodb.DynamoReportDataDaoTest.OFFSET_KEY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

public class DynamoHealthDataDocumentationDaoTest {
    private static final String DOC_ID = "test-documentation";

    @Mock
    private DynamoDBMapper mockMapper;

    @InjectMocks
    @Spy
    private DynamoHealthDataDocumentationDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdateDocumentation_NewDoc() {
        HealthDataDocumentation doc = new DynamoHealthDataDocumentation();

        HealthDataDocumentation returnedDoc = dao.createOrUpdateDocumentation(doc);
        assertSame(returnedDoc, doc);
        assertNotNull(returnedDoc.getIdentifier());

        verify(mockMapper).save(doc);
    }

    @Test
    public void createOrUpdateDocumentation_UpdateDoc() {
        HealthDataDocumentation doc = new DynamoHealthDataDocumentation();
        doc.setIdentifier(DOC_ID);

        HealthDataDocumentation returnedDoc = dao.createOrUpdateDocumentation(doc);
        assertSame(returnedDoc, doc);
        assertEquals(doc.getIdentifier(), DOC_ID);

        verify(mockMapper).save(doc);
    }

    @Test
    public void deleteDocumentationForParentId() {
        // mock dependencies
        List<DynamoHealthDataDocumentation> docList = ImmutableList.of(new DynamoHealthDataDocumentation());
        doReturn(docList).when(dao).queryHelper(any());

        // execute
        dao.deleteDocumentationForParentId(TEST_APP_ID);

        // validate
        ArgumentCaptor<DynamoDBQueryExpression<DynamoHealthDataDocumentation>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());
        verify(mockMapper).batchDelete(docList);

        DynamoDBQueryExpression<DynamoHealthDataDocumentation> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getParentId(), TEST_APP_ID);
        assertNull(query.getRangeKeyConditions());
    }

    @Test
    public void deleteDocumentationForParentID_NoDoc() {
        // mock dependencies
        doReturn(ImmutableList.of()).when(dao).queryHelper(any());

        // execute
        dao.deleteDocumentationForParentId(TEST_APP_ID);

        // query is validated, just validate that we don't call batchDelete()
        verify(mockMapper, never()).batchDelete(anyList());
    }

    @Test
    public void deleteDocumentationForIdentifier() {
        // mock dependencies
        DynamoHealthDataDocumentation doc = new DynamoHealthDataDocumentation();
        doc.setParentId(TEST_APP_ID);
        doc.setIdentifier(IDENTIFIER);
        when(mockMapper.load(any(DynamoHealthDataDocumentation.class))).thenReturn(doc);

        // execute
        dao.deleteDocumentationForIdentifier(TEST_APP_ID, IDENTIFIER);

        // validate
        ArgumentCaptor<HealthDataDocumentation> docCaptor = ArgumentCaptor.forClass(DynamoHealthDataDocumentation.class);
        verify(mockMapper).load(docCaptor.capture());
        HealthDataDocumentation capturedDoc = docCaptor.getValue();
        assertEquals(capturedDoc.getParentId(), TEST_APP_ID);
        assertEquals(capturedDoc.getIdentifier(), IDENTIFIER);

        verify(mockMapper).delete(doc);
    }

    @Test
    public void deleteDocumentationForIdentifier_NoDoc() {
        dao.deleteDocumentationForIdentifier(TEST_APP_ID, IDENTIFIER);

        verify(mockMapper, never()).delete(any(DynamoHealthDataDocumentation.class));
    }

    @Test
    public void getDocumentationForIdentifier() {
        DynamoHealthDataDocumentation doc = new DynamoHealthDataDocumentation();
        when(mockMapper.load(any(DynamoHealthDataDocumentation.class))).thenReturn(doc);

        HealthDataDocumentation returned = dao.getDocumentationByIdentifier(TEST_APP_ID, DOC_ID);
        ArgumentCaptor<HealthDataDocumentation> docCaptor = ArgumentCaptor.forClass(DynamoHealthDataDocumentation.class);
        verify(mockMapper).load(docCaptor.capture());

        assertEquals(docCaptor.getValue().getParentId(), TEST_APP_ID);
        assertEquals(docCaptor.getValue().getIdentifier(), DOC_ID);
        assertEquals(returned, doc);
    }

    @Test
    public void getDocumentationForIdentifier_NoDoc() {
        when(mockMapper.load(any(DynamoHealthDataDocumentation.class))).thenReturn(null);

        HealthDataDocumentation returned = dao.getDocumentationByIdentifier(TEST_APP_ID, DOC_ID);
        assertNull(returned);
    }

    @Test
    public void getDocumentationForParentId() {
        // mock dependencies
        DynamoHealthDataDocumentation doc = new DynamoHealthDataDocumentation();
        doReturn(ImmutableList.of(doc)).when(dao).queryHelper(any());

        // execute
        ForwardCursorPagedResourceList<HealthDataDocumentation> resultList = dao.getDocumentationForParentId(
                TEST_APP_ID, API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
        assertEquals(resultList.getItems().size(), 1);
        assertSame(resultList.getItems().get(0), doc);
        assertNull(resultList.getNextPageOffsetKey());

        // validate
        ArgumentCaptor<DynamoDBQueryExpression<DynamoHealthDataDocumentation>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(dao).queryHelper(queryCaptor.capture());

        DynamoDBQueryExpression<DynamoHealthDataDocumentation> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getParentId(), TEST_APP_ID);
        assertEquals(query.getLimit().intValue(), API_DEFAULT_PAGE_SIZE + 1);
        assertEquals(query.getRangeKeyConditions().size(), 1);

        Condition rangeKeyCondition = query.getRangeKeyConditions().get("identifier");
        assertEquals(rangeKeyCondition.getComparisonOperator(), ComparisonOperator.GE.toString());
        assertEquals(rangeKeyCondition.getAttributeValueList().size(), 1);
        assertEquals(rangeKeyCondition.getAttributeValueList().get(0).getS(), OFFSET_KEY);
    }

    @Test
    public void getDocumentationForParentId_NextPageOffsetKey() {
        // mock dependencies
        DynamoHealthDataDocumentation doc0 = new DynamoHealthDataDocumentation();
        DynamoHealthDataDocumentation doc1 = new DynamoHealthDataDocumentation();
        DynamoHealthDataDocumentation doc2 = new DynamoHealthDataDocumentation();
        doc2.setIdentifier(DOC_ID);

        doReturn(ImmutableList.of(doc0, doc1, doc2)).when(dao).queryHelper(any());

        // execute
        ForwardCursorPagedResourceList<HealthDataDocumentation> resultList = dao.getDocumentationForParentId(
                TEST_APP_ID, 2, OFFSET_KEY);
        assertEquals(resultList.getItems().size(), 2);
        assertSame(resultList.getItems().get(0), doc0);
        assertSame(resultList.getItems().get(1), doc1);
        assertEquals(resultList.getNextPageOffsetKey(), DOC_ID);
    }

    @Test
    public void getDocumentationForParentId_NoDoc() {
        ForwardCursorPagedResourceList<HealthDataDocumentation> emptyList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(), null, true);
        doReturn(emptyList).when(dao).getDocumentationForParentId(TEST_APP_ID, API_DEFAULT_PAGE_SIZE, OFFSET_KEY);

        ForwardCursorPagedResourceList<HealthDataDocumentation> result = dao.getDocumentationForParentId(TEST_APP_ID,
                API_DEFAULT_PAGE_SIZE, OFFSET_KEY);

        assertEquals(result.getItems().size(), 0);
    }
}