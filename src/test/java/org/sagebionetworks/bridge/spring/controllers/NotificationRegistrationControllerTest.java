package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.NotificationsService;

public class NotificationRegistrationControllerTest extends Mockito {
    private static final ObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final StudyParticipant PARTICIPANT = TestUtils.getStudyParticipant(
            NotificationRegistrationControllerTest.class);
    private static final String OS_NAME = "osName";
    private static final String DEVICE_ID = "deviceId";
    private static final String GUID = "oneRegistrationGuid";
    private static final String ENDPOINT = "endpoint";
    
    @Mock
    NotificationsService mockNotificationService;
    
    @Mock
    NotificationTopicService mockTopicService;
    
    @Mock
    UserSession mockSession;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<NotificationRegistration> registrationCaptor;

    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @Captor
    ArgumentCaptor<Set<String>> stringSetCaptor;
    
    @InjectMocks
    @Spy
    NotificationRegistrationController controller;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        when(mockSession.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockSession.getParticipant()).thenReturn(PARTICIPANT);
        when(mockSession.getAppId()).thenReturn(TEST_APP_ID);
        
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(NotificationRegistrationController.class);
        assertGet(NotificationRegistrationController.class, "getAllRegistrations");
        assertCreate(NotificationRegistrationController.class, "createRegistration");
        assertPost(NotificationRegistrationController.class, "updateRegistration");
        assertGet(NotificationRegistrationController.class, "getRegistration");
        assertDelete(NotificationRegistrationController.class, "deleteRegistration");
        assertGet(NotificationRegistrationController.class, "getSubscriptionStatuses");
        assertPost(NotificationRegistrationController.class, "subscribe"); // not create
    }
    
    @Test
    public void getAllRegistrations() throws Exception {
        doReturn(createRegList()).when(mockNotificationService).listRegistrations(HEALTH_CODE);
        
        ResourceList<NotificationRegistration> result = controller.getAllRegistrations();
        
        verify(mockNotificationService).listRegistrations(HEALTH_CODE);
        
        assertEquals(result.getItems().size(), 1);
        
        NotificationRegistration registration = serializeAndDeserialize(result.getItems().get(0));
        assertEquals(registration.getDeviceId(), DEVICE_ID);
        assertEquals(registration.getGuid(), GUID);
        assertEquals(registration.getOsName(), OS_NAME);
        assertEquals(registration.getEndpoint(), ENDPOINT);
        assertNull(registration.getHealthCode());
    }
        
    @Test
    public void createRegistration() throws Exception {
        // Mock service.
        doReturn(createRegList().get(0)).when(mockNotificationService).createRegistration(any(), any(), any());

        // Mock Play context.
        String json = TestUtils.createJson("{'deviceId':'"+DEVICE_ID+"','osName':'"+OS_NAME+"'}");
        mockRequestBody(mockRequest, json);

        // Execute and validate.
        GuidHolder result = controller.createRegistration();
        
        assertEquals(result.getGuid(), GUID);

        // Verify service.
        verify(mockNotificationService).createRegistration(eq(TEST_APP_ID), any(), registrationCaptor.capture());
        
        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals(registration.getDeviceId(), DEVICE_ID);
        assertEquals(registration.getOsName(), OS_NAME);
        assertEquals(registration.getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void updateRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockNotificationService).updateRegistration(any(), any());
        
        String json = TestUtils.createJson("{'guid':'guidWeIgnore','deviceId':'NEW_DEVICE_ID','osName':'"+OS_NAME+"'}");
        mockRequestBody(mockRequest, json);
        
        GuidHolder result = controller.updateRegistration(GUID);
        
        assertEquals(result.getGuid(), GUID);
        
        verify(mockNotificationService).updateRegistration(eq(TEST_APP_ID), registrationCaptor.capture());
        NotificationRegistration registration = registrationCaptor.getValue();
        assertEquals(registration.getDeviceId(), "NEW_DEVICE_ID");
        assertEquals(registration.getOsName(), OS_NAME);
        assertEquals(registration.getHealthCode(), HEALTH_CODE);
        assertEquals(registration.getGuid(), GUID);
    }
    
    @Test
    public void getRegistration() throws Exception {
        doReturn(createRegList().get(0)).when(mockNotificationService).getRegistration(HEALTH_CODE, GUID);
        
        NotificationRegistration result = controller.getRegistration(GUID);
        
        NotificationRegistration registration = serializeAndDeserialize(result);
        verify(mockNotificationService).getRegistration(HEALTH_CODE, GUID);
        verifyRegistration(registration);
    }
    
    @Test
    public void deleteRegistration() throws Exception {
        StatusMessage result = controller.deleteRegistration(GUID);
        assertEquals(result, NotificationRegistrationController.DELETED_MSG);
        
        verify(mockNotificationService).deleteRegistration(TEST_APP_ID, HEALTH_CODE, GUID);
    }

    @Test
    public void getSubscriptionStatuses() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        SubscriptionStatus status = new SubscriptionStatus("topicGuid","topicName",true);
        doReturn(ImmutableList.of(status)).when(mockTopicService)
                .currentSubscriptionStatuses(TEST_APP_ID, HEALTH_CODE, GUID);

        ResourceList<SubscriptionStatus> result = controller.getSubscriptionStatuses(GUID);
        
        assertEquals(result.getItems().size(), 1);
        SubscriptionStatus retrievedStatus = result.getItems().get(0);
        assertEquals(retrievedStatus.getTopicGuid(), "topicGuid");
        assertEquals(retrievedStatus.getTopicName(), "topicName");
        assertTrue(retrievedStatus.isSubscribed());
    }

    @Test
    public void subscribe() throws Exception {
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        SubscriptionStatus status = new SubscriptionStatus("topicGuid","topicName",true);
        doReturn(ImmutableList.of(status)).when(mockTopicService).subscribe(eq(TEST_APP_ID), eq(HEALTH_CODE), eq(GUID),
                any());
        mockRequestBody(mockRequest, createSubscriptionRequest());
        
        ResourceList<SubscriptionStatus> result = controller.subscribe(GUID);
        
        assertEquals(result.getItems().size(), 1);
        SubscriptionStatus retrievedStatus = result.getItems().get(0);
        assertEquals(retrievedStatus.getTopicGuid(), "topicGuid");
        assertEquals(retrievedStatus.getTopicName(), "topicName");
        assertTrue(retrievedStatus.isSubscribed());
        
        verify(mockTopicService).subscribe(eq(TEST_APP_ID), eq(HEALTH_CODE), stringCaptor.capture(),
                stringSetCaptor.capture());
        
        assertEquals(stringCaptor.getValue(), GUID);
        assertEquals(stringSetCaptor.getValue(), ImmutableSet.of("topicA", "topicB"));
    }
    
    private List<NotificationRegistration> createRegList() {
        return ImmutableList.of(createNotificationRegistration());
    }
    
    /**
     * Serialize and deserialize to verify that the health code is not included in the serialization 
     * (probably redundant with tests of NotificationRegistration itself).
     */
    private NotificationRegistration serializeAndDeserialize(NotificationRegistration reg) throws Exception {
        return MAPPER.readValue(MAPPER.writeValueAsString(reg), NotificationRegistration.class);
    }

    private NotificationRegistration createNotificationRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId(DEVICE_ID);
        registration.setEndpoint(ENDPOINT);
        registration.setGuid(GUID);
        registration.setHealthCode(HEALTH_CODE);
        registration.setOsName(OS_NAME);
        registration.setCreatedOn(TIMESTAMP.getMillis());
        return registration;
    }
    
    private SubscriptionRequest createSubscriptionRequest() { 
        return new SubscriptionRequest(ImmutableSet.of("topicA", "topicB"));
    }
    
    private void verifyRegistration(NotificationRegistration reg) {
        assertNull(reg.getHealthCode());
        assertEquals(reg.getEndpoint(), ENDPOINT);
        assertEquals(reg.getOsName(), OS_NAME);
        assertEquals(reg.getGuid(), GUID);
        assertEquals(reg.getDeviceId(), DEVICE_ID);
        assertEquals(reg.getCreatedOn(), TIMESTAMP.getMillis());
    }
}
