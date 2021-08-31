package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.HealthDataEx3Service;
import org.sagebionetworks.bridge.services.StudyService;

public class HealthDataEx3ControllerTest {
    private static final String CREATED_ON_START_STRING = "2020-07-27T18:48:14.564-0700";
    private static final DateTime CREATED_ON_START = DateTime.parse(CREATED_ON_START_STRING);
    private static final String CREATED_ON_END_STRING = "2020-07-29T15:22:58.998-0700";
    private static final DateTime CREATED_ON_END = DateTime.parse(CREATED_ON_END_STRING);
    private static final String OFFSET_KEY = "dummy-offset-key";
    private static final String PAGE_SIZE_STRING = String.valueOf(BridgeConstants.API_DEFAULT_PAGE_SIZE);
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";

    @Mock
    private AccountService mockAccountService;

    @Mock
    private AppService mockAppService;

    @Mock
    private HealthDataEx3Service mockHealthDataEx3Service;

    @Mock
    private Metrics mockMetrics;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private StudyService mockStudyService;

    @InjectMocks
    @Spy
    private HealthDataEx3Controller controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock metrics.
        doReturn(mockMetrics).when(controller).getMetrics();

        // Mock request.
        doReturn(mockRequest).when(controller).request();

        // Mock session.
        doReturn(new UserSession()).when(controller).getAuthenticatedSession(any());
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(HealthDataEx3Controller.class);
        assertPost(HealthDataEx3Controller.class, "createOrUpdateRecord");
        assertDelete(HealthDataEx3Controller.class, "deleteRecordsForUser");
        assertGet(HealthDataEx3Controller.class, "getRecord");
        assertGet(HealthDataEx3Controller.class, "getRecordsForUser");
        assertGet(HealthDataEx3Controller.class, "getRecordsForApp");
        assertGet(HealthDataEx3Controller.class, "getRecordsForStudy");
    }

    @Test
    public void createOrUpdateRecord() throws Exception {
        // Set up mocks.
        App app = App.create();
        app.setIdentifier(TestConstants.TEST_APP_ID);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);

        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setId(RECORD_ID);
        mockRequestBody(mockRequest, record);

        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenAnswer(invocation -> invocation
                .getArgument(0));

        // Execute and verify.
        HealthDataRecordEx3 result = controller.createOrUpdateRecord(TestConstants.TEST_APP_ID);
        assertEquals(result.getId(), RECORD_ID);
        assertEquals(result.getAppId(), TestConstants.TEST_APP_ID);

        verify(mockHealthDataEx3Service).createOrUpdateRecord(same(result));
        verify(mockMetrics).setRecordId(RECORD_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void createOrUpdateRecord_AppNotFound() {
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(null);
        controller.createOrUpdateRecord(TestConstants.TEST_APP_ID);
    }

    @Test
    public void deleteRecordsForUser() {
        // Set up mocks.
        when(mockAccountService.getAccountHealthCode(any(), any()))
            .thenReturn(Optional.of(HEALTH_CODE));

        // Execute.
        StatusMessage statusMessage = controller.deleteRecordsForUser(TestConstants.TEST_APP_ID,
                TestConstants.TEST_USER_ID);
        assertEquals(statusMessage.getMessage(), "Health data has been deleted for participant");

        verify(mockHealthDataEx3Service).deleteRecordsForHealthCode(HEALTH_CODE);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteRecordsForUser_UserNotFound() {
        when(mockAccountService.getAccountHealthCode(any(), any())).thenReturn(Optional.empty());
        controller.deleteRecordsForUser(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID);
    }

    @Test
    public void getRecord() {
        // Set up mocks.
        App app = App.create();
        app.setIdentifier(TestConstants.TEST_APP_ID);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);

        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setAppId(TestConstants.TEST_APP_ID);
        record.setId(RECORD_ID);
        when(mockHealthDataEx3Service.getRecord(RECORD_ID)).thenReturn(Optional.of(record));

        // Execute and verify.
        HealthDataRecordEx3 result = controller.getRecord(TestConstants.TEST_APP_ID, RECORD_ID);
        assertSame(result, record);

        verify(mockHealthDataEx3Service).getRecord(RECORD_ID);
        verify(mockMetrics).setRecordId(RECORD_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecord_RecordInWrongApp() {
        // Set up mocks.
        App app = App.create();
        app.setIdentifier(TestConstants.TEST_APP_ID);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);

        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setAppId("wrong-app");
        record.setId(RECORD_ID);
        when(mockHealthDataEx3Service.getRecord(RECORD_ID)).thenReturn(Optional.of(record));

        // Execute.
        controller.getRecord(TestConstants.TEST_APP_ID, RECORD_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecord_RecordNotFound() {
        // Set up mocks.
        App app = App.create();
        app.setIdentifier(TestConstants.TEST_APP_ID);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);

        when(mockHealthDataEx3Service.getRecord(RECORD_ID)).thenReturn(Optional.empty());

        // Execute.
        controller.getRecord(TestConstants.TEST_APP_ID, RECORD_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecord_AppNotFound() {
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(null);
        controller.getRecord(TestConstants.TEST_APP_ID, RECORD_ID);
    }

    @Test
    public void getRecordsForUser() {
        // Set up mocks.
        when(mockAccountService.getAccountHealthCode(any(), any()))
            .thenReturn(Optional.of(HEALTH_CODE));

        ForwardCursorPagedResourceList<HealthDataRecordEx3> recordList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(HealthDataRecordEx3.create()), null);
        when(mockHealthDataEx3Service.getRecordsForHealthCode(HEALTH_CODE, CREATED_ON_START,
                CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(recordList);

        // Execute and verify.
        ResourceList<HealthDataRecordEx3> outputList = controller.getRecordsForUser(TestConstants.TEST_APP_ID, TEST_USER_ID,
                CREATED_ON_START_STRING, CREATED_ON_END_STRING, PAGE_SIZE_STRING, OFFSET_KEY);
        assertSame(outputList, recordList);
        assertEquals(outputList.getRequestParams().size(), 7);
        assertEquals(outputList.getRequestParams().get("appId"), TestConstants.TEST_APP_ID);
        assertEquals(outputList.getRequestParams().get("userId"), TestConstants.TEST_USER_ID);
        assertEquals(outputList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_START_STRING);
        assertEquals(outputList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STRING);
        assertEquals(outputList.getRequestParams().get(ResourceList.PAGE_SIZE), BridgeConstants.API_DEFAULT_PAGE_SIZE);
        assertEquals(outputList.getRequestParams().get(ResourceList.OFFSET_KEY), OFFSET_KEY);
        assertEquals(outputList.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        verify(mockHealthDataEx3Service).getRecordsForHealthCode(HEALTH_CODE, CREATED_ON_START,
                CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecordsForUser_UserNotFound() {
        when(mockAccountService.getAccountHealthCode(any(), any())).thenReturn(Optional.empty());
        controller.getRecordsForUser(TestConstants.TEST_APP_ID, TestConstants.TEST_USER_ID, CREATED_ON_START_STRING, CREATED_ON_END_STRING,
                PAGE_SIZE_STRING, OFFSET_KEY);
    }

    @Test
    public void getRecordsForApp() {
        // Set up mocks.
        App app = App.create();
        app.setIdentifier(TestConstants.TEST_APP_ID);
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(app);

        ForwardCursorPagedResourceList<HealthDataRecordEx3> recordList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(HealthDataRecordEx3.create()), null);
        when(mockHealthDataEx3Service.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START, CREATED_ON_END,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(recordList);

        // Execute and verify.
        ResourceList<HealthDataRecordEx3> outputList = controller.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START_STRING,
                CREATED_ON_END_STRING, PAGE_SIZE_STRING, OFFSET_KEY);
        assertSame(outputList, recordList);
        assertEquals(outputList.getRequestParams().size(), 6);
        assertEquals(outputList.getRequestParams().get("appId"), TestConstants.TEST_APP_ID);
        assertEquals(outputList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_START_STRING);
        assertEquals(outputList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STRING);
        assertEquals(outputList.getRequestParams().get(ResourceList.PAGE_SIZE), BridgeConstants.API_DEFAULT_PAGE_SIZE);
        assertEquals(outputList.getRequestParams().get(ResourceList.OFFSET_KEY), OFFSET_KEY);
        assertEquals(outputList.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        verify(mockHealthDataEx3Service).getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START, CREATED_ON_END,
                BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecordsForApp_AppNotFound() {
        when(mockAppService.getApp(TestConstants.TEST_APP_ID)).thenReturn(null);
        controller.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START_STRING, CREATED_ON_END_STRING,
                PAGE_SIZE_STRING, OFFSET_KEY);
    }

    @Test
    public void getRecordsForStudy() {
        // Set up mocks.
        Study study = Study.create();
        study.setAppId(TestConstants.TEST_APP_ID);
        study.setIdentifier(STUDY_ID);
        when(mockStudyService.getStudy(TestConstants.TEST_APP_ID, STUDY_ID, true)).thenReturn(study);

        ForwardCursorPagedResourceList<HealthDataRecordEx3> recordList = new ForwardCursorPagedResourceList<>(
                ImmutableList.of(HealthDataRecordEx3.create()), null);
        when(mockHealthDataEx3Service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START,
                CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY)).thenReturn(recordList);

        // Execute and verify.
        ResourceList<HealthDataRecordEx3> outputList = controller.getRecordsForStudy(TestConstants.TEST_APP_ID, STUDY_ID,
                CREATED_ON_START_STRING, CREATED_ON_END_STRING, PAGE_SIZE_STRING, OFFSET_KEY);
        assertSame(outputList, recordList);
        assertEquals(outputList.getRequestParams().size(), 7);
        assertEquals(outputList.getRequestParams().get("appId"), TestConstants.TEST_APP_ID);
        assertEquals(outputList.getRequestParams().get("studyId"), STUDY_ID);
        assertEquals(outputList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_START_STRING);
        assertEquals(outputList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STRING);
        assertEquals(outputList.getRequestParams().get(ResourceList.PAGE_SIZE), BridgeConstants.API_DEFAULT_PAGE_SIZE);
        assertEquals(outputList.getRequestParams().get(ResourceList.OFFSET_KEY), OFFSET_KEY);
        assertEquals(outputList.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);

        verify(mockHealthDataEx3Service).getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID,
                CREATED_ON_START, CREATED_ON_END, BridgeConstants.API_DEFAULT_PAGE_SIZE, OFFSET_KEY);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecordsForStudy_StudyNotFound() {
        when(mockStudyService.getStudy(TestConstants.TEST_APP_ID, STUDY_ID, true))
                .thenThrow(EntityNotFoundException.class);
        controller.getRecordsForStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START_STRING,
                CREATED_ON_END_STRING, PAGE_SIZE_STRING, OFFSET_KEY);
    }
}
