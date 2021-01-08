package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.testng.Assert.assertEquals;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentDetail;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.EnrollmentService;

public class StudyParticipantControllerTest extends Mockito {
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);;

    @Mock
    AppService mockAppService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    EnrollmentService mockEnrollmentService;
    
    @Mock
    ActivityEventService mockActivityEventService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @InjectMocks
    @Spy
    StudyParticipantController controller;
    
    UserSession session;
    
    Account account;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockResponse).when(controller).response();
        doReturn(mockRequest).when(controller).request();
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).build());
        
        account = Account.create();
    }
    
    @Test
    public void getActivityEvents() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);
        
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        
        List<ActivityEvent> events = ImmutableList.of(new DynamoActivityEvent.Builder()
                .withObjectType(ENROLLMENT)
                .withTimestamp(CREATED_ON)
                .withHealthCode(HEALTH_CODE).build());
        when(mockActivityEventService.getActivityEventList(TEST_APP_ID, HEALTH_CODE, TEST_STUDY_ID)).thenReturn(events);
        
        String retValue = controller.getActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
        
        ResourceList<ActivityEvent> retList = BridgeObjectMapper.get().readValue(retValue, new TypeReference<ResourceList<ActivityEvent>>() {});
        assertEquals(retList.getItems().size(), 1);
        assertEquals(retList.getItems().get(0).getEventId(), "enrollment");
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getActivityEventsAccountNotFound() throws Exception { 
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);
        
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(null);
        
        controller.getActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
    }

    @Test
    public void createActivityEvent() throws Exception {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);

        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        
        CustomActivityEventRequest event = new CustomActivityEventRequest.Builder()
                .withEventKey("eventKey")
                .withTimestamp(CREATED_ON).build();
        TestUtils.mockRequestBody(mockRequest, event);
        
        StatusMessage retValue = controller.createActivityEvent(TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(retValue, StudyParticipantController.EVENT_RECORDED_MSG);
        
        verify(mockActivityEventService).publishCustomEvent(app, HEALTH_CODE,
                "eventKey", CREATED_ON, TEST_STUDY_ID);
    }
    
    @Test
    public void getSelfActivityEvents() throws Exception {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID) // ID is drawn from the session so add it
                .withHealthCode(HEALTH_CODE).build());
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        
        List<ActivityEvent> events = ImmutableList.of(new DynamoActivityEvent.Builder()
                .withObjectType(ENROLLMENT)
                .withTimestamp(CREATED_ON)
                .withHealthCode(HEALTH_CODE).build());
        when(mockActivityEventService.getActivityEventList(TEST_APP_ID, HEALTH_CODE, TEST_STUDY_ID)).thenReturn(events);
        
        String retValue = controller.getSelfActivityEvents(TEST_STUDY_ID);
        
        ResourceList<ActivityEvent> retList = BridgeObjectMapper.get().readValue(retValue, new TypeReference<ResourceList<ActivityEvent>>() {});
        assertEquals(retList.getItems().size(), 1);
        assertEquals(retList.getItems().get(0).getEventId(), "enrollment");
    }

    @Test
    public void createSelfActivityEvent() throws Exception {
        session.setParticipant(new StudyParticipant.Builder()
                .withId(TEST_USER_ID) // ID is drawn from the session so add it
                .withHealthCode(HEALTH_CODE).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();

        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        List<EnrollmentDetail> list = ImmutableList.of(new EnrollmentDetail(en, null, null, null));
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        
        CustomActivityEventRequest event = new CustomActivityEventRequest.Builder()
                .withEventKey("eventKey")
                .withTimestamp(CREATED_ON).build();
        TestUtils.mockRequestBody(mockRequest, event);
        
        StatusMessage retValue = controller.createSelfActivityEvent(TEST_STUDY_ID);
        assertEquals(retValue, StudyParticipantController.EVENT_RECORDED_MSG);
        
        verify(mockActivityEventService).publishCustomEvent(app, HEALTH_CODE,
                "eventKey", CREATED_ON, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getActivityEventsForParticipantNotInStudy() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(RESEARCHER);
        
        when(mockEnrollmentService.getEnrollmentsForUser(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID))
            .thenReturn(ImmutableList.of());
        when(mockAccountService.getHealthCodeForAccount(ACCOUNT_ID)).thenReturn(HEALTH_CODE);
        
        List<ActivityEvent> events = ImmutableList.of(new DynamoActivityEvent.Builder()
                .withObjectType(ENROLLMENT)
                .withTimestamp(CREATED_ON)
                .withHealthCode(HEALTH_CODE).build());
        when(mockActivityEventService.getActivityEventList(TEST_APP_ID, HEALTH_CODE, TEST_STUDY_ID)).thenReturn(events);
        
        controller.getActivityEvents(TEST_STUDY_ID, TEST_USER_ID);
    }
}
