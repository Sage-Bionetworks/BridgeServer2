package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.getNotificationRegistration;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.DeleteEndpointRequest;
import com.amazonaws.services.sns.model.GetEndpointAttributesResult;
import com.amazonaws.services.sns.model.SetEndpointAttributesRequest;
import com.google.common.collect.Maps;

/**
 * This interacts with SNS and to test against SNS itself, we'd need to create client application 
 * registrations across all environments. Using mocks for this DAO test instead (DynamoDB is a 
 * known system at this point so mock tests are OK).
 */
public class DynamoNotificationRegistrationDaoTest {

    private static final String PLATFORM_ARN = "platformARN";
    private static final String GUID = "registrationGuid";
    private static final String HEALTH_CODE = "healthCode";
    private static final String PUSH_NOTIFICATION_ENDPOINT_ARN = "endpoint";
    private static final String PHONE_ENDPOINT = "+14255550123";
    private static final String DEVICE_ID = "deviceId";
    private static final String OS_NAME = "osName";
    private static final long CREATED_ON = 1484173675648L;
    private static final long MODIFIED_ON = 1484173687607L;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    AmazonSNS mockSnsClient;
    
    @Mock
    CreatePlatformEndpointResult mockCreatePlatformEndpointResult;
    
    @Mock
    GetEndpointAttributesResult mockGetEndpointAttributesResult;
    
    @Mock
    PaginatedQueryList<NotificationRegistration> paginatedQueryList;
    
    DynamoNotificationRegistrationDao dao;
    
    @Captor
    ArgumentCaptor<NotificationRegistration> notificationRegistrationCaptor;
    
    @Captor
    ArgumentCaptor<CreatePlatformEndpointRequest> createPlatformEndpointRequestCaptor;
    
    @Captor
    ArgumentCaptor<SetEndpointAttributesRequest> setEndpointAttributesRequestCaptor;
    
    @Captor
    ArgumentCaptor<DeleteEndpointRequest> deleteEndpointRequestCaptor;
    
    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<DynamoNotificationRegistration>> queryCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        dao = new DynamoNotificationRegistrationDao();
        dao.setNotificationRegistrationMapper(mockMapper);
        dao.setSnsClient(mockSnsClient);
    }
    
    @Test
    public void list() {
        mockQuery(TestUtils.getNotificationRegistration(), TestUtils.getNotificationRegistration());

        List<NotificationRegistration> list = dao.listRegistrations(HEALTH_CODE);
        
        assertEquals(list.size(), 2);
        DynamoDBQueryExpression<DynamoNotificationRegistration> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getHealthCode(), HEALTH_CODE);
    }
    
    // Opted here to return an empty list, as it's a list operation and we're not asking for specific registration
    @Test
    public void listWhenNoneExist() {
        mockQuery();

        List<NotificationRegistration> list = dao.listRegistrations(HEALTH_CODE);
        
        assertEquals(list.size(), 0);
        DynamoDBQueryExpression<DynamoNotificationRegistration> query = queryCaptor.getValue();
        assertEquals(query.getHashKeyValues().getHealthCode(), HEALTH_CODE);
    }
    
    @Test
    public void createPushNotification() {
        // No existing record
        mockQuery();

        doReturn(PUSH_NOTIFICATION_ENDPOINT_ARN).when(mockCreatePlatformEndpointResult).getEndpointArn();
        doReturn(mockCreatePlatformEndpointResult).when(mockSnsClient).createPlatformEndpoint(any());
        
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(HEALTH_CODE);
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        
        NotificationRegistration result = dao.createPushNotificationRegistration(PLATFORM_ARN, registration);
        
        verify(mockSnsClient).createPlatformEndpoint(createPlatformEndpointRequestCaptor.capture());
        
        CreatePlatformEndpointRequest snsRequest = createPlatformEndpointRequestCaptor.getValue();
        assertEquals(snsRequest.getPlatformApplicationArn(), PLATFORM_ARN);
        assertEquals(snsRequest.getToken(), DEVICE_ID);
        assertNull(snsRequest.getCustomUserData());
        
        assertNotNull(result.getGuid());
        assertNotEquals(result.getGuid(), GUID);
        
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        
        NotificationRegistration reg = notificationRegistrationCaptor.getValue();
        assertEquals(reg.getHealthCode(), HEALTH_CODE);
        assertEquals(reg.getGuid(), result.getGuid());
        assertEquals(reg.getEndpoint(), PUSH_NOTIFICATION_ENDPOINT_ARN);
        assertEquals(reg.getDeviceId(), DEVICE_ID);
        assertEquals(reg.getOsName(), OperatingSystem.IOS);
        assertTrue(reg.getCreatedOn() > 0L);
        assertTrue(reg.getModifiedOn() > 0L);
    }
    
    // In this case, we want to see the GUID returned and no duplicate record created. So 
    // that's the only part that's additional to the createNotifications() test when new.
    @Test
    public void createPushNotificationWhenItExists() {
        mockQuery(getNotificationRegistration());

        doReturn(PUSH_NOTIFICATION_ENDPOINT_ARN).when(mockCreatePlatformEndpointResult).getEndpointArn();
        doReturn(mockCreatePlatformEndpointResult).when(mockSnsClient).createPlatformEndpoint(any());
        
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setHealthCode(HEALTH_CODE);
        registration.setDeviceId(DEVICE_ID);
        registration.setOsName(OperatingSystem.IOS);
        
        NotificationRegistration result = dao.createPushNotificationRegistration(PLATFORM_ARN, registration);
        
        assertEquals(result.getGuid(), GUID);
        
        // The save needs to use the same GUID and HEALTH_CODE or it'll be a duplicate
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration reg = notificationRegistrationCaptor.getValue();
        assertEquals(reg.getHealthCode(), HEALTH_CODE);
        assertEquals(reg.getGuid(), GUID);
    }

    @Test
    public void createSmsRegistration() {
        // No existing record.
        mockQuery();

        // Execute.
        NotificationRegistration result = dao.createRegistration(getSmsNotificationRegistration());

        // Validate saved registration.
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration savedRegistration = notificationRegistrationCaptor.getValue();
        assertEquals(savedRegistration.getHealthCode(), HEALTH_CODE);
        assertEquals(savedRegistration.getProtocol(), NotificationProtocol.SMS);
        assertEquals(savedRegistration.getEndpoint(), PHONE_ENDPOINT);
        assertNotEquals(savedRegistration.getGuid(), GUID);
        assertTrue(savedRegistration.getCreatedOn() > 0L);
        assertNotEquals(savedRegistration.getCreatedOn(), CREATED_ON);
        assertTrue(savedRegistration.getModifiedOn() > 0L);

        assertSame(savedRegistration, result);
    }

    @Test
    public void createSmsRegistrationAlreadyExists() {
        // Create existing record.
        NotificationRegistration existingRegistration = getSmsNotificationRegistration();
        existingRegistration.setGuid(GUID);
        existingRegistration.setCreatedOn(CREATED_ON);
        mockQuery(existingRegistration);

        // Execute.
        NotificationRegistration result = dao.createRegistration(getSmsNotificationRegistration());

        // Validate saved registration.
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration savedRegistration = notificationRegistrationCaptor.getValue();
        assertEquals(savedRegistration.getHealthCode(), HEALTH_CODE);
        assertEquals(savedRegistration.getProtocol(), NotificationProtocol.SMS);
        assertEquals(savedRegistration.getEndpoint(), PHONE_ENDPOINT);
        assertEquals(savedRegistration.getGuid(), GUID);
        assertEquals(savedRegistration.getCreatedOn(), CREATED_ON);
        assertTrue(savedRegistration.getModifiedOn() > 0L);

        assertSame(savedRegistration, result);
    }

    @Test
    public void get() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        NotificationRegistration returned = dao.getRegistration(HEALTH_CODE, GUID);
        
        verify(mockMapper).load(notificationRegistrationCaptor.capture());
        NotificationRegistration hashKey = notificationRegistrationCaptor.getValue();
        assertEquals(hashKey.getHealthCode(), HEALTH_CODE);
        assertEquals(hashKey.getGuid(), GUID);
        assertEquals(returned, registration);
    }
    
    @Test
    public void getWhenItDoesNotExist() {
        doReturn(null).when(mockMapper).load(any());
        
        try {
            dao.getRegistration(HEALTH_CODE, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals(e.getMessage(), "NotificationRegistration not found.");
        }
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateNotExists() {
        // Mock mapper.
        when(mockMapper.load(any())).thenReturn(null);

        // Execute. Will throw.
        dao.updateRegistration(getNotificationRegistration());
    }

    @Test
    public void updateSmsNotificationDoesNothing() {
        // Mock mapper.
        NotificationRegistration existingRegistration = getSmsNotificationRegistration();
        existingRegistration.setGuid(GUID);
        when(mockMapper.load(any())).thenReturn(existingRegistration);

        // Execute.
        NotificationRegistration registrationToUpdate = getSmsNotificationRegistration();
        registrationToUpdate.setGuid(GUID);
        NotificationRegistration result = dao.updateRegistration(registrationToUpdate);
        assertSame(result, existingRegistration);

        // No back-ends called.
        verifyZeroInteractions(mockSnsClient);
        verify(mockMapper, never()).save(any());
    }

    @Test
    public void updateNoChange() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("Token", DEVICE_ID);
        map.put("Enabled", "true");
        map.put("CustomUserData", HEALTH_CODE);
        doReturn(map).when(mockGetEndpointAttributesResult).getAttributes();
        doReturn(mockGetEndpointAttributesResult).when(mockSnsClient).getEndpointAttributes(any());
        
        NotificationRegistration result = dao.updateRegistration(registration);
        assertSame(result, registration);
        
        verify(mockSnsClient, never()).setEndpointAttributes(any());
        verify(mockMapper, never()).save(any());
    }
    
    @Test
    public void updateWhenTokenHasChanged() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("Token", "this is not the device id");
        map.put("Enabled", "false");
        map.put("CustomUserData", HEALTH_CODE);
        doReturn(map).when(mockGetEndpointAttributesResult).getAttributes();
        doReturn(mockGetEndpointAttributesResult).when(mockSnsClient).getEndpointAttributes(any());
        
        dao.updateRegistration(registration);
        
        verify(mockSnsClient).setEndpointAttributes(setEndpointAttributesRequestCaptor.capture());
        SetEndpointAttributesRequest request = setEndpointAttributesRequestCaptor.getValue();
        assertEquals(request.getEndpointArn(), registration.getEndpoint());

        Map<String,String> attributes = request.getAttributes();
        assertEquals(attributes.get("Token"), DEVICE_ID);
        assertEquals(attributes.get("Enabled"), "true");
        assertNull(attributes.get("CustomUserData"));
        
        verify(mockMapper).save(notificationRegistrationCaptor.capture());
        NotificationRegistration persisted = notificationRegistrationCaptor.getValue();
        assertEquals(persisted.getGuid(), GUID);
        assertEquals(persisted.getHealthCode(), HEALTH_CODE);
        assertEquals(persisted.getEndpoint(), PUSH_NOTIFICATION_ENDPOINT_ARN);
        assertEquals(persisted.getDeviceId(), DEVICE_ID);
        assertEquals(persisted.getOsName(), OS_NAME);
        assertEquals(persisted.getCreatedOn(), CREATED_ON);
        assertNotEquals(persisted.getModifiedOn(), MODIFIED_ON); // modified is changed
        assertTrue(persisted.getModifiedOn() > 0L);
    }
    
    @Test
    public void updateWhenNew() {
        doReturn(null).when(mockMapper).load(any());
        
        NotificationRegistration registration = getNotificationRegistration();
        try {
            dao.updateRegistration(registration);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(mockMapper, never()).save(any());
        verify(mockSnsClient, never()).setEndpointAttributes(any());
    }
    
    @Test
    public void delete() {
        NotificationRegistration registration = getNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());
        
        dao.deleteRegistration(HEALTH_CODE, GUID);
        
        verify(mockMapper).delete(notificationRegistrationCaptor.capture());
        NotificationRegistration del = notificationRegistrationCaptor.getValue();
        assertEquals(del.getGuid(), GUID);
        assertEquals(del.getHealthCode(), HEALTH_CODE);
        
        verify(mockSnsClient).deleteEndpoint(deleteEndpointRequestCaptor.capture());
        DeleteEndpointRequest request = deleteEndpointRequestCaptor.getValue();
        assertEquals(request.getEndpointArn(), PUSH_NOTIFICATION_ENDPOINT_ARN);
    }
    
    @Test
    public void deleteWhenDoesNotExist() {
        doReturn(null).when(mockMapper).load(any());
        
        try {
            dao.deleteRegistration(HEALTH_CODE, GUID);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(mockMapper, never()).save(any());
        verify(mockSnsClient, never()).setEndpointAttributes(any());
    }

    @Test
    public void deleteSmsNotification() {
        // Set up mocks.
        NotificationRegistration registration = getSmsNotificationRegistration();
        doReturn(registration).when(mockMapper).load(any());

        // Execute.
        dao.deleteRegistration(HEALTH_CODE, GUID);

        // Verify DDB mapper.
        verify(mockMapper).delete(notificationRegistrationCaptor.capture());
        NotificationRegistration deletedRegistration = notificationRegistrationCaptor.getValue();
        assertEquals(deletedRegistration.getHealthCode(), HEALTH_CODE);
        assertEquals(deletedRegistration.getProtocol(), NotificationProtocol.SMS);
        assertEquals(deletedRegistration.getEndpoint(), PHONE_ENDPOINT);

        // Verify we don't call SNS delete endpoint for SMS registrations.
        verify(mockSnsClient, never()).deleteEndpoint(any());
    }

    private void mockQuery(NotificationRegistration... registrations) {
        List<NotificationRegistration> registrationList = ImmutableList.copyOf(registrations);
        doReturn(paginatedQueryList).when(mockMapper).query(eq(DynamoNotificationRegistration.class),
                queryCaptor.capture());
        doReturn(registrationList.stream()).when(paginatedQueryList).stream();
    }

    private static NotificationRegistration getSmsNotificationRegistration() {
        NotificationRegistration existingRegistration = NotificationRegistration.create();
        existingRegistration.setHealthCode(HEALTH_CODE);
        existingRegistration.setProtocol(NotificationProtocol.SMS);
        existingRegistration.setEndpoint(PHONE_ENDPOINT);
        return existingRegistration;
    }
}
