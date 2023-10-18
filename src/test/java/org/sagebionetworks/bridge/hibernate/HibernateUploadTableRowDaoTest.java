package org.sagebionetworks.bridge.hibernate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

public class HibernateUploadTableRowDaoTest {
    private static final String RECORD_ID = "test-record";
    private static final DateTime START_TIME = TestConstants.TIMESTAMP;
    private static final DateTime END_TIME = TestConstants.TIMESTAMP.plusHours(1);

    @Mock
    private HibernateHelper mockHibernateHelper;

    @InjectMocks
    private HibernateUploadTableRowDao dao;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void deleteUploadTableRow() {
        // Execute.
        dao.deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);

        // Verify call to hibernate.
        ArgumentCaptor<HibernateUploadTableRowId> idCaptor = ArgumentCaptor.forClass(HibernateUploadTableRowId.class);
        verify(mockHibernateHelper).deleteById(eq(HibernateUploadTableRow.class), idCaptor.capture());
        HibernateUploadTableRowId id = idCaptor.getValue();
        assertEquals(id.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(id.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(id.getRecordId(), RECORD_ID);
    }

    @Test
    public void getUploadTableRow() {
        // Set up mock.
        HibernateUploadTableRow row = new HibernateUploadTableRow();
        when(mockHibernateHelper.getById(eq(HibernateUploadTableRow.class), any())).thenReturn(row);

        // Execute.
        Optional<UploadTableRow> result = dao.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
        assertTrue(result.isPresent());
        assertSame(result.get(), row);

        // Verify call to hibernate.
        ArgumentCaptor<HibernateUploadTableRowId> idCaptor = ArgumentCaptor.forClass(HibernateUploadTableRowId.class);
        verify(mockHibernateHelper).getById(eq(HibernateUploadTableRow.class), idCaptor.capture());
        HibernateUploadTableRowId id = idCaptor.getValue();
        assertEquals(id.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(id.getStudyId(), TestConstants.TEST_STUDY_ID);
        assertEquals(id.getRecordId(), RECORD_ID);
    }

    @Test
    public void getUploadTableRow_nullRow() {
        // Set up mock.
        when(mockHibernateHelper.getById(eq(HibernateUploadTableRow.class), any())).thenReturn(null);

        // Execute.
        Optional<UploadTableRow> result = dao.getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);
        assertFalse(result.isPresent());
    }

    @Test
    public void queryUploadTableRows() {
        // Set up mock.
        HibernateUploadTableRow row = new HibernateUploadTableRow();
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(1);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(row));

        // Execute.
        UploadTableRowQuery query = new UploadTableRowQuery();
        query.setAppId(TestConstants.TEST_APP_ID);
        query.setStudyId(TestConstants.TEST_STUDY_ID);

        PagedResourceList<UploadTableRow> resourceList = dao.queryUploadTableRows(query);
        assertEquals(resourceList.getItems().size(), 1);
        assertSame(resourceList.getItems().get(0), row);
        assertEquals(resourceList.getTotal().intValue(), 1);

        Map<String, Object> queryParamMap = resourceList.getRequestParams();
        assertNull(queryParamMap.get("assessmentId"));
        assertNull(queryParamMap.get("assessmentRevision"));
        assertNull(queryParamMap.get("startTime"));
        assertNull(queryParamMap.get("endTime"));
        assertFalse((boolean) queryParamMap.get("includeTestData"));
        assertEquals(queryParamMap.get("start"), 0);
        assertEquals(queryParamMap.get("pageSize"), BridgeConstants.API_DEFAULT_PAGE_SIZE);

        // Verify call to hibernate.
        String expectedQuery = "FROM HibernateUploadTableRow WHERE appId = :appId AND studyId = :studyId AND testData = 0";
        String expectedCountQuery = "SELECT COUNT(DISTINCT recordId) " + expectedQuery;
        Map<String, Object> paramMap = ImmutableMap.<String, Object>builder()
                .put("appId", TestConstants.TEST_APP_ID)
                .put("studyId", TestConstants.TEST_STUDY_ID).build();

        verify(mockHibernateHelper).queryCount(expectedCountQuery, paramMap);
        verify(mockHibernateHelper).queryGet(expectedQuery, paramMap, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                HibernateUploadTableRow.class);
    }


    @Test
    public void queryUploadTableRows_WithOptionalParams() {
        // Set up mock.
        HibernateUploadTableRow row = new HibernateUploadTableRow();
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(1);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(row));

        // Execute.
        UploadTableRowQuery query = new UploadTableRowQuery();
        query.setAppId(TestConstants.TEST_APP_ID);
        query.setStudyId(TestConstants.TEST_STUDY_ID);
        query.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        query.setStartTime(START_TIME);
        query.setEndTime(END_TIME);
        query.setIncludeTestData(true);
        query.setStart(10);
        query.setPageSize(20);

        PagedResourceList<UploadTableRow> resourceList = dao.queryUploadTableRows(query);
        assertEquals(resourceList.getItems().size(), 1);
        assertSame(resourceList.getItems().get(0), row);
        assertEquals(resourceList.getTotal().intValue(), 1);

        Map<String, Object> queryParamMap = resourceList.getRequestParams();
        assertEquals(queryParamMap.get("assessmentGuid"), TestConstants.ASSESSMENT_1_GUID);
        assertEquals(queryParamMap.get("startTime"), START_TIME.toString());
        assertEquals(queryParamMap.get("endTime"), END_TIME.toString());
        assertTrue((boolean) queryParamMap.get("includeTestData"));
        assertEquals(queryParamMap.get("start"), 10);
        assertEquals(queryParamMap.get("pageSize"), 20);

        // Verify call to hibernate.
        String expectedQuery = "FROM HibernateUploadTableRow WHERE appId = :appId AND studyId = :studyId "
                + "AND assessmentGuid = :assessmentGuid AND createdOn >= :startDate AND createdOn < :endDate";
        String expectedCountQuery = "SELECT COUNT(DISTINCT recordId) " + expectedQuery;
        Map<String, Object> paramMap = ImmutableMap.<String, Object>builder()
                .put("appId", TestConstants.TEST_APP_ID)
                .put("studyId", TestConstants.TEST_STUDY_ID)
                .put("assessmentGuid", TestConstants.ASSESSMENT_1_GUID)
                .put("startDate", START_TIME)
                .put("endDate", END_TIME).build();

        verify(mockHibernateHelper).queryCount(expectedCountQuery, paramMap);
        verify(mockHibernateHelper).queryGet(expectedQuery, paramMap, 10, 20,
                HibernateUploadTableRow.class);
    }

    @Test
    public void saveUploadTableRow() {
        // Execute.
        UploadTableRow row = new HibernateUploadTableRow();
        dao.saveUploadTableRow(row);

        // Verify call to hibernate.
        verify(mockHibernateHelper).saveOrUpdate(row);
    }
}
