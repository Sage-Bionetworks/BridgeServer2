package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.HealthDataEx3Service;

public class HealthDataEx3ControllerTest {
    private static final String CREATED_ON_START_STRING = "2020-07-27T18:48:14.564-0700";
    private static final DateTime CREATED_ON_START = DateTime.parse(CREATED_ON_START_STRING);
    private static final String CREATED_ON_END_STRING = "2020-07-29T15:22:58.998-0700";
    private static final DateTime CREATED_ON_END = DateTime.parse(CREATED_ON_END_STRING);
    private static final String RECORD_ID = "test-record";
    private static final String STUDY_ID = "test-study";

    @Mock
    private AccountService mockAccountService;

    @Mock
    private HealthDataEx3Service mockHealthDataEx3Service;

    @Mock
    private Metrics mockMetrics;

    @Mock
    private HttpServletRequest mockRequest;

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
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.SUPERADMIN);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(HealthDataEx3Controller.class);
        assertPost(HealthDataEx3Controller.class, "createOrUpdateRecord");
        assertDelete(HealthDataEx3Controller.class, "deleteRecordsForUser");
        assertGet(HealthDataEx3Controller.class, "getRecord");
        assertGet(HealthDataEx3Controller.class, "getRecordsForUser");
        assertGet(HealthDataEx3Controller.class, "getRecordsForCurrentApp");
        assertGet(HealthDataEx3Controller.class, "getRecordsForStudy");
    }

    @Test
    public void createOrUpdateRecord() throws Exception {
        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setId(RECORD_ID);
        mockRequestBody(mockRequest, record);

        when(mockHealthDataEx3Service.createOrUpdateRecord(any())).thenAnswer(invocation -> invocation
                .getArgument(0));

        HealthDataRecordEx3 result = controller.createOrUpdateRecord();
        assertEquals(result.getId(), RECORD_ID);
        assertEquals(result.getAppId(), TestConstants.TEST_APP_ID);

        verify(mockHealthDataEx3Service).createOrUpdateRecord(same(result));
        verify(mockMetrics).setRecordId(RECORD_ID);
    }

    @Test
    public void deleteRecordsForUser() {
        when(mockAccountService.getHealthCodeForAccount(any())).thenReturn(TestConstants.HEALTH_CODE);

        StatusMessage statusMessage = controller.deleteRecordsForUser(TestConstants.USER_ID);
        assertEquals(statusMessage.getMessage(), "Health data has been deleted for participant");

        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        verify(mockAccountService).getHealthCodeForAccount(accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(accountId.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(accountId.getId(), TestConstants.USER_ID);

        verify(mockHealthDataEx3Service).deleteRecordsForHealthCode(TestConstants.HEALTH_CODE);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void deleteRecordsForUser_UserNotFound() {
        when(mockAccountService.getHealthCodeForAccount(any())).thenReturn(null);
        controller.deleteRecordsForUser(TestConstants.USER_ID);
    }

    @Test
    public void getRecord() {
        HealthDataRecordEx3 record = HealthDataRecordEx3.create();
        record.setId(RECORD_ID);
        when(mockHealthDataEx3Service.getRecord(RECORD_ID)).thenReturn(Optional.of(record));

        HealthDataRecordEx3 result = controller.getRecord(RECORD_ID);
        assertSame(result, record);

        verify(mockHealthDataEx3Service).getRecord(RECORD_ID);
        verify(mockMetrics).setRecordId(RECORD_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecord_NotFound() {
        when(mockHealthDataEx3Service.getRecord(RECORD_ID)).thenReturn(Optional.empty());
        controller.getRecord(RECORD_ID);
    }

    @Test
    public void getRecordsForUser() {
        when(mockAccountService.getHealthCodeForAccount(any())).thenReturn(TestConstants.HEALTH_CODE);

        List<HealthDataRecordEx3> recordList = ImmutableList.of(HealthDataRecordEx3.create());
        when(mockHealthDataEx3Service.getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START,
                CREATED_ON_END)).thenReturn(recordList);

        ResourceList<HealthDataRecordEx3> resourceList = controller.getRecordsForUser(TestConstants.USER_ID,
                CREATED_ON_START_STRING, CREATED_ON_END_STRING);
        assertSame(resourceList.getItems(), recordList);
        assertEquals(resourceList.getRequestParams().get("userId"), TestConstants.USER_ID);
        assertEquals(resourceList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_START_STRING);
        assertEquals(resourceList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STRING);

        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        verify(mockAccountService).getHealthCodeForAccount(accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(accountId.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(accountId.getId(), TestConstants.USER_ID);

        verify(mockHealthDataEx3Service).getRecordsForHealthCode(TestConstants.HEALTH_CODE, CREATED_ON_START,
                CREATED_ON_END);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecordsForUser_UserNotFound() {
        when(mockAccountService.getHealthCodeForAccount(any())).thenReturn(null);
        controller.getRecordsForUser(TestConstants.USER_ID, CREATED_ON_START_STRING, CREATED_ON_END_STRING);
    }

    @Test
    public void getRecordsForCurrentApp() {
        List<HealthDataRecordEx3> recordList = ImmutableList.of(HealthDataRecordEx3.create());
        when(mockHealthDataEx3Service.getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START, CREATED_ON_END))
                .thenReturn(recordList);

        ResourceList<HealthDataRecordEx3> resourceList = controller.getRecordsForCurrentApp(CREATED_ON_START_STRING,
                CREATED_ON_END_STRING);
        assertSame(resourceList.getItems(), recordList);
        assertEquals(resourceList.getRequestParams().get("appId"), TestConstants.TEST_APP_ID);
        assertEquals(resourceList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_START_STRING);
        assertEquals(resourceList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STRING);

        verify(mockHealthDataEx3Service).getRecordsForApp(TestConstants.TEST_APP_ID, CREATED_ON_START, CREATED_ON_END);
    }

    @Test
    public void getRecordsForStudy() {
        List<HealthDataRecordEx3> recordList = ImmutableList.of(HealthDataRecordEx3.create());
        when(mockHealthDataEx3Service.getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID, CREATED_ON_START,
                CREATED_ON_END)).thenReturn(recordList);

        ResourceList<HealthDataRecordEx3> resourceList = controller.getRecordsForStudy(STUDY_ID,
                CREATED_ON_START_STRING, CREATED_ON_END_STRING);
        assertSame(resourceList.getItems(), recordList);
        assertEquals(resourceList.getRequestParams().get("appId"), TestConstants.TEST_APP_ID);
        assertEquals(resourceList.getRequestParams().get("studyId"), STUDY_ID);
        assertEquals(resourceList.getRequestParams().get(ResourceList.START_TIME), CREATED_ON_START_STRING);
        assertEquals(resourceList.getRequestParams().get(ResourceList.END_TIME), CREATED_ON_END_STRING);

        verify(mockHealthDataEx3Service).getRecordsForAppAndStudy(TestConstants.TEST_APP_ID, STUDY_ID,
                CREATED_ON_START, CREATED_ON_END);
    }
}
