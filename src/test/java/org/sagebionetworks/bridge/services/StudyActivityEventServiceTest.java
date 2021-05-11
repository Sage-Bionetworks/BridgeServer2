package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

import org.sagebionetworks.bridge.BridgeUtils;
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
import org.sagebionetworks.bridge.models.apps.App;

public class StudyActivityEventServiceTest extends Mockito {
    private static final DateTime TIMELINE_RETRIEVED_TS = DateTime.parse("2019-03-05T01:34:53.395Z");
    private static final DateTime ENROLLMENT_TS = DateTime.parse("2019-10-14T01:34:53.395Z");

    @Mock
    StudyActivityEventDao mockDao;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    AccountService mockAccountService;
    
    @InjectMocks
    @Spy
    StudyActivityEventService service;
    
    @Captor
    ArgumentCaptor<StudyActivityEvent> eventCaptor;
    
    App app;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        app = App.create();
        app.setCustomEvents(ImmutableMap.of(
                "event1", MUTABLE, "event2", IMMUTABLE));
        app.setAutomaticCustomEvents(ImmutableMap.of(
                "event3", "enrollment:P3W", "event4", "enrollment:P6M"));
        
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
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
            assertEquals(e.getErrors().get("eventId").get(0), "eventId cannot be null or blank");
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
        assertEquals(event.getUpdateType(), MUTABLE);
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
        StudyActivityEventRequest request = makeRequest().objectId("enrollment")
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
    
    @Test
    public void publishEvent_autoEventsAlsoPublished() {
        StudyActivityEventRequest request = makeRequest()
                .objectType(ENROLLMENT).timestamp(ENROLLMENT_TS);
        
        service.publishEvent(request);
        
        verify(mockDao, times(3)).publishEvent(eventCaptor.capture());
        StudyActivityEvent event = eventCaptor.getAllValues().get(0);
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "enrollment");
        assertEquals(event.getTimestamp(), ENROLLMENT_TS);
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getUpdateType(), IMMUTABLE);

        event = eventCaptor.getAllValues().get(1);
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:event3");
        assertEquals(event.getTimestamp(), ENROLLMENT_TS.plusWeeks(3));
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getUpdateType(), MUTABLE);

        event = eventCaptor.getAllValues().get(2);
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "custom:event4");
        assertEquals(event.getTimestamp(), ENROLLMENT_TS.plusMonths(6));
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getUpdateType(), MUTABLE);
    }
    
    @Test
    public void getRecentStudyActivityEvents() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("enrollment");
        event1.setTimestamp(ENROLLMENT_TS);
        StudyActivityEvent event2 = new StudyActivityEvent();
        event2.setEventId("timeline_retrieved");
        event2.setTimestamp(TIMELINE_RETRIEVED_TS);
        
        List<StudyActivityEvent> list = Lists.newArrayList(event1, event2);
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.of(account));
        
        ResourceList<StudyActivityEvent> retValue = service
                .getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        assertEquals(retValue.getItems().size(), 4);
        
        StudyActivityEvent createdOn = BridgeUtils.findByEventType(
                retValue.getItems(), ActivityEventObjectType.CREATED_ON);
        assertEquals(createdOn.getTimestamp(), CREATED_ON);
                
        StudyActivityEvent studyStartDate = BridgeUtils.findByEventType(
                retValue.getItems(), ActivityEventObjectType.STUDY_START_DATE);
        assertEquals(studyStartDate.getTimestamp(), TIMELINE_RETRIEVED_TS);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getRecentStudyActivityEvents_noAccount() {
        service.getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void getRecentStudyActivityEvents_studyStartDateFromTimelineRetrieved() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("enrollment");
        event1.setTimestamp(ENROLLMENT_TS);
        StudyActivityEvent event2 = new StudyActivityEvent();
        event2.setEventId("timeline_retrieved");
        event2.setTimestamp(TIMELINE_RETRIEVED_TS);
        
        List<StudyActivityEvent> list = Lists.newArrayList(event1, event2);
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.of(account));
        
        ResourceList<StudyActivityEvent> retValue = service
                .getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        
        StudyActivityEvent studyStartDate = BridgeUtils.findByEventType(
                retValue.getItems(), ActivityEventObjectType.STUDY_START_DATE);
        assertEquals(studyStartDate.getTimestamp(), TIMELINE_RETRIEVED_TS);
    }
    
    @Test
    public void getRecentStudyActivityEvents_studyStartDateFromEnrollment() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("enrollment");
        event1.setTimestamp(ENROLLMENT_TS);
        
        List<StudyActivityEvent> list = Lists.newArrayList(event1);
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(list);
        
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.of(account));
        
        ResourceList<StudyActivityEvent> retValue = service
                .getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        
        StudyActivityEvent studyStartDate = BridgeUtils.findByEventType(
                retValue.getItems(), ActivityEventObjectType.STUDY_START_DATE);
        assertEquals(studyStartDate.getTimestamp(), ENROLLMENT_TS);
    }
    
    @Test
    public void getRecentStudyActivityEvents_studyStartDateFromAccountCreatedOn() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.of(account));
        
        ResourceList<StudyActivityEvent> retValue = service
                .getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        
        StudyActivityEvent studyStartDate = BridgeUtils.findByEventType(
                retValue.getItems(), ActivityEventObjectType.STUDY_START_DATE);
        assertEquals(studyStartDate.getTimestamp(), CREATED_ON);
    }

    @Test
    public void getStudyActivityEventHistory() {
        List<StudyActivityEvent> list = ImmutableList.of();
        PagedResourceList<StudyActivityEvent> page = new PagedResourceList<>(list, 100);
        when(mockDao.getStudyActivityEventHistory(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event1", 10, 100)).thenReturn(page);
        
        StudyActivityEventRequest request = makeRequest().objectId("event1");
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(request, 10, 100);
        assertSame(retValue, page);
        assertEquals(retValue.getRequestParams().get("offsetBy"), Integer.valueOf(10));
        assertEquals(retValue.getRequestParams().get("pageSize"), Integer.valueOf(100));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_negativeOffset() {
        StudyActivityEventRequest request = makeRequest().objectId("event1");
        service.getStudyActivityEventHistory(request, -10, 100);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_minPageSize() {
        StudyActivityEventRequest request = makeRequest().objectId("event1");
        service.getStudyActivityEventHistory(request, 0, API_MINIMUM_PAGE_SIZE-1);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void getStudyActivityEventHistory_maxPageSize() {
        StudyActivityEventRequest request = makeRequest().objectId("event1");
        service.getStudyActivityEventHistory(request, 0, API_MAXIMUM_PAGE_SIZE+1);
        
    }
    
    @Test
    public void getStudyActivityEventHistory_createdOn() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.of(account));
        
        StudyActivityEventRequest request = makeRequest().objectId("created_on");
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(request, 0, 50);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0).getTimestamp(), CREATED_ON);
    }

    @Test
    public void getStudyActivityEventHistory_studyStartDate() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("enrollment");
        event1.setTimestamp(ENROLLMENT_TS);

        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of(event1));
        
        Account account = Account.create();
        account.setCreatedOn(CREATED_ON);
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.of(account));
        
        StudyActivityEventRequest request = makeRequest().objectId("study_start_date");
        
        PagedResourceList<StudyActivityEvent> retValue = service.getStudyActivityEventHistory(request, 0, 50);
        assertEquals(retValue.getItems().size(), 1);
        assertEquals(retValue.getItems().get(0).getTimestamp(), ENROLLMENT_TS);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Account not found.")
    public void getStudyActivityEventHistory_syntheticEventNoAccount() {
        when(mockDao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID)).thenReturn(ImmutableList.of());
        
        when(mockAccountService.getAccountNoFilter(AccountId.forId(
                TEST_APP_ID, TEST_USER_ID))).thenReturn(Optional.empty());
        
        StudyActivityEventRequest request = makeRequest().objectId("study_start_date");
        
        service.getStudyActivityEventHistory(request, 0, 50);
    }
    
    @Test
    public void getStudyActivityEventHistory_noPaging() {
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("custom:event1");
        event1.setTimestamp(MODIFIED_ON);

        when(mockDao.getStudyActivityEventHistory(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event1", null, null))
            .thenReturn(new PagedResourceList<>(ImmutableList.of(event1), 0, true));
        
        StudyActivityEventRequest request = makeRequest().objectId("event1");
        
        PagedResourceList<StudyActivityEvent> retValue = service
                .getStudyActivityEventHistory(request, null, null);
        assertEquals(retValue.getItems().size(), 1);
        
        verify(mockDao).getStudyActivityEventHistory(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event1", null, null);
    }
}
