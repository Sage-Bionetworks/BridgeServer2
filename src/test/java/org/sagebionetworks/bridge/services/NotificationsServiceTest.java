package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.getNotificationMessage;
import static org.sagebionetworks.bridge.TestUtils.getNotificationRegistration;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotImplementedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.studies.App;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.InvalidParameterException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class NotificationsServiceTest {
    private static final String HEALTH_CODE = "ABC";
    private static final String GUID = "ABC-DEF-GHI-JKL";
    private static final String OS_NAME = "iPhone OS";
    private static final String PLATFORM_ARN = "arn:platform";

    private static final CriteriaContext DUMMY_CONTEXT = new CriteriaContext.Builder().withAppId(TEST_APP_ID)
            .withUserId(USER_ID).build();

    @Mock
    private NotificationTopicService mockNotificationTopicService;

    @Mock
    private ParticipantService mockParticipantService;

    @Mock
    private AppService mockAppService;
    
    @Mock
    private AmazonSNSClient mockSnsClient;
    
    @Mock
    private PublishResult mockPublishResult;
    
    @Mock
    private NotificationRegistrationDao mockRegistrationDao;
    
    @Mock
    private App mockApp;

    @Captor
    private ArgumentCaptor<PublishRequest> requestCaptor;

    private NotificationsService service;

    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        service = new NotificationsService();
        service.setNotificationTopicService(mockNotificationTopicService);
        service.setParticipantService(mockParticipantService);
        service.setAppService(mockAppService);
        service.setNotificationRegistrationDao(mockRegistrationDao);
        service.setSnsClient(mockSnsClient);

        Map<String,String> map = Maps.newHashMap();
        map.put(OS_NAME, PLATFORM_ARN);
        doReturn(map).when(mockApp).getPushNotificationARNs();
     
        doReturn(mockApp).when(mockAppService).getApp(TEST_APP_ID);
    }

    @Test
    public void listRegistrations() {
        List<NotificationRegistration> list = Lists.newArrayList(getNotificationRegistration());
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        List<NotificationRegistration> result = service.listRegistrations(HEALTH_CODE);
        
        verify(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        assertEquals(result, list);
    }
    
    @Test
    public void getRegistration() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockRegistrationDao).getRegistration(HEALTH_CODE, GUID);
        
        NotificationRegistration result = service.getRegistration(HEALTH_CODE, GUID);
        verify(mockRegistrationDao).getRegistration(HEALTH_CODE, GUID);
        assertEquals(result, registration);
    }
    
    @Test
    public void createRegistration_PushNotification() {
        // Mock registration DAO.
        NotificationRegistration registration = getNotificationRegistration();
        registration.setHealthCode(HEALTH_CODE);
        registration.setOsName(OS_NAME);
        doReturn(registration).when(mockRegistrationDao).createPushNotificationRegistration(PLATFORM_ARN, registration);

        // Execute and validate.
        NotificationRegistration result = service.createRegistration(TEST_APP_ID, DUMMY_CONTEXT, registration);
        verify(mockRegistrationDao).createPushNotificationRegistration(PLATFORM_ARN, registration);
        assertEquals(result, registration);

        // We also manage criteria-based topics.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(TEST_APP_ID, DUMMY_CONTEXT, HEALTH_CODE);
    }

    @Test
    public void createRegistration_SmsNotification() {
        // Mock registration DAO.
        NotificationRegistration registration = getSmsNotificationRegistration();
        when(mockRegistrationDao.createRegistration(registration)).thenReturn(registration);

        // Mock participant DAO w/ phone number.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withPhone(TestConstants.PHONE)
                .withPhoneVerified(true).build();
        when(mockParticipantService.getParticipant(mockApp, USER_ID, false)).thenReturn(participant);

        // Execute and validate.
        NotificationRegistration result = service.createRegistration(TEST_APP_ID, DUMMY_CONTEXT, registration);
        verify(mockRegistrationDao).createRegistration(registration);
        assertEquals(result, registration);

        // We also manage criteria-based topics.
        verify(mockNotificationTopicService).manageCriteriaBasedSubscriptions(TEST_APP_ID, DUMMY_CONTEXT, HEALTH_CODE);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createRegistration_SmsNotificationPhoneNotVerified() {
        // Mock participant DAO w/ unverified phone number.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withPhone(TestConstants.PHONE)
                .withPhoneVerified(null).build();
        when(mockParticipantService.getParticipant(mockApp, USER_ID, false)).thenReturn(participant);

        // Execute and validate.
        service.createRegistration(TEST_APP_ID, DUMMY_CONTEXT, getSmsNotificationRegistration());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void createRegistration_SmsNotificationPhoneDoesNotMatch() {
        // Mock participant DAO w/ wrong phone number.
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withPhone(TestConstants.PHONE)
                .withPhoneVerified(true).build();
        when(mockParticipantService.getParticipant(mockApp, USER_ID, false)).thenReturn(participant);

        // Execute and validate.
        NotificationRegistration registration = getSmsNotificationRegistration();
        registration.setEndpoint("+14255550123");
        service.createRegistration(TEST_APP_ID, DUMMY_CONTEXT, registration);
    }

    @Test
    public void updateRegistration() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName(OS_NAME);
        doReturn(registration).when(mockRegistrationDao).updateRegistration(registration);
        
        NotificationRegistration result = service.updateRegistration(TEST_APP_ID, registration);
        verify(mockRegistrationDao).updateRegistration(registration);
        assertEquals(result, registration);
    }

    @Test
    public void deleteAllRegistrations() {
        // Mock dependencies.
        NotificationRegistration pushNotificationRegistration = getNotificationRegistration();
        pushNotificationRegistration.setGuid("push-notification-registration");

        NotificationRegistration smsNotificationRegistration = getSmsNotificationRegistration();
        smsNotificationRegistration.setGuid("sms-notification-registration");

        when(mockRegistrationDao.listRegistrations(HEALTH_CODE)).thenReturn(ImmutableList.of(
                pushNotificationRegistration, smsNotificationRegistration));

        // Execute.
        service.deleteAllRegistrations(TEST_APP_ID, HEALTH_CODE);

        // Verify dependencies.
        verify(mockNotificationTopicService).unsubscribeAll(TEST_APP_ID, HEALTH_CODE,
                "push-notification-registration");
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, "push-notification-registration");

        verify(mockNotificationTopicService).unsubscribeAll(TEST_APP_ID, HEALTH_CODE,
                "sms-notification-registration");
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, "sms-notification-registration");
    }
    
    @Test
    public void deleteRegistration() {
        service.deleteRegistration(TEST_APP_ID, HEALTH_CODE, GUID);
        verify(mockNotificationTopicService).unsubscribeAll(TEST_APP_ID, HEALTH_CODE, GUID);
        verify(mockRegistrationDao).deleteRegistration(HEALTH_CODE, GUID);
    }
    
    @Test
    public void serviceFixesSynonymOsNamesOnCreate() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).createPushNotificationRegistration(PLATFORM_ARN, registration);

        NotificationRegistration result = service.createRegistration(TEST_APP_ID, DUMMY_CONTEXT, registration);
        assertEquals(result.getOsName(), OperatingSystem.IOS);
    }
    
    @Test
    public void serviceFixesSynonymOsNamesOnUpdate() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName("iOS");
        doReturn(registration).when(mockRegistrationDao).updateRegistration(registration);
        
        NotificationRegistration result = service.updateRegistration(TEST_APP_ID, registration);
        assertEquals(result.getOsName(), OperatingSystem.IOS);
    }
    
    @Test(expectedExceptions = NotImplementedException.class)
    public void throwsUnimplementedExceptionIfPlatformHasNoARN() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setOsName(OperatingSystem.ANDROID);

        service.createRegistration(TEST_APP_ID, DUMMY_CONTEXT, registration);
    }

    @Test
    public void sendNotificationOK() {
        NotificationRegistration registration = getNotificationRegistration();
        registration.setEndpoint("endpointARN");
        List<NotificationRegistration> list = Lists.newArrayList(registration);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doReturn(mockPublishResult).when(mockSnsClient).publish(any());
        
        NotificationMessage message = getNotificationMessage();
        
        service.sendNotificationToUser(TEST_APP_ID, HEALTH_CODE, message);
        
        verify(mockSnsClient).publish(requestCaptor.capture());
        
        PublishRequest request = requestCaptor.getValue();
        assertEquals(request.getSubject(), message.getSubject());
        assertEquals(request.getMessage(), message.getMessage());
        assertEquals(request.getTargetArn(), "endpointARN");
    }
    
    @Test
    public void sendNotificationNoRegistration() {
        doReturn(Lists.newArrayList()).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        NotificationMessage message = getNotificationMessage();
        try {
            service.sendNotificationToUser(TEST_APP_ID, HEALTH_CODE, message);
            fail("Should have thrown exception.");
        } catch(BadRequestException e) {
            assertEquals(e.getMessage(), "Participant has not registered to receive push notifications.");
        }
    }
    
    // Publish to two devices, where one device fails but the other sends to the user. 
    // Method succeeds but returns the GUID of the failed call for reporting back to the user.
    @Test
    public void sendNotificationWithPartialErrors() {
        NotificationRegistration reg1 = getNotificationRegistration();
        NotificationRegistration reg2 = getNotificationRegistration();
        reg2.setGuid("registrationGuid2");
        List<NotificationRegistration> list = Lists.newArrayList(reg1, reg2);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        when(mockSnsClient.publish(any()))
            .thenReturn(mockPublishResult)
            .thenThrow(new InvalidParameterException("bad parameter"));
        
        NotificationMessage message = getNotificationMessage();
        Set<String> erroredNotifications = service.sendNotificationToUser(TEST_APP_ID, HEALTH_CODE, message);
        assertEquals(erroredNotifications.size(), 1);
        assertEquals(Iterables.getFirst(erroredNotifications, null), "registrationGuid2");        
    }
    
    // Publish to two devices, where all the devices fail. This should throw an exception as nothing 
    // was successfully returned to the user.
    @Test(expectedExceptions = BadRequestException.class)
    public void sendNotificationAmazonExceptionConverted() {
        NotificationRegistration reg1 = getNotificationRegistration();
        NotificationRegistration reg2 = getNotificationRegistration();
        reg2.setGuid("registrationGuid2"); // This has to be different
        List<NotificationRegistration> list = Lists.newArrayList(reg1, reg2);
        doReturn(list).when(mockRegistrationDao).listRegistrations(HEALTH_CODE);
        
        doThrow(new InvalidParameterException("bad parameter")).when(mockSnsClient).publish(any());
        
        NotificationMessage message = getNotificationMessage();
        service.sendNotificationToUser(TEST_APP_ID, HEALTH_CODE, message);
    }

    private static NotificationRegistration getSmsNotificationRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(HEALTH_CODE);
        registration.setProtocol(NotificationProtocol.SMS);
        registration.setEndpoint(TestConstants.PHONE.getNumber());
        return registration;
    }
}
