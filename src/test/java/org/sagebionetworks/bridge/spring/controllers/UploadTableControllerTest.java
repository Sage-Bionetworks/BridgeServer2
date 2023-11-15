package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

import java.util.List;
import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.UploadTableService;
import org.sagebionetworks.bridge.upload.UploadTableJob;
import org.sagebionetworks.bridge.upload.UploadTableJobGuidHolder;
import org.sagebionetworks.bridge.upload.UploadTableJobResult;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

public class UploadTableControllerTest {
    private static final String JOB_GUID = "test-job-guid";
    private static final String RECORD_ID = "test-record";
    private static final String S3_KEY = "dummy-s3-key";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private UploadTableService mockSvc;

    @InjectMocks
    @Spy
    private UploadTableController controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock request.
        doReturn(mockRequest).when(controller).request();

        // Mock session.
        UserSession session = new UserSession();
        session.setAppId(TestConstants.TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(UploadTableController.class);
        assertGet(UploadTableController.class, "getUploadTableJobResult");
        assertGet(UploadTableController.class, "listUploadTableJobsForStudy");
        assertPost(UploadTableController.class, "requestUploadTableForStudy");
        assertGet(UploadTableController.class, "getUploadTableJobForWorker");
        assertPost(UploadTableController.class, "updateUploadTableJobForWorker");
        assertDelete(UploadTableController.class, "deleteUploadTableRowForSuperadmin");
        assertGet(UploadTableController.class, "getUploadTableRowForSuperadmin");
        assertPost(UploadTableController.class, "queryUploadTableRowsForWorker");
        assertPost(UploadTableController.class, "saveUploadTableRowForWorker");
    }

    @Test
    public void getUploadTableJobResult() {
        // Mock service.
        UploadTableJobResult svcResult = new UploadTableJobResult();
        doReturn(svcResult).when(mockSvc).getUploadTableJobResult(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, JOB_GUID);

        // Execute and verify.
        UploadTableJobResult ctrlResult = controller.getUploadTableJobResult(TestConstants.TEST_STUDY_ID, JOB_GUID);
        assertSame(ctrlResult, svcResult);
        verify(mockSvc).getUploadTableJobResult(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test
    public void listUploadTableJobsForStudy_DefaultParams() {
        // Mock service.
        PagedResourceList<UploadTableJob> svcResult = new PagedResourceList<>(
                ImmutableList.of(UploadTableJob.create()), 1);
        doReturn(svcResult).when(mockSvc).listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 0, BridgeConstants.API_DEFAULT_PAGE_SIZE);

        // Execute and verify.
        PagedResourceList<UploadTableJob> ctrlResult = controller.listUploadTableJobsForStudy(
                TestConstants.TEST_STUDY_ID, null, null);
        assertSame(ctrlResult, svcResult);
        verify(mockSvc).listUploadTableJobsForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, 0,
                BridgeConstants.API_DEFAULT_PAGE_SIZE);
    }

    @Test
    public void listUploadTableJobsForStudy_WithOptionalParams() {
        // Mock service.
        PagedResourceList<UploadTableJob> svcResult = new PagedResourceList<>(
                ImmutableList.of(UploadTableJob.create()), 1);
        doReturn(svcResult).when(mockSvc).listUploadTableJobsForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, 10, 20);

        // Execute and verify.
        PagedResourceList<UploadTableJob> ctrlResult = controller.listUploadTableJobsForStudy(
                TestConstants.TEST_STUDY_ID, "10", "20");
        assertSame(ctrlResult, svcResult);
        verify(mockSvc).listUploadTableJobsForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, 10,
                20);
    }

    @Test
    public void requestUploadTableForStudy() {
        // Mock service.
        UploadTableJobGuidHolder svcResult = new UploadTableJobGuidHolder(JOB_GUID);
        doReturn(svcResult).when(mockSvc).requestUploadTableForStudy(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);

        // Execute and verify.
        UploadTableJobGuidHolder ctrlResult = controller.requestUploadTableForStudy(TestConstants.TEST_STUDY_ID);
        assertSame(ctrlResult, svcResult);
        verify(mockSvc).requestUploadTableForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID);
    }

    @Test
    public void getUploadTableJobForWorker() {
        // Mock service.
        UploadTableJob svcResult = UploadTableJob.create();
        doReturn(svcResult).when(mockSvc).getUploadTableJobForWorker(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, JOB_GUID);

        // Execute and verify.
        UploadTableJob ctrlResult = controller.getUploadTableJobForWorker(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, JOB_GUID);
        assertSame(ctrlResult, svcResult);
        verify(mockSvc).getUploadTableJobForWorker(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, JOB_GUID);
    }

    @Test
    public void updateUploadTableJobForWorker() throws Exception {
        // Mock request. Set a field so we can verify it later.
        UploadTableJob job = UploadTableJob.create();
        job.setS3Key(S3_KEY);
        mockRequestBody(mockRequest, job);

        // Execute and verify.
        StatusMessage result = controller.updateUploadTableJobForWorker(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, JOB_GUID);
        assertNotNull(result);

        ArgumentCaptor<UploadTableJob> jobCaptor = ArgumentCaptor.forClass(UploadTableJob.class);
        verify(mockSvc).updateUploadTableJobForWorker(eq(TestConstants.TEST_APP_ID), eq(TestConstants.TEST_STUDY_ID),
                eq(JOB_GUID), jobCaptor.capture());
        UploadTableJob capturedJob = jobCaptor.getValue();
        assertEquals(capturedJob.getS3Key(), S3_KEY);
    }

    @Test
    public void delete() {
        StatusMessage result = controller.deleteUploadTableRowForSuperadmin(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, RECORD_ID);
        assertNotNull(result);
        verify(mockSvc).deleteUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
        verify(controller).getAuthenticatedSession(Roles.SUPERADMIN);
    }

    @Test
    public void get() {
        // Mock service.
        UploadTableRow row = UploadTableRow.create();
        doReturn(row).when(mockSvc).getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID,
                RECORD_ID);

        // Execute and verify.
        UploadTableRow result = controller.getUploadTableRowForSuperadmin(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID, RECORD_ID);
        assertSame(result, row);
        verify(mockSvc).getUploadTableRow(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID, RECORD_ID);
        verify(controller).getAuthenticatedSession(Roles.SUPERADMIN);
    }

    @Test
    public void query() throws Exception {
        // Mock request. Since this is parsed from JSON, set one of the fields so we can verify it later.
        UploadTableRowQuery query = new UploadTableRowQuery();
        query.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        mockRequestBody(mockRequest, query);

        // Mock service.
        List<UploadTableRow> rowList = ImmutableList.of(UploadTableRow.create());
        PagedResourceList<UploadTableRow> pagedResourceList = new PagedResourceList<>(rowList, 1);
        doReturn(pagedResourceList).when(mockSvc).queryUploadTableRows(eq(TestConstants.TEST_APP_ID),
                eq(TestConstants.TEST_STUDY_ID), any());

        // Execute and verify.
        PagedResourceList<UploadTableRow> result = controller.queryUploadTableRowsForWorker(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertSame(result, pagedResourceList);
        verify(controller).getAuthenticatedSession(Roles.WORKER);

        ArgumentCaptor<UploadTableRowQuery> queryCaptor = ArgumentCaptor.forClass(UploadTableRowQuery.class);
        verify(mockSvc).queryUploadTableRows(eq(TestConstants.TEST_APP_ID), eq(TestConstants.TEST_STUDY_ID),
                queryCaptor.capture());
        UploadTableRowQuery capturedQuery = queryCaptor.getValue();
        assertEquals(capturedQuery.getAssessmentGuid(), TestConstants.ASSESSMENT_1_GUID);
    }

    @Test
    public void save() throws Exception {
        // Mock request. Since this is parsed from JSON, set one of the fields so we can verify it later.
        UploadTableRow row = UploadTableRow.create();
        row.setAssessmentGuid(TestConstants.ASSESSMENT_1_GUID);
        mockRequestBody(mockRequest, row);

        // Execute and verify.
        StatusMessage result = controller.saveUploadTableRowForWorker(TestConstants.TEST_APP_ID,
                TestConstants.TEST_STUDY_ID);
        assertNotNull(result);
        verify(controller).getAuthenticatedSession(Roles.WORKER);

        ArgumentCaptor<UploadTableRow> rowCaptor = ArgumentCaptor.forClass(UploadTableRow.class);
        verify(mockSvc).saveUploadTableRow(eq(TestConstants.TEST_APP_ID), eq(TestConstants.TEST_STUDY_ID),
                rowCaptor.capture());
        UploadTableRow capturedRow = rowCaptor.getValue();
        assertEquals(capturedRow.getAssessmentGuid(), TestConstants.ASSESSMENT_1_GUID);
    }
}
