package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.upload.UploadSchema;

@SuppressWarnings({ "ConstantConditions", "rawtypes", "RedundantCast", "unchecked" })
public class DynamoUploadSchemaDaoMockTest {
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;

    private DynamoDBMapper mapper;
    private DynamoUploadSchema daoInputSchema;
    private DynamoUploadSchemaDao dao;

    @BeforeMethod
    public void setup() {
        daoInputSchema = new DynamoUploadSchema();
        mapper = mock(DynamoDBMapper.class);
        dao = spy(new DynamoUploadSchemaDao());
        dao.setDdbMapper(mapper);
    }

    @Test
    public void create() {
        // Create a schema with a version, to make sure we clear it.
        daoInputSchema.setVersion(3L);
        daoInputSchema.setDeleted(true);

        // execute
        UploadSchema daoOutputSchema = dao.createSchemaRevision(daoInputSchema);

        // verify mapper call
        ArgumentCaptor<DynamoUploadSchema> mapperInputSchemaCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mapper).save(mapperInputSchemaCaptor.capture(), any(DynamoDBSaveExpression.class));

        DynamoUploadSchema mapperInputSchema = mapperInputSchemaCaptor.getValue();
        assertNull(mapperInputSchema.getVersion());
        assertFalse(mapperInputSchema.isDeleted());

        // schema returned by dao is the same one that was sent to the mapper
        assertSame(daoOutputSchema, mapperInputSchema);
    }

    @Test
    public void delete() {
        // mock mapper to return an empty list of failures (all success)
        List<UploadSchema> schemaListToDelete = ImmutableList.of(new DynamoUploadSchema());
        when(mapper.batchDelete(schemaListToDelete)).thenReturn(ImmutableList.of());

        // execute
        dao.deleteUploadSchemas(schemaListToDelete);

        // verify schema was passed to mapper
        verify(mapper).batchSave(schemaListToDelete);
        assertTrue(schemaListToDelete.stream().allMatch(schema -> schema.isDeleted()));
    }

    @Test
    public void deletePermanently() {
        // mock mapper to return an empty list of failures (all success)
        List<UploadSchema> schemaListToDelete = ImmutableList.of(new DynamoUploadSchema());
        when(mapper.batchDelete(schemaListToDelete)).thenReturn(ImmutableList.of());

        // execute
        dao.deleteUploadSchemasPermanently(schemaListToDelete);

        // verify schema was passed to mapper
        verify(mapper).batchDelete(schemaListToDelete);
    }
    
    @Test
    public void allSchemasAllRevisionsExcludeDeleted() {
        // spy index helper
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(new DynamoUploadSchema());
        ArgumentCaptor<DynamoUploadSchema> indexHashKeyCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        doReturn(mapperOutputSchemaList).when(dao).indexHelper(eq(DynamoUploadSchemaDao.STUDY_ID_INDEX_NAME),
                indexHashKeyCaptor.capture(), eq(false));

        // execute
        List<UploadSchema> daoOutputSchemaList = dao.getAllUploadSchemasAllRevisions(API_APP_ID, false);

        // validate index hash key
        DynamoUploadSchema indexHashKey = indexHashKeyCaptor.getValue();
        assertEquals(indexHashKey.getStudyId(), API_APP_ID);

        // Verify DAO output
        assertSame(daoOutputSchemaList, mapperOutputSchemaList);
    }

    @Test
    public void allSchemasAllRevisionsIncludeDeleted() {
        // spy index helper
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(new DynamoUploadSchema());
        ArgumentCaptor<DynamoUploadSchema> indexHashKeyCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        doReturn(mapperOutputSchemaList).when(dao).indexHelper(eq(DynamoUploadSchemaDao.STUDY_ID_INDEX_NAME),
                indexHashKeyCaptor.capture(), eq(true));

        // execute
        List<UploadSchema> daoOutputSchemaList = dao.getAllUploadSchemasAllRevisions(API_APP_ID, true);

        // validate index hash key
        DynamoUploadSchema indexHashKey = indexHashKeyCaptor.getValue();
        assertEquals(indexHashKey.getStudyId(), API_APP_ID);

        // Verify DAO output
        assertSame(daoOutputSchemaList, mapperOutputSchemaList);
    }

    @Test
    public void deleteFlagFiltersIndexHelper() {
        DynamoUploadSchema undeletedSchema = new DynamoUploadSchema();
        
        DynamoUploadSchema deletedSchema = new DynamoUploadSchema();
        deletedSchema.setDeleted(true);
        
        Map<String,List<Object>> map = Maps.newHashMap();
        map.put("A", ImmutableList.of(undeletedSchema));
        map.put("B", ImmutableList.of(deletedSchema));
        
        PaginatedQueryList<DynamoUploadSchema> pql = mock(PaginatedQueryList.class);
        when(mapper.query(eq(DynamoUploadSchema.class), any())).thenReturn(pql);
        
        when(mapper.batchLoad(pql)).thenReturn(map);
        
        List<UploadSchema> results1 = dao.indexHelper("indexName", new DynamoUploadSchema(), false);
        assertEquals(results1.size(), 1);
        assertEquals(results1.get(0), undeletedSchema);
        
        List<UploadSchema> results2 = dao.indexHelper("indexName", new DynamoUploadSchema(), true);
        assertEquals(results2.size(), 2);
        assertEquals(results2.get(0), undeletedSchema);
        assertEquals(results2.get(1), deletedSchema);
    }

    @Test
    public void getSchemaAllRevisionsIncludeDeleted() {
        // spy query
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(new DynamoUploadSchema());
        ArgumentCaptor<DynamoDBQueryExpression> mapperQueryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        doReturn(mapperOutputSchemaList).when(dao).queryHelper(mapperQueryCaptor.capture());

        // execute
        List<UploadSchema> daoOutputSchemaList = dao.getUploadSchemaAllRevisionsById(API_APP_ID,
                SCHEMA_ID, true);

        // validate query
        DynamoDBQueryExpression<DynamoUploadSchema> mapperQuery = mapperQueryCaptor.getValue();
        assertFalse(mapperQuery.isScanIndexForward());
        assertEquals(mapperQuery.getHashKeyValues().getStudyId(), API_APP_ID);
        assertEquals(mapperQuery.getHashKeyValues().getSchemaId(), SCHEMA_ID);
        assertNull(mapperQuery.getQueryFilter());

        // Verify DAO output is same as mapper output. We use equals because they are different instances, but contain
        // the same objects.
        assertEquals(daoOutputSchemaList, mapperOutputSchemaList);
    }
    
    @Test
    public void getSchemaAllRevisionsExcludeDeleted() {
        // spy query
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(new DynamoUploadSchema());
        ArgumentCaptor<DynamoDBQueryExpression> mapperQueryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        doReturn(mapperOutputSchemaList).when(dao).queryHelper(mapperQueryCaptor.capture());

        // execute
        List<UploadSchema> daoOutputSchemaList = dao.getUploadSchemaAllRevisionsById(API_APP_ID,
                SCHEMA_ID, false);

        // validate query
        DynamoDBQueryExpression<DynamoUploadSchema> mapperQuery = mapperQueryCaptor.getValue();
        assertFalse(mapperQuery.isScanIndexForward());
        assertEquals(mapperQuery.getHashKeyValues().getStudyId(), API_APP_ID);
        assertEquals(mapperQuery.getHashKeyValues().getSchemaId(), SCHEMA_ID);
        assertEquals(mapperQuery.getQueryFilter().toString(),
                "{deleted={AttributeValueList: [{N: 1,}],ComparisonOperator: NE}}");

        // Verify DAO output is same as mapper output. We use equals because they are different instances, but contain
        // the same objects.
        assertEquals(daoOutputSchemaList, mapperOutputSchemaList);
    }

    @Test
    public void getSchemaByIdAndRev() {
        // mock DDB mapper
        DynamoUploadSchema mapperOutputSchema = new DynamoUploadSchema();
        ArgumentCaptor<DynamoUploadSchema> mapperInputSchemaCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        when(mapper.load(mapperInputSchemaCaptor.capture())).thenReturn(mapperOutputSchema);

        // set up test dao and execute
        UploadSchema daoOutputSchema = dao.getUploadSchemaByIdAndRevision(API_APP_ID, SCHEMA_ID,
                SCHEMA_REV);
        assertSame(daoOutputSchema, mapperOutputSchema);

        // validate intermediate args
        DynamoUploadSchema mapperInputSchema = mapperInputSchemaCaptor.getValue();
        assertEquals(mapperInputSchema.getStudyId(), API_APP_ID);
        assertEquals(mapperInputSchema.getSchemaId(), SCHEMA_ID);
        assertEquals(mapperInputSchema.getRevision(), SCHEMA_REV);
    }

    @Test
    public void getLatestById() {
        // mock DDB mapper
        DynamoUploadSchema mapperOutputSchema = new DynamoUploadSchema();
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of(mapperOutputSchema);
        QueryResultPage<DynamoUploadSchema> queryResultPage = new QueryResultPage<>();
        queryResultPage.setResults(mapperOutputSchemaList);

        ArgumentCaptor<DynamoDBQueryExpression> mapperQueryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        when(mapper.queryPage(eq(DynamoUploadSchema.class), mapperQueryCaptor.capture())).thenReturn(queryResultPage);

        // execute
        UploadSchema daoOutputSchema = dao.getUploadSchemaLatestRevisionById(API_APP_ID, SCHEMA_ID);

        // validate query
        DynamoDBQueryExpression<DynamoUploadSchema> mapperQuery = mapperQueryCaptor.getValue();
        assertFalse(mapperQuery.isScanIndexForward());
        assertEquals(mapperQuery.getLimit().intValue(), 1);
        assertEquals(mapperQuery.getHashKeyValues().getStudyId(), API_APP_ID);
        assertEquals(mapperQuery.getHashKeyValues().getSchemaId(), SCHEMA_ID);

        // Verify DAO output
        assertSame(daoOutputSchema, mapperOutputSchema);
    }

    @Test
    public void getLatestByIdNoResult() {
        // mock DDB mapper
        List<DynamoUploadSchema> mapperOutputSchemaList = ImmutableList.of();
        QueryResultPage<DynamoUploadSchema> queryResultPage = new QueryResultPage<>();
        queryResultPage.setResults(mapperOutputSchemaList);

        when(mapper.queryPage(eq(DynamoUploadSchema.class), any())).thenReturn(queryResultPage);

        // execute and validate result is null
        UploadSchema daoOutputSchema = dao.getUploadSchemaLatestRevisionById(API_APP_ID, SCHEMA_ID);
        assertNull(daoOutputSchema);

        // query is already validated in getLatestById(). Don't bother checking again.
    }

    @Test
    public void update() {
        // We can mark something deleted (or not) on an update
        daoInputSchema.setDeleted(true);
        
        // execute (no additional setup required)
        UploadSchema daoOutputSchema = dao.updateSchemaRevision(daoInputSchema);

        // verify mapper call
        ArgumentCaptor<DynamoUploadSchema> mapperInputSchemaCaptor = ArgumentCaptor.forClass(DynamoUploadSchema.class);
        verify(mapper).save(mapperInputSchemaCaptor.capture());

        // schema returned by dao is the same one that was sent to the mapper
        DynamoUploadSchema mapperInputSchema = mapperInputSchemaCaptor.getValue();
        assertSame(mapperInputSchema, daoOutputSchema);
        assertTrue(mapperInputSchema.isDeleted());
    }
}
