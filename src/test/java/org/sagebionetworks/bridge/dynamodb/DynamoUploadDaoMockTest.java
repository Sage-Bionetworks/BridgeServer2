package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.upload.UploadCompletionClient.APP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class DynamoUploadDaoMockTest {

    private static String UPLOAD_ID = "uploadId";
    private static String UPLOAD_ID_2 = "uploadId2";
    private static String UPLOAD_ID_3 = "uploadId3";
    private static String UPLOAD_ID_4 = "uploadId4";

    @Mock
    private DynamoDBMapper mockMapper;

    @Mock
    private DynamoIndexHelper mockIndexHelper;

    @Mock
    private Index mockIndex;

    @Mock
    private ItemCollection<QueryOutcome> mockQueryOutcome;

    @Mock
    private QueryOutcome lastQueryOutcome;

    @Mock
    private QueryResult mockQueryResult;

    @Mock
    private IteratorSupport<Item, QueryOutcome> mockIterSupport;

    @Mock
    private DynamoUpload2 upload1;

    @Mock
    private DynamoUpload2 upload2;

    @Mock
    private DynamoUpload2 upload3;

    @Mock
    private DynamoUpload2 upload4;

    @Mock
    QueryResultPage<DynamoUpload2> queryPage1;

    @Mock
    QueryResultPage<DynamoUpload2> queryPage2;

    @Mock
    HealthCodeDao healthCodeDao;

    @Captor
    private ArgumentCaptor<QuerySpec> querySpecCaptor;

    @Captor
    private ArgumentCaptor<DynamoUpload2> uploadCaptor;

    @Captor
    private ArgumentCaptor<List<Upload>> uploadListCaptor;

    private DynamoUploadDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        dao = new DynamoUploadDao();
        dao.setDdbMapper(mockMapper);
        dao.setHealthCodeDao(healthCodeDao);
        dao.setHealthCodeRequestedOnIndex(mockIndexHelper);
    }

    @Test
    public void createUpload() {
        // execute
        UploadRequest req = createUploadRequest();
        Upload upload = dao.createUpload(req, TEST_APP_ID, "fakeHealthCode", null);

        // Validate that our mock DDB mapper was called.
        verify(mockMapper).save(uploadCaptor.capture());

        DynamoUpload2 capturedUpload = uploadCaptor.getValue();

        // Validate that our DDB upload object matches our upload request, and that the upload ID matches.
        assertEquals(capturedUpload.getUploadId(), upload.getUploadId());
        assertNull(capturedUpload.getDuplicateUploadId());
        assertEquals(capturedUpload.getAppId(), TEST_APP_ID);
        assertTrue(capturedUpload.getRequestedOn() > 0);
        assertEquals(capturedUpload.getStatus(), UploadStatus.REQUESTED);
        assertEquals(capturedUpload.getContentLength(), req.getContentLength());
        assertEquals(capturedUpload.getContentMd5(), req.getContentMd5());
        assertEquals(capturedUpload.getContentType(), req.getContentType());
        assertEquals(capturedUpload.getFilename(), req.getName());
    }

    @Test
    public void createUploadDupe() {
        // execute
        UploadRequest req = createUploadRequest();
        dao.createUpload(req, TEST_APP_ID, "fakeHealthCode", "original-upload-id");

        // Validate that our mock DDB mapper was called.
        verify(mockMapper).save(uploadCaptor.capture());

        DynamoUpload2 capturedUpload = uploadCaptor.getValue();

        // Validate key values (appId, requestedOn) and values from the dupe code path.
        // Everything else is tested in the previous test
        assertEquals(capturedUpload.getDuplicateUploadId(), "original-upload-id");
        assertEquals(capturedUpload.getAppId(), TEST_APP_ID);
        assertTrue(capturedUpload.getRequestedOn() > 0);
        assertEquals(capturedUpload.getStatus(), UploadStatus.DUPLICATE);
    }

    @Test
    public void getUpload() {
        // mock DDB mapper
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setAppId(TEST_APP_ID);
        when(mockMapper.load(uploadCaptor.capture())).thenReturn(upload);

        // execute
        Upload retVal = dao.getUpload("test-get-upload");
        assertSame(retVal, upload);

        // validate we passed in the expected key
        assertEquals(uploadCaptor.getValue().getUploadId(), "test-get-upload");
    }

    @Test
    public void getUploadWithoutStudyId() {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode("healthCode");
        when(mockMapper.load(uploadCaptor.capture())).thenReturn(upload);

        when(healthCodeDao.getStudyIdentifier(upload.getHealthCode())).thenReturn(TEST_APP_ID);

        Upload retVal = dao.getUpload("test-get-upload");
        assertEquals(retVal.getAppId(), TEST_APP_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getUploadWithoutStudyIdAndNoHealthCodeRecord() {
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setHealthCode("healthCode");
        when(mockMapper.load(uploadCaptor.capture())).thenReturn(upload);

        when(healthCodeDao.getStudyIdentifier(upload.getHealthCode())).thenReturn(null);

        dao.getUpload("test-get-upload");
    }

    @Test
    public void getUploadNotFound() {
        when(mockMapper.load(uploadCaptor.capture())).thenReturn(null);

        // execute
        Exception thrown = null;
        try {
            dao.getUpload("test-get-404");
            fail();
        } catch (NotFoundException ex) {
            thrown = ex;
        }
        assertNotNull(thrown);

        // validate we passed in the expected key
        assertEquals(uploadCaptor.getValue().getUploadId(), "test-get-404");
    }

    @Test
    public void uploadComplete() {
        // execute
        dao.uploadComplete(UploadCompletionClient.APP, new DynamoUpload2());

        // Verify our mock. We add status=VALIDATION_IN_PROGRESS and uploadDate on save, so only check for those
        // properties.
        verify(mockMapper).save(uploadCaptor.capture());
        assertEquals(uploadCaptor.getValue().getStatus(), UploadStatus.VALIDATION_IN_PROGRESS);
        assertEquals(uploadCaptor.getValue().getCompletedBy(), UploadCompletionClient.APP);
        assertTrue(uploadCaptor.getValue().getCompletedOn() > 0);

        // There is a slim chance that this will fail if it runs just after midnight.
        assertEquals(uploadCaptor.getValue().getUploadDate(), LocalDate.now(DateTimeZone.forID("America/Los_Angeles")));
    }

    @Test
    public void writeValidationStatus() {
        // create input
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");
        upload2.setValidationMessageList(Collections.<String>emptyList());

        // execute
        dao.writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of("wrote new"), null);

        // Verify our mock. We set the status and append messages.
        verify(mockMapper).save(uploadCaptor.capture());
        assertEquals(uploadCaptor.getValue().getStatus(), UploadStatus.SUCCEEDED);
        assertNull(uploadCaptor.getValue().getRecordId());

        List<String> messageList = uploadCaptor.getValue().getValidationMessageList();
        assertEquals(messageList.size(), 1);
        assertEquals(messageList.get(0), "wrote new");
    }

    @Test
    public void writeValidationStatusOptionalValues() {
        // create input
        DynamoUpload2 upload2 = new DynamoUpload2();
        upload2.setUploadId("test-upload");
        upload2.setValidationMessageList(ImmutableList.of("pre-existing message"));

        // execute
        dao.writeValidationStatus(upload2, UploadStatus.SUCCEEDED, ImmutableList.of("appended this message"),
                "test-record-id");

        // Verify our mock. We set the status and append messages.
        verify(mockMapper).save(uploadCaptor.capture());
        assertEquals(uploadCaptor.getValue().getStatus(), UploadStatus.SUCCEEDED);
        assertEquals(uploadCaptor.getValue().getRecordId(), "test-record-id");

        List<String> messageList = uploadCaptor.getValue().getValidationMessageList();
        assertEquals(messageList.size(), 2);
        assertEquals(messageList.get(0), "pre-existing message");
        assertEquals(messageList.get(1), "appended this message");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getUploads() {
        String healthCode = "abc";
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();
        int pageSize = 50;

        // The mock items are not in order, the later one is returned before the earlier one,
        // and the order should be reversed by sorting.
        Item mockItem1 = new Item().withLong("requestedOn", 30000).with("uploadId", UPLOAD_ID);
        Item mockItem2 = new Item().withLong("requestedOn", 10000).with("uploadId", UPLOAD_ID_2);

        when(upload1.getRequestedOn()).thenReturn(30000L);
        when(upload2.getRequestedOn()).thenReturn(10000L);

        when(mockIndexHelper.query(any(QuerySpec.class))).thenReturn(lastQueryOutcome);
        when(lastQueryOutcome.getItems()).thenReturn(Lists.newArrayList(mockItem1, mockItem2));

        Map<String, List<Object>> batchLoadMap = new ImmutableMap.Builder<String, List<Object>>()
                .put(UPLOAD_ID, Lists.newArrayList(upload1)).put(UPLOAD_ID_2, Lists.newArrayList(upload2)).build();

        when(mockMapper.batchLoad(any(List.class))).thenReturn(batchLoadMap);

        ForwardCursorPagedResourceList<Upload> page = dao.getUploads(healthCode, startTime, endTime, pageSize, null);

        verify(mockIndexHelper).query(querySpecCaptor.capture());
        QuerySpec mockSpec = querySpecCaptor.getValue();
        assertEquals(mockSpec.getMaxPageSize(), new Integer(51));
        assertEquals(mockSpec.getHashKey().getValue(), healthCode);

        verify(mockMapper).batchLoad(uploadListCaptor.capture());
        List<Upload> uploads = uploadListCaptor.getValue();
        assertEquals(uploads.size(), 2);

        // These have been sorted.
        assertEquals(page.getItems().size(), 2);
        assertEquals(page.getItems().get(0).getRequestedOn(), 10000);
        assertEquals(page.getItems().get(1).getRequestedOn(), 30000);

        // All parameters were returned. No paging in this test
        assertEquals(page.getRequestParams().get("pageSize"), (Integer) pageSize);
        assertEquals(page.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(page.getRequestParams().get("endTime"), endTime.toString());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsMinPageEnforced() {
        String healthCode = "abc";
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();

        dao.getUploads(healthCode, startTime, endTime, 0, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getUploadsMaxPageEnforced() {
        String healthCode = "abc";
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();

        dao.getUploads(healthCode, startTime, endTime, 200, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getUploadsPagingWorks() {
        String healthCode = "abc";
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();
        int pageSize = 2;

        Item mockItem1 = new Item().withLong("requestedOn", 10000).with("uploadId", UPLOAD_ID);
        Item mockItem2 = new Item().withLong("requestedOn", 20000).with("uploadId", UPLOAD_ID_2);
        Item mockItem3 = new Item().withLong("requestedOn", 30000).with("uploadId", UPLOAD_ID_3);
        Item mockItem4 = new Item().withLong("requestedOn", 40000).with("uploadId", UPLOAD_ID_4);

        when(upload1.getRequestedOn()).thenReturn(10000L);
        when(upload2.getRequestedOn()).thenReturn(20000L);
        when(upload3.getRequestedOn()).thenReturn(30000L);
        when(upload4.getRequestedOn()).thenReturn(40000L);

        when(mockIndexHelper.query(any(QuerySpec.class))).thenReturn(lastQueryOutcome);

        when(lastQueryOutcome.getItems()).thenReturn(Lists.newArrayList(mockItem1, mockItem2),
                Lists.newArrayList(mockItem3, mockItem4));

        Map<String, List<Object>> batchLoadMap1 = new ImmutableMap.Builder<String, List<Object>>()
                .put(UPLOAD_ID, Lists.newArrayList(upload1)).put(UPLOAD_ID_2, Lists.newArrayList(upload2))
                .put(UPLOAD_ID_3, Lists.newArrayList(upload3)).build();

        Map<String, List<Object>> batchLoadMap2 = new ImmutableMap.Builder<String, List<Object>>()
                .put(UPLOAD_ID_3, Lists.newArrayList(upload3)).put(UPLOAD_ID_4, Lists.newArrayList(upload4)).build();

        when(mockMapper.batchLoad(any(List.class))).thenReturn(batchLoadMap1, batchLoadMap2);

        ForwardCursorPagedResourceList<Upload> page1 = dao.getUploads(healthCode, startTime, endTime, pageSize, null);
        assertEquals(page1.getNextPageOffsetKey(), "30000");
        assertNull(page1.getRequestParams().get("offsetKey"));
        assertEquals(page1.getRequestParams().get("pageSize"), pageSize);
        assertEquals(page1.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(page1.getRequestParams().get("endTime"), endTime.toString());

        ForwardCursorPagedResourceList<Upload> page2 = dao.getUploads(healthCode, startTime, endTime, pageSize,
                page1.getNextPageOffsetKey());
        assertNull(page2.getNextPageOffsetKey());
        assertEquals(page2.getRequestParams().get("offsetKey"), "30000");
        assertEquals(page2.getRequestParams().get("pageSize"), pageSize);
        assertEquals(page2.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(page2.getRequestParams().get("endTime"), endTime.toString());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getStudyUploadsPagingWorks() throws Exception {
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();
        int pageSize = 2;

        when(upload3.getRequestedOn()).thenReturn(30000L);
        when(upload3.getUploadId()).thenReturn(UPLOAD_ID_3);

        when(mockMapper.load(DynamoUpload2.class, upload3.getUploadId())).thenReturn(upload3);

        when(mockMapper.queryPage(eq(DynamoUpload2.class), any(DynamoDBQueryExpression.class))).thenReturn(queryPage1,
                queryPage2);

        when(queryPage1.getResults()).thenReturn(Lists.newArrayList(upload1, upload2));

        Map<String, AttributeValue> lastKey1 = new ImmutableMap.Builder<String, AttributeValue>()
                .put(UPLOAD_ID, new AttributeValue().withS(upload3.getUploadId())).build();
        when(queryPage1.getLastEvaluatedKey()).thenReturn(lastKey1);

        Map<String, AttributeValue> lastKey2 = new ImmutableMap.Builder<String, AttributeValue>()
                .put(UPLOAD_ID, new AttributeValue().withS(null)).build();
        when(queryPage2.getLastEvaluatedKey()).thenReturn(lastKey2);

        when(queryPage2.getResults()).thenReturn(Lists.newArrayList(upload3, upload4));

        Map<String, List<Object>> batchLoadMap1 = new ImmutableMap.Builder<String, List<Object>>()
                .put(UPLOAD_ID, Lists.newArrayList(upload1)).put(UPLOAD_ID_2, Lists.newArrayList(upload2))
                .put(UPLOAD_ID_3, Lists.newArrayList(upload3)).build();

        Map<String, List<Object>> batchLoadMap2 = new ImmutableMap.Builder<String, List<Object>>()
                .put(UPLOAD_ID_3, Lists.newArrayList(upload3)).put(UPLOAD_ID_4, Lists.newArrayList(upload4)).build();

        when(mockMapper.batchLoad(any(List.class))).thenReturn(batchLoadMap1, batchLoadMap2);

        ForwardCursorPagedResourceList<Upload> page1 = dao.getAppUploads(TEST_APP_ID, startTime, endTime, pageSize, null);
        assertEquals(page1.getNextPageOffsetKey(), "uploadId3");
        assertNull(page1.getRequestParams().get("offsetKey"));
        assertEquals(page1.getRequestParams().get("pageSize"), pageSize);
        assertEquals(page1.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(page1.getRequestParams().get("endTime"), endTime.toString());

        ForwardCursorPagedResourceList<Upload> page2 = dao.getAppUploads(TEST_APP_ID, startTime, endTime, pageSize,
                page1.getNextPageOffsetKey());
        assertNull(page2.getNextPageOffsetKey());
        assertEquals(page2.getRequestParams().get("offsetKey"), "uploadId3");
        assertEquals(page1.getRequestParams().get("pageSize"), pageSize);
        assertEquals(page1.getRequestParams().get("startTime"), startTime.toString());
        assertEquals(page1.getRequestParams().get("endTime"), endTime.toString());
    }

    @Test
    public void getStudyUploadsBadOffsetKey() {
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();
        int pageSize = 2;

        try {
            dao.getAppUploads(TEST_APP_ID, startTime, endTime, pageSize, "bad-key");
            fail("Should have thrown an exception");
        } catch (BadRequestException e) {
            assertEquals(e.getMessage(), "Invalid offsetKey: bad-key");
        }
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyUploadsMinPageSizeEnforced() {
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();

        dao.getAppUploads(TEST_APP_ID, startTime, endTime, -1, null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyUploadsMaxPageSizeEnforced() {
        DateTime startTime = DateTime.now().minusDays(4);
        DateTime endTime = DateTime.now();

        dao.getAppUploads(TEST_APP_ID, startTime, endTime, 101, null);
    }

    @Test
    public void deleteUploadsForHealthCode() {
        List<DynamoUpload2> uploads = ImmutableList.of(new DynamoUpload2());
        when(mockIndexHelper.queryKeys(DynamoUpload2.class, "healthCode", "oneHealthCode", null)).thenReturn(uploads);

        dao.deleteUploadsForHealthCode("oneHealthCode");

        verify(mockIndexHelper).queryKeys(DynamoUpload2.class, "healthCode", "oneHealthCode", null);
        verify(mockMapper).batchDelete(uploads);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deleteUploadsForHealthCodeFailsSilently() {
        when(mockIndexHelper.queryKeys(DynamoUpload2.class, "healthCode", "oneHealthCode", null))
                .thenReturn(ImmutableList.of());

        dao.deleteUploadsForHealthCode("oneHealthCode");
        
        verify(mockIndexHelper).queryKeys(DynamoUpload2.class, "healthCode", "oneHealthCode", null);
        verify(mockMapper, never()).batchDelete(any(List.class));
    }

    @Test(expectedExceptions = ConcurrentModificationException.class)
    public void uploadCompleteConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());

        Upload upload = new DynamoUpload2();

        dao.uploadComplete(APP, upload);
    }

    private static UploadRequest createUploadRequest() {
        final String text = "test upload dao";
        return new UploadRequest.Builder().withName("test-upload-dao-filename").withContentType("text/plain")
                .withContentLength((long) text.getBytes().length)
                .withContentMd5(Base64.encodeBase64String(DigestUtils.md5(text))).build();
    }
}
