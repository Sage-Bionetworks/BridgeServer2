package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.exporter.ExportToAppNotification;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionResult;
import org.sagebionetworks.bridge.services.Exporter3Service;

public class Exporter3ControllerTest {
    private static final String RECORD_ID = "test-record";
    private static final String SUBSCRIBE_PROTOCOL = "sqs";
    private static final String SUBSCRIBE_QUEUE_ARN = "arn:aws:sqs:us-east-1:111111111111:test-queue";

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private Exporter3Service mockSvc;

    @InjectMocks
    @Spy
    private Exporter3Controller controller;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);

        // Mock request.
        doReturn(mockRequest).when(controller).request();
    }

    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(Exporter3Controller.class);
        assertPost(Exporter3Controller.class, "initExporter3");
        assertPost(Exporter3Controller.class, "subscribeToCreateStudyNotifications");
        assertPost(Exporter3Controller.class, "subscribeToExportNotificationsForApp");
        assertPost(Exporter3Controller.class, "sendExportNotifications");
        assertPost(Exporter3Controller.class, "initExporter3ForStudy");
        assertPost(Exporter3Controller.class, "subscribeToExportNotificationsForStudy");
    }

    @Test
    public void initExporter3() throws Exception {
        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        // Mock service.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        when(mockSvc.initExporter3(TestConstants.TEST_APP_ID)).thenReturn(ex3Config);

        // Execute.
        Exporter3Configuration retVal = controller.initExporter3();
        assertSame(retVal, ex3Config);
    }

    @Test
    public void subscribeToCreateStudyNotifications() throws Exception {
        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        // Mock request body.
        ExporterSubscriptionRequest controllerInput = new ExporterSubscriptionRequest();
        controllerInput.setEndpoint(SUBSCRIBE_QUEUE_ARN);
        controllerInput.setProtocol(SUBSCRIBE_PROTOCOL);
        mockRequestBody(mockRequest, controllerInput);

        // Mock service.
        ExporterSubscriptionResult svcResult = new ExporterSubscriptionResult();
        when(mockSvc.subscribeToCreateStudyNotifications(any(), any())).thenReturn(svcResult);

        // Execute.
        ExporterSubscriptionResult result = controller.subscribeToCreateStudyNotifications();
        assertSame(result, svcResult);

        // Verify service call.
        ArgumentCaptor<ExporterSubscriptionRequest> svcInputCaptor = ArgumentCaptor.forClass(
                ExporterSubscriptionRequest.class);
        verify(mockSvc).subscribeToCreateStudyNotifications(eq(TestConstants.TEST_APP_ID), svcInputCaptor.capture());
        ExporterSubscriptionRequest svcInput = svcInputCaptor.getValue();
        assertEquals(svcInput.getEndpoint(), SUBSCRIBE_QUEUE_ARN);
        assertEquals(svcInput.getProtocol(), SUBSCRIBE_PROTOCOL);
    }

    @Test
    public void subscribeToExportNotificationsForApp() throws Exception {
        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(DEVELOPER);

        // Mock request body.
        ExporterSubscriptionRequest controllerInput = new ExporterSubscriptionRequest();
        controllerInput.setEndpoint(SUBSCRIBE_QUEUE_ARN);
        controllerInput.setProtocol(SUBSCRIBE_PROTOCOL);
        mockRequestBody(mockRequest, controllerInput);

        // Mock service.
        ExporterSubscriptionResult svcResult = new ExporterSubscriptionResult();
        when(mockSvc.subscribeToExportNotificationsForApp(any(), any())).thenReturn(svcResult);

        // Execute.
        ExporterSubscriptionResult result = controller.subscribeToExportNotificationsForApp();
        assertSame(result, svcResult);

        // Verify service call.
        ArgumentCaptor<ExporterSubscriptionRequest> svcInputCaptor = ArgumentCaptor.forClass(
                ExporterSubscriptionRequest.class);
        verify(mockSvc).subscribeToExportNotificationsForApp(eq(TestConstants.TEST_APP_ID), svcInputCaptor.capture());
        ExporterSubscriptionRequest svcInput = svcInputCaptor.getValue();
        assertEquals(svcInput.getEndpoint(), SUBSCRIBE_QUEUE_ARN);
        assertEquals(svcInput.getProtocol(), SUBSCRIBE_PROTOCOL);
    }

    @Test
    public void sendExportNotifications() throws Exception {
        // Mock session.
        doReturn(new UserSession()).when(controller).getAuthenticatedSession(WORKER);

        // Mock request body. Just set basic parameters for testing.
        ExportToAppNotification notification = new ExportToAppNotification();
        notification.setAppId(TestConstants.TEST_APP_ID);
        notification.setRecordId(RECORD_ID);
        mockRequestBody(mockRequest, notification);

        // Execute.
        StatusMessage message = controller.sendExportNotifications();
        assertSame(message, Exporter3Controller.SEND_NOTIFICATION_MSG);

        // Verify service call.
        ArgumentCaptor<ExportToAppNotification> svcInputCaptor = ArgumentCaptor.forClass(
                ExportToAppNotification.class);
        verify(mockSvc).sendExportNotifications(svcInputCaptor.capture());
        ExportToAppNotification svcInput = svcInputCaptor.getValue();
        assertEquals(svcInput.getAppId(), TestConstants.TEST_APP_ID);
        assertEquals(svcInput.getRecordId(), RECORD_ID);
    }

    @Test
    public void initExporter3ForStudy() throws Exception {
        // Set up request context.
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());

        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);

        // Mock service.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        when(mockSvc.initExporter3ForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID))
                .thenReturn(ex3Config);

        // Execute.
        Exporter3Configuration retVal = controller.initExporter3ForStudy(TestConstants.TEST_STUDY_ID);
        assertSame(retVal, ex3Config);
    }

    @Test
    public void exportTimelineForStudy() throws Exception {
        // Set up request context.
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());

        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(STUDY_DESIGNER, DEVELOPER);

        // Mock service.
        Exporter3Configuration ex3Config = new Exporter3Configuration();
        when(mockSvc.exportTimelineForStudy(TestConstants.TEST_APP_ID, TestConstants.TEST_STUDY_ID))
                .thenReturn(ex3Config);

        // Execute.
        Exporter3Configuration retVal = controller.exportTimelineForStudy(TestConstants.TEST_STUDY_ID);
        assertSame(retVal, ex3Config);
    }

    public void subscribeToExportNotificationsForStudy() throws Exception {
        // Set up request context.
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());

        // Mock session.
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TestConstants.TEST_APP_ID);
        doReturn(mockSession).when(controller).getAuthenticatedSession(STUDY_DESIGNER, STUDY_COORDINATOR, DEVELOPER);

        // Mock request body.
        ExporterSubscriptionRequest controllerInput = new ExporterSubscriptionRequest();
        controllerInput.setEndpoint(SUBSCRIBE_QUEUE_ARN);
        controllerInput.setProtocol(SUBSCRIBE_PROTOCOL);
        mockRequestBody(mockRequest, controllerInput);

        // Mock service.
        ExporterSubscriptionResult svcResult = new ExporterSubscriptionResult();
        when(mockSvc.subscribeToExportNotificationsForStudy(any(), any(), any())).thenReturn(svcResult);

        // Execute.
        ExporterSubscriptionResult result = controller.subscribeToExportNotificationsForStudy(TEST_STUDY_ID);
        assertSame(result, svcResult);

        // Verify service call.
        ArgumentCaptor<ExporterSubscriptionRequest> svcInputCaptor = ArgumentCaptor.forClass(
                ExporterSubscriptionRequest.class);
        verify(mockSvc).subscribeToExportNotificationsForStudy(eq(TestConstants.TEST_APP_ID), eq(TEST_STUDY_ID),
                svcInputCaptor.capture());
        ExporterSubscriptionRequest svcInput = svcInputCaptor.getValue();
        assertEquals(svcInput.getEndpoint(), SUBSCRIBE_QUEUE_ARN);
        assertEquals(svcInput.getProtocol(), SUBSCRIBE_PROTOCOL);
    }
}
