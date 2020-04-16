package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertAccept;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.services.NotificationTopicService;

public class NotificationTopicControllerTest extends Mockito {
    
    static final NotificationMessage NOTIFICATION_MESSAGE = new NotificationMessage.Builder()
            .withSubject("a subject").withMessage("a message").build();
    static final ObjectMapper MAPPER = BridgeObjectMapper.get();
    static final String GUID = "DEF-GHI";

    @Mock
    NotificationTopicService mockTopicService;
    
    @Mock
    BridgeConfig mockBridgeConfig;

    @Mock
    UserSession mockUserSession;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<NotificationTopic> topicCaptor;

    @Captor
    ArgumentCaptor<NotificationMessage> messageCaptor;
    
    @Captor
    ArgumentCaptor<SubscriptionRequest> subRequestCaptor; 

    @InjectMocks
    @Spy
    NotificationTopicController controller;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        doReturn(Environment.UAT).when(mockBridgeConfig).getEnvironment();
        doReturn(TEST_APP_ID).when(mockUserSession).getStudyIdentifier();
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(NotificationTopicController.class);
        assertGet(NotificationTopicController.class, "getAllTopics");
        assertCreate(NotificationTopicController.class, "createTopic");
        assertGet(NotificationTopicController.class, "getTopic");
        assertPost(NotificationTopicController.class, "updateTopic");
        assertDelete(NotificationTopicController.class, "deleteTopic");
        assertAccept(NotificationTopicController.class, "sendNotification");
    }
    
    @Test
    public void getAllTopicsIncludeDeleted() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        NotificationTopic topic = getNotificationTopic();
        doReturn(Lists.newArrayList(topic)).when(mockTopicService).listTopics(TEST_APP_ID, true);

        ResourceList<NotificationTopic> result = controller.getAllTopics(true);

        verify(mockTopicService).listTopics(TEST_APP_ID, true);
        assertEquals(result.getItems().size(), 1);
        assertEquals(result.getItems().get(0).getGuid(), topic.getGuid());
    }

    @Test
    public void getAllTopicsExcludeDeleted() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        NotificationTopic topic = getNotificationTopic();
        doReturn(Lists.newArrayList(topic)).when(mockTopicService).listTopics(TEST_APP_ID, false);

        controller.getAllTopics(false);

        // It's enough to test it's there, the prior test has a more complete test of the payload
        verify(mockTopicService).listTopics(TEST_APP_ID, false);
    }
    
    @Test
    public void createTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        NotificationTopic topic = getNotificationTopic();
        mockRequestBody(mockRequest, topic);
        doReturn(topic).when(mockTopicService).createTopic(any());

        GuidHolder result = controller.createTopic();
        
        assertEquals(result.getGuid(), GUID);

        verify(mockTopicService).createTopic(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getValue();
        assertEquals(captured.getName(), topic.getName());
    }

    @Test
    public void getTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockTopicService.getTopic(TEST_APP_ID, GUID)).thenReturn(getNotificationTopic());

        NotificationTopic result = controller.getTopic(GUID);
        // Serialize and deserialize to verify studyId is not included
        NotificationTopic topic = MAPPER.readValue(MAPPER.writeValueAsString(result), NotificationTopic.class);
        
        assertEquals(topic.getName(), "Test Topic Name");
        assertEquals(topic.getGuid(), GUID);
        assertNull(topic.getStudyId());
        assertNull(topic.getTopicARN());
    }

    @Test
    public void updateTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER);
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicService).updateTopic(any());
        mockRequestBody(mockRequest, topic);

        GuidHolder result = controller.updateTopic(GUID);
        assertEquals(result.getGuid(), GUID);

        verify(mockTopicService).updateTopic(topicCaptor.capture());
        NotificationTopic returned = topicCaptor.getValue();
        assertEquals(returned.getName(), topic.getName());
        assertEquals(returned.getGuid(), GUID);
    }
    
    @Test
    public void deleteTopic() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);        
        StatusMessage result = controller.deleteTopic(GUID, false);
        assertEquals(result, NotificationTopicController.DELETE_STATUS_MSG);

        verify(mockTopicService).deleteTopic(TEST_APP_ID, GUID);
    }
    
    @Test
    public void deleteTopicPermanently() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);        
        when(mockUserSession.isInRole(ADMIN)).thenReturn(true);
        
        StatusMessage result = controller.deleteTopic(GUID, true);
        assertEquals(result, NotificationTopicController.DELETE_STATUS_MSG);

        // Does not delete permanently because permissions are wrong; just logically deletes
        verify(mockTopicService).deleteTopicPermanently(TEST_APP_ID, GUID);
    }

    @Test
    public void deleteTopicPermanentlyForDeveloper() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        StatusMessage result = controller.deleteTopic(GUID, true);
        assertEquals(result, NotificationTopicController.DELETE_STATUS_MSG);

        // Does not delete permanently because permissions are wrong; just logically deletes
        verify(mockTopicService).deleteTopic(TEST_APP_ID, GUID);
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void cannotSendMessageAsDeveloper() throws Exception {
        mockRequestBody(mockRequest, NOTIFICATION_MESSAGE);

        controller.sendNotification(GUID);
    }

    @Test
    public void sendNotification() throws Exception {
        doReturn(mockUserSession).when(controller).getAuthenticatedSession(ADMIN);
        mockRequestBody(mockRequest, NOTIFICATION_MESSAGE);

        StatusMessage result = controller.sendNotification(GUID);
        assertEquals(result, NotificationTopicController.SEND_STATUS_MSG);

        verify(mockTopicService).sendNotification(eq(TEST_APP_ID), eq(GUID), messageCaptor.capture());
        NotificationMessage captured = messageCaptor.getValue();
        assertEquals(captured.getSubject(), "a subject");
        assertEquals(captured.getMessage(), "a message");
    }

    // Test permissions of all the methods... DEVELOPER or DEVELOPER RESEARCHER. Do
    // something that
    
    private NotificationTopic getNotificationTopic() {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid(GUID);
        topic.setName("Test Topic Name");
        topic.setShortName("Short Name");
        topic.setDescription("Test Description");
        topic.setStudyId(TEST_APP_ID);
        topic.setTopicARN("atopicArn");
        return topic;
    }
}
