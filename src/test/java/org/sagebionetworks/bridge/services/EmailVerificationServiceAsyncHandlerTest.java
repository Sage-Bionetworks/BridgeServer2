package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.GetIdentityNotificationAttributesRequest;
import com.amazonaws.services.simpleemail.model.GetIdentityNotificationAttributesResult;
import com.amazonaws.services.simpleemail.model.IdentityNotificationAttributes;
import com.amazonaws.services.simpleemail.model.SetIdentityNotificationTopicRequest;
import com.amazonaws.services.simpleemail.model.SetIdentityNotificationTopicResult;
import com.amazonaws.services.simpleemail.model.VerifyEmailIdentityRequest;
import com.amazonaws.services.simpleemail.model.VerifyEmailIdentityResult;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;

public class EmailVerificationServiceAsyncHandlerTest {
    private static final String DUMMY_NOTIFICATION_ARN = "my-notification-topic";
    private static final String EMAIL_ADDRESS = "example@example.com";

    private AmazonSimpleEmailServiceClient mockSesClient;
    private EmailVerificationService.AsyncSnsTopicHandler asyncHandler;
    private int numSetBounceNotificationCalls;
    private int numSetComplaintNotificationCalls;

    @BeforeMethod
    public void setup() {
        // Mock config.
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(EmailVerificationService.CONFIG_KEY_NOTIFICATION_TOPIC_ARN)).thenReturn(
                DUMMY_NOTIFICATION_ARN);

        // Mock SES Client. Each call should fail once, then succeed. (Get notification topic call will be mocked
        // individually in tests, since the result varies from test to test.)
        mockSesClient = mock(AmazonSimpleEmailServiceClient.class);
        when(mockSesClient.verifyEmailIdentity(any())).thenThrow(makeAwsInternalError()).thenReturn(
                new VerifyEmailIdentityResult());

        // Set Notifications has to use an Answer because there are actually two different types of calls here.
        numSetBounceNotificationCalls = 0;
        numSetComplaintNotificationCalls = 0;
        when(mockSesClient.setIdentityNotificationTopic(any())).thenAnswer(invocation -> {
            SetIdentityNotificationTopicRequest request = invocation.getArgument(0);
            boolean shouldThrow = false;
            switch (request.getNotificationType()) {
                case "Bounce": {
                    numSetBounceNotificationCalls++;
                    shouldThrow = numSetBounceNotificationCalls < 2;
                    break;
                }
                case "Complaint": {
                    numSetComplaintNotificationCalls++;
                    shouldThrow = numSetComplaintNotificationCalls < 2;
                    break;
                }
                default: {
                    fail("Unexpected code path");
                }
            }

            if (shouldThrow) {
                throw makeAwsInternalError();
            } else {
                return new SetIdentityNotificationTopicResult();
            }
        });

        // Set up service.
        EmailVerificationService service = new EmailVerificationService();
        service.setAmazonSimpleEmailServiceClient(mockSesClient);
        service.setConfig(mockConfig);

        // For testing purposes, set to 2 tries and a large rate limit.
        service.setMaxSesTries(2);
        service.setSesRateLimit(1000.0);

        // Set up handler.
        asyncHandler = service.new AsyncSnsTopicHandler(EMAIL_ADDRESS);
    }

    @Test
    public void noMapping() {
        mockGetNotificationCallWithResult(null);
        asyncHandler.handle();
        verifyCommonCalls();
        verifySetNotificationCalls();
    }

    @Test
    public void wrongMapping() {
        IdentityNotificationAttributes attributes = new IdentityNotificationAttributes().withBounceTopic("wrong topic")
                .withComplaintTopic("wrong topic");
        mockGetNotificationCallWithResult(attributes);
        asyncHandler.handle();
        verifyCommonCalls();
        verifySetNotificationCalls();
    }

    @Test
    public void snsTopicsAlreadySet() {
        IdentityNotificationAttributes attributes = new IdentityNotificationAttributes()
                .withBounceTopic(DUMMY_NOTIFICATION_ARN).withComplaintTopic(DUMMY_NOTIFICATION_ARN);
        mockGetNotificationCallWithResult(attributes);
        asyncHandler.handle();
        verifyCommonCalls();
        verify(mockSesClient, never()).setIdentityNotificationTopic(any());
    }

    private void mockGetNotificationCallWithResult(IdentityNotificationAttributes attributes) {
        Map<String, IdentityNotificationAttributes> attributesMap = new HashMap<>();
        if (attributes != null) {
            attributesMap.put(EMAIL_ADDRESS, attributes);
        }
        GetIdentityNotificationAttributesResult result = new GetIdentityNotificationAttributesResult()
                .withNotificationAttributes(attributesMap);
        when(mockSesClient.getIdentityNotificationAttributes(any())).thenThrow(makeAwsInternalError()).thenReturn(
                result);
    }

    private void verifyCommonCalls() {
        // Verify verify email calls.
        ArgumentCaptor<VerifyEmailIdentityRequest> verifyEmailRequestCaptor = ArgumentCaptor.forClass(
                VerifyEmailIdentityRequest.class);
        verify(mockSesClient, times(2)).verifyEmailIdentity(verifyEmailRequestCaptor
                .capture());

        List<VerifyEmailIdentityRequest> verifyEmailRequestList = verifyEmailRequestCaptor.getAllValues();
        assertEquals(verifyEmailRequestList.size(), 2);
        for (VerifyEmailIdentityRequest oneVerifyEmailRequest : verifyEmailRequestList) {
            assertEquals(oneVerifyEmailRequest.getEmailAddress(), EMAIL_ADDRESS);
        }

        // Verify get notifications calls.
        ArgumentCaptor<GetIdentityNotificationAttributesRequest> getNotificationRequestCaptor = ArgumentCaptor
                .forClass(GetIdentityNotificationAttributesRequest.class);
        verify(mockSesClient, times(2)).getIdentityNotificationAttributes(
                getNotificationRequestCaptor.capture());

        List<GetIdentityNotificationAttributesRequest> getNotificationRequestList = getNotificationRequestCaptor
                .getAllValues();
        assertEquals(getNotificationRequestList.size(), 2);
        for (GetIdentityNotificationAttributesRequest oneGetNotificationRequest : getNotificationRequestList) {
            List<String> identityList = oneGetNotificationRequest.getIdentities();
            assertEquals(identityList.size(), 1);
            assertEquals(identityList.get(0), EMAIL_ADDRESS);
        }
    }

    private void verifySetNotificationCalls() {
        // There are 4 calls. The first two are bounce calls and are identical. The second two are notification calls
        // and are also identical.
        ArgumentCaptor<SetIdentityNotificationTopicRequest> requestCaptor = ArgumentCaptor.forClass(
                SetIdentityNotificationTopicRequest.class);
        verify(mockSesClient, times(4)).setIdentityNotificationTopic(requestCaptor.capture());
        List<SetIdentityNotificationTopicRequest> requestList = requestCaptor.getAllValues();
        assertEquals(requestList.size(), 4);

        // Verify common args in all calls.
        for (SetIdentityNotificationTopicRequest oneRequest : requestList) {
            assertEquals(oneRequest.getIdentity(), EMAIL_ADDRESS);
            assertEquals(oneRequest.getSnsTopic(), DUMMY_NOTIFICATION_ARN);
        }

        // Verify notification types.
        assertEquals(requestList.get(0).getNotificationType(), "Bounce");
        assertEquals(requestList.get(1).getNotificationType(), "Bounce");
        assertEquals(requestList.get(2).getNotificationType(), "Complaint");
        assertEquals(requestList.get(3).getNotificationType(), "Complaint");
    }

    private static AmazonServiceException makeAwsInternalError() {
        AmazonServiceException ex = new AmazonServiceException("dummy message");
        ex.setErrorCode("Internal Error");
        ex.setStatusCode(500);
        return ex;
    }
}