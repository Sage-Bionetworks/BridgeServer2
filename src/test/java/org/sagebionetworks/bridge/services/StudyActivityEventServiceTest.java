package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.INSTALL_LINK_SENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.services.StudyActivityEventService.CREATED_ON_FIELD;
import static org.sagebionetworks.bridge.services.StudyActivityEventService.ENROLLMENT_FIELD;
import static org.sagebionetworks.bridge.services.StudyActivityEventService.INSTALL_LINK_SENT_FIELD;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

public class StudyActivityEventServiceTest extends Mockito {
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
    private static final DateTime TIMELINE_RETRIEVED_TS = DateTime.parse("2019-03-05T01:34:53.395Z");
    private static final DateTime ENROLLMENT_TS = DateTime.parse("2019-10-14T01:34:53.395Z");
    private static final DateTime INSTALL_LINK_SENT_TS = DateTime.parse("2018-10-11T03:34:53.395Z");

    @Mock
    StudyActivityEventDao mockDao;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    ActivityEventService mockActivityEventService;
    
    @InjectMocks
    @Spy
    StudyActivityEventService service;
    
    @Captor
    ArgumentCaptor<StudyActivityEvent> eventCaptor;
    
    Study study;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        study = Study.create();
        study.setCustomEvents(ImmutableList.of(
            new StudyCustomEvent("event1", MUTABLE),
            new StudyCustomEvent("event2", IMMUTABLE)
        ));
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        doReturn(CREATED_ON).when(service).getCreatedOn();
    }
    
    private StudyActivityEventRequest makeRequest() { 
        return new StudyActivityEventRequest()
                .appId(TEST_APP_ID)
                .studyId(TEST_STUDY_ID)
                .userId(TEST_USER_ID);
    }
    
    @Test
    public void deleteCustomEvent() {
        StudyActivityEventRequest request = makeRequest()
                .objectType(CUSTOM).objectId("event1");
        
        StudyActivityEvent persistedEvent = new StudyActivityEvent();
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any()))
            .thenReturn(persistedEvent);

        service.deleteCustomEvent(request);
        
        verify(mockDao).deleteCustomEvent(eventCaptor.capture());
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:event1");
    }
    
    @Test
    public void deleteCustomEvent_eventIsImmutable() {
        StudyActivityEventRequest request = makeRequest()
                .objectType(CUSTOM).objectId("event2");
        
        StudyActivityEvent persistedEvent = new StudyActivityEvent();
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any()))
            .thenReturn(persistedEvent);

        service.deleteCustomEvent(request);
        
        verify(mockDao, never()).deleteCustomEvent(any());
    }

    @Test
    public void deleteCustomEvent_noEventPersisted() {
        StudyActivityEventRequest request = makeRequest().objectId("event2")
                .objectType(CUSTOM);
        
        // no event returned from a query of the DAO 

        service.deleteCustomEvent(request);
        
        verify(mockDao, never()).deleteCustomEvent(any());
    }
    
    @Test
    public void deleteCustomEvent_eventInvalid() {
        // this is not a custom event. Object ID needs to be included or you
        // get (correctly) a validation error for not including an eventId.
        StudyActivityEventRequest request = makeRequest().objectType(CUSTOM);
        
        try {
            service.deleteCustomEvent(request);
            fail("should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals(e.getErrors().get("eventId").get(0), "eventId " + INVALID_EVENT_ID);
        }
    }
    
    @Test
    public void publishCustomEvent_eventIsImmutable() {
        StudyActivityEventRequest request = makeRequest()
                .objectType(CUSTOM).objectId("event2").timestamp(MODIFIED_ON);
        
        StudyActivityEvent persistedEvent = new StudyActivityEvent();
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any()))
            .thenReturn(persistedEvent);

        service.publishEvent(request);
        
        verify(mockDao, never()).publishEvent(any());
    }
    
    @Test
    public void publishEvent() {
        StudyActivityEventRequest request = makeRequest()
                .objectType(CUSTOM).objectId("event1").timestamp(MODIFIED_ON)
                .clientTimeZone("America/Los_Angeles");
        
        service.publishEvent(request);
        
        verify(mockDao).publishEvent(eventCaptor.capture());
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:event1");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
    }
    
    @Test
    public void publishEvent_noEventPersisted() {
        // This event doesn’t update unless there is no persisted event. Here
        // it persists
        StudyActivityEventRequest request = makeRequest().objectId("timeline_retrieved")
                .timestamp(TIMELINE_RETRIEVED_TS).objectType(TIMELINE_RETRIEVED);
        
        service.publishEvent(request);
        
        verify(mockDao).publishEvent(any());
    }
    
    @Test
    public void publishEvent_eventPersisted() {
        // This event doesn’t update unless there is no persisted event. Here
        // it does not persist.
        StudyActivityEventRequest request = makeRequest().objectId(ENROLLMENT_FIELD)
                .timestamp(ENROLLMENT_TS).objectType(ENROLLMENT);
        
        StudyActivityEvent persistedEvent = new StudyActivityEvent();
        when(mockDao.getRecentStudyActivityEvent(any(), any(), any()))
            .thenReturn(persistedEvent);

        service.publishEvent(request);
        
        verify(mockDao, never()).publishEvent(any());
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void publishEvent_eventInvalid() {
        StudyActivityEventRequest request = makeRequest();
        
        service.publishEvent(request);
    }
    
//    @Test
//    public void publishEvent_autoEventsAlsoPublished() {
//        StudyActivityEventRequest request = makeRequest()
//                .objectType(ENROLLMENT).timestamp(ENROLLMENT_TS);
//        
//        service.publishEvent(request);
//        
//        verify(mockDao, times(2)).publishEvent(eventCaptor.capture());
//        StudyActivityEvent event = eventCaptor.getAllValues().get(0);
//        assertEquals(event.getAppId(), TEST_APP_ID);
//        assertEquals(event.getStudyId(), TEST_STUDY_ID);
//        assertEquals(event.getUserId(), TEST_USER_ID);
//        assertEquals(event.getEventId(), ENROLLMENT_FIELD);
//        assertEquals(event.getTimestamp(), ENROLLMENT_TS);
//        assertEquals(event.getCreatedOn(), CREATED_ON);
//
//        event = eventCaptor.getAllValues().get(1);
//        assertEquals(event.getAppId(), TEST_APP_ID);
//        assertEquals(event.getStudyId(), TEST_STUDY_ID);
//        assertEquals(event.getUserId(), TEST_USER_ID);
//        assertEquals(event.getEventId(), "custom:event3");
//        assertEquals(event.getTimestamp(), ENROLLMENT_TS.plusWeeks(3));
//        assertEquals(event.getCreatedOn(), CREATED_ON);
//    }
//    
//    @Test
//    public void publishEvent_autoEventsPublishWithCustomEvent() {
//        StudyActivityEventRequest request = makeRequest()
//                .objectType(CUSTOM).objectId("event2").timestamp(ENROLLMENT_TS);
//        
//        service.publishEvent(request);
//        
//        verify(mockDao, times(2)).publishEvent(eventCaptor.capture());
//        StudyActivityEvent event = eventCaptor.getAllValues().get(0);
//        assertEquals(event.getAppId(), TEST_APP_ID);
//        assertEquals(event.getStudyId(), TEST_STUDY_ID);
//        assertEquals(event.getUserId(), TEST_USER_ID);
//        assertEquals(event.getEventId(), "custom:event2");
//        assertEquals(event.getTimestamp(), ENROLLMENT_TS);
//        assertEquals(event.getCreatedOn(), CREATED_ON);
//
//        event = eventCaptor.getAllValues().get(1);
//        assertEquals(event.getAppId(), TEST_APP_ID);
//        assertEquals(event.getStudyId(), TEST_STUDY_ID);
//        assertEquals(event.getUserId(), TEST_USER_ID);
//        assertEquals(event.getEventId(), "custom:event4");
//        assertEquals(event.getTimestamp(), ENROLLMENT_TS.plusMonths(6));
//        assertEquals(event.getCreatedOn(), CREATED_ON);
//    }
    
    @Test
    public void getRecentStudyActivityEvents() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId(ENROLLMENT_FIELD);
        event1.setTimestamp(ENROLLMENT_TS);
        StudyActivityEvent event2 = new StudyActivityEvent();
        event2.setEventId("timeline_retrieved");
        event2.setTimestamp(TIMELINE_RETRIEVED_TS);
        
        List<StudyActivityEvent> list = Lists.newArrayList(event1, event2);
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, CREATED_ON, 
                INSTALL_LINK_SENT_FIELD, INSTALL_LINK_SENT_TS);
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        Account account = Account.create();
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        ResourceList<StudyActivityEvent> retValue = service
                .getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        assertEquals(retValue.getItems().size(), 4);
        
        StudyActivityEvent createdOn = TestUtils.findByEventId(
                retValue.getItems(), ActivityEventObjectType.CREATED_ON);
        assertEquals(createdOn.getTimestamp(), CREATED_ON);

        StudyActivityEvent installLinkSentOn = TestUtils.findByEventId(
                retValue.getItems(), INSTALL_LINK_SENT);
        assertEquals(installLinkSentOn.getTimestamp(), INSTALL_LINK_SENT_TS);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecentStudyActivityEvents_noAccount() {
        service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void getStudyActivityEventHistory() {
        List<StudyActivityEvent> list = new ArrayList<>();
        PagedResourceList<StudyActivityEvent> page = new PagedResourceList<>(list, 100);
        when(mockDao.getStudyActivityEventHistory(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event1", 10, 100)).thenReturn(page);
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, "custom:event1", 10, 100);
        assertSame(retValue, page);
        assertEquals(retValue.getRequestParams().get("offsetBy"), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get("pageSize"), Integer.valueOf(100));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_negativeOffset() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "event1", -10, 100);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_minPageSize() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "event1", 0, API_MINIMUM_PAGE_SIZE-1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_maxPageSize() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "event1", 0, API_MAXIMUM_PAGE_SIZE+1);
    }
    
    @Test
    public void getStudyActivityEventHistory_createdOn() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setHealthCode(HEALTH_CODE);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, CREATED_ON);
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, CREATED_ON_FIELD, 0, 50);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0).getTimestamp(), CREATED_ON);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getStudyActivityEventHistory_syntheticEventNoAccount() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
        
        service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, CREATED_ON_FIELD, 0, 50);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = "Event ID is required")
    public void getStudyActivityEventHistory_nullEventId() {
        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, null, 0, 50);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*"+INVALID_EVENT_ID+".*")
    public void getStudyActivityEventHistory_invalidEventId() {
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, "nonsense", 0, 50);
    }
    
    @Test
    public void getStudyActivityEventHistory_noPaging() {
        StudyActivityEvent event = new StudyActivityEvent();
        event.setEventId("custom:event1");
        event.setTimestamp(MODIFIED_ON);

        when(mockDao.getStudyActivityEventHistory(TEST_USER_ID, TEST_STUDY_ID, 
                "custom:event1", null, null)).thenReturn(
                        new PagedResourceList<>(Lists.newArrayList(event), 0, true));
                
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(
                ACCOUNT_ID, TEST_STUDY_ID, "event1", null, null);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0), event);
        
        verify(mockDao).getStudyActivityEventHistory(TEST_USER_ID, TEST_STUDY_ID, "custom:event1", null, null);
    }
    
    @Test
    public void addEnrollmentToRecentIfMissing() {
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setHealthCode(HEALTH_CODE);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        Map<String, DateTime> map = ImmutableMap.of(CREATED_ON_FIELD, CREATED_ON);
        when(mockActivityEventService.getActivityEventMap(TEST_APP_ID, HEALTH_CODE)).thenReturn(map);
        
        ResourceList<StudyActivityEvent> retValue = service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        assertEquals(retValue.getItems().size(), 2);
        assertEquals(retValue.getItems().get(0).getEventId(), ENROLLMENT_FIELD);
        assertEquals(retValue.getItems().get(0).getTimestamp(), MODIFIED_ON);
        assertEquals(retValue.getItems().get(1).getEventId(), CREATED_ON_FIELD);
        assertEquals(retValue.getItems().get(1).getTimestamp(), CREATED_ON);
    }

    @Test
    public void doNotAddEnrollmentToRecentIfNotMissing() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));
        
        List<StudyActivityEvent> list = Lists.newArrayList(new StudyActivityEvent(ENROLLMENT_FIELD, CREATED_ON));
        when(mockDao.getRecentStudyActivityEvents(TEST_USER_ID, TEST_STUDY_ID))
            .thenReturn(list);
        
        ResourceList<StudyActivityEvent> retValue = service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        assertEquals(retValue.getItems().get(0).getEventId(), ENROLLMENT_FIELD);
        assertEquals(retValue.getItems().get(0).getTimestamp(), CREATED_ON); // not modifiedOn
    }

    @Test
    public void addEnrollmentToHistoryIfMissing() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        PagedResourceList<StudyActivityEvent> results = new PagedResourceList<>(ImmutableList.of(), 0, true);
        when(mockDao.getStudyActivityEventHistory(any(), any(), any(), any(), any())).thenReturn(results);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, ENROLLMENT_FIELD, null, null);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(1));
        assertEquals(retValue.getItems().get(0).getTimestamp(), MODIFIED_ON);
    }

    @Test
    public void doNotAddEnrollmentToHistoryIfNotMissing() { 
        Enrollment en = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        en.setEnrolledOn(MODIFIED_ON);
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        account.setEnrollments(ImmutableSet.of(en));
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(Optional.of(account));

        PagedResourceList<StudyActivityEvent> results = new PagedResourceList<>(
                ImmutableList.of(new StudyActivityEvent(ENROLLMENT_FIELD, CREATED_ON)), 1, true);
        when(mockDao.getStudyActivityEventHistory(any(), any(), any(), any(), any())).thenReturn(results);
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(ACCOUNT_ID, TEST_STUDY_ID, ENROLLMENT_FIELD, null, null);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getTotal(), Integer.valueOf(1));
        assertEquals(retValue.getItems().get(0).getTimestamp(), CREATED_ON); // not modifiedOn
    }
}
