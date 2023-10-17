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

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.UploadTableService;
import org.sagebionetworks.bridge.upload.UploadTableRow;
import org.sagebionetworks.bridge.upload.UploadTableRowQuery;

public class UploadTableControllerTest {
    private static final String RECORD_ID = "test-record";

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
        doReturn(new UserSession()).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(UploadTableController.class);
        assertDelete(UploadTableController.class, "deleteUploadTableRowForSuperadmin");
        assertGet(UploadTableController.class, "getUploadTableRowForSuperadmin");
        assertPost(UploadTableController.class, "queryUploadTableRowsForWorker");
        assertPost(UploadTableController.class, "saveUploadTableRowForWorker");
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
