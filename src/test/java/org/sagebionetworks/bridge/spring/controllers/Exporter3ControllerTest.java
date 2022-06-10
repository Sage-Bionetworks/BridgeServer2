package org.sagebionetworks.bridge.spring.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
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
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;
import org.sagebionetworks.bridge.services.Exporter3Service;

public class Exporter3ControllerTest {
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
        assertPost(Exporter3Controller.class, "initExporter3ForStudy");
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

        // Execute.
        StatusMessage result = controller.subscribeToCreateStudyNotifications();
        assertEquals(result.getMessage(), Exporter3Controller.MESSAGE_SUBSCRIPTION_CREATED.getMessage());

        // Verify service call.
        ArgumentCaptor<ExporterSubscriptionRequest> svcInputCaptor = ArgumentCaptor.forClass(
                ExporterSubscriptionRequest.class);
        verify(mockSvc).subscribeToCreateStudyNotifications(eq(TestConstants.TEST_APP_ID), svcInputCaptor.capture());
        ExporterSubscriptionRequest svcInput = svcInputCaptor.getValue();
        assertEquals(svcInput.getEndpoint(), SUBSCRIBE_QUEUE_ARN);
        assertEquals(svcInput.getProtocol(), SUBSCRIBE_PROTOCOL);
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
}
