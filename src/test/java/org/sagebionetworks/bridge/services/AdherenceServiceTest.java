package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.getAdherenceRecord;
import static org.sagebionetworks.bridge.models.ResourceList.ADHERENCE_RECORD_TYPE;
import static org.sagebionetworks.bridge.models.ResourceList.ASSESSMENT_IDS;
import static org.sagebionetworks.bridge.models.ResourceList.CURRENT_TIMESTAMPS_ONLY;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.EVENT_TIMESTAMPS;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_REPEATS;
import static org.sagebionetworks.bridge.models.ResourceList.INSTANCE_GUIDS;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.SESSION_GUIDS;
import static org.sagebionetworks.bridge.models.ResourceList.SORT_ORDER;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.STUDY_ID;
import static org.sagebionetworks.bridge.models.ResourceList.TIME_WINDOW_GUIDS;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.ASC;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventRequest;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

public class AdherenceServiceTest extends Mockito {
    
    private static final DateTime FINISHED_ON = MODIFIED_ON;

    @Mock
    AdherenceRecordDao mockDao;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    StudyActivityEventService mockStudyActivityEventService;
    
    @Mock
    Schedule2Service mockScheduleService;
    
    @Captor
    ArgumentCaptor<AdherenceRecordsSearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyActivityEventRequest> requestCaptor;

    @InjectMocks
    AdherenceService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void updateAdherenceRecords() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        AdherenceRecord rec1 = getAdherenceRecord("AAA");
        AdherenceRecord rec2 = getAdherenceRecord("BBB");
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        service.updateAdherenceRecords(TEST_APP_ID, records);
        
        verify(mockDao).updateAdherenceRecords(records);
        verifyNoMoreInteractions(mockStudyActivityEventService);
        verifyNoMoreInteractions(mockScheduleService);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateAdherenceRecords_noRecords() {
        service.updateAdherenceRecords(TEST_APP_ID, new AdherenceRecordList(ImmutableList.of()));
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateAdherenceRecords_notAuthorized() {
        AdherenceRecord rec1 = getAdherenceRecord("AAA");
        service.updateAdherenceRecords(TEST_APP_ID, new AdherenceRecordList(ImmutableList.of(rec1)));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateAdherenceRecords_invalidRecord() {
        AdherenceRecord rec1 = getAdherenceRecord("AAA");
        rec1.setStartedOn(null);
        AdherenceRecord rec2 = getAdherenceRecord("BBB");
        rec2.setUserId(null);
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1, rec2));

        service.updateAdherenceRecords(TEST_APP_ID, records);
    }
    
    @Test
    public void updateAdherenceRecords_recordSessionFinished() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setSessionGuid("sessionGuid");
        when(mockScheduleService.getTimelineMetadata("BBB")).thenReturn(Optional.of(meta));
        
        AdherenceRecord rec1 = getAdherenceRecord("AAA");
        AdherenceRecord rec2 = getAdherenceRecord("BBB");
        rec2.setFinishedOn(FINISHED_ON);
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        service.updateAdherenceRecords(TEST_APP_ID, records);
        
        verify(mockDao).updateAdherenceRecords(records);
        verify(mockStudyActivityEventService).publishEvent(requestCaptor.capture());
        
        StudyActivityEventRequest request = requestCaptor.getValue();
        assertEquals(request.getAppId(), TEST_APP_ID);
        assertEquals(request.getStudyId(), TEST_STUDY_ID);
        assertEquals(request.getUserId(), TEST_USER_ID);
        assertEquals(request.getObjectType(), ActivityEventObjectType.SESSION);
        assertEquals(request.getObjectId(), "sessionGuid");
        assertEquals(request.getEventType(), ActivityEventType.FINISHED);
        assertEquals(request.getTimestamp(), FINISHED_ON); 
    }

    @Test
    public void updateAdherenceRecords_recordAssessmentFinished() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        TimelineMetadata meta = new TimelineMetadata();
        meta.setSessionGuid("sessionGuid");
        meta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        meta.setAssessmentId("assessmentId");
        when(mockScheduleService.getTimelineMetadata("BBB")).thenReturn(Optional.of(meta));
        
        AdherenceRecord rec1 = getAdherenceRecord("AAA");
        AdherenceRecord rec2 = getAdherenceRecord("BBB");
        rec2.setFinishedOn(FINISHED_ON);
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        service.updateAdherenceRecords(TEST_APP_ID, records);
        
        verify(mockDao).updateAdherenceRecords(records);
        verify(mockStudyActivityEventService).publishEvent(requestCaptor.capture());
        
        StudyActivityEventRequest request = requestCaptor.getValue();
        assertEquals(request.getAppId(), TEST_APP_ID);
        assertEquals(request.getStudyId(), TEST_STUDY_ID);
        assertEquals(request.getUserId(), TEST_USER_ID);
        assertEquals(request.getObjectType(), ActivityEventObjectType.ASSESSMENT);
        assertEquals(request.getObjectId(), "assessmentId");
        assertEquals(request.getEventType(), ActivityEventType.FINISHED);
        assertEquals(request.getTimestamp(), FINISHED_ON); 
    }
    
    @Test
    public void updateAdherenceRecords_eventWithoutMetadata() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        when(mockScheduleService.getTimelineMetadata("BBB")).thenReturn(Optional.empty());
        
        AdherenceRecord rec1 = getAdherenceRecord("BBB");
        rec1.setFinishedOn(FINISHED_ON);
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1));
        
        service.updateAdherenceRecords(TEST_APP_ID, records);
        
        verify(mockStudyActivityEventService, never()).publishEvent(any());
    }
    
    @Test
    public void getAdherenceRecords() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("event1", IMMUTABLE));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withInstanceGuids(ImmutableSet.of("AAA@" + CREATED_ON.toString()))
                .withEventTimestamps(ImmutableMap.of("event1", MODIFIED_ON))
                .withSessionGuids(ImmutableSet.of("BBB"))
                .withTimeWindowGuids(ImmutableSet.of("CCC"))
                .withOffsetBy(10)
                .withAdherenceRecordType(ASSESSMENT)
                .withUserId(TEST_USER_ID)
                .withStartTime(CREATED_ON)
                .withEndTime(MODIFIED_ON)
                .withStudyId(TEST_STUDY_ID).build();
        
        List<AdherenceRecord> list = ImmutableList.of(getAdherenceRecord("AAA"), getAdherenceRecord("BBB"));
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<AdherenceRecord>(list, 100);
        when(mockDao.getAdherenceRecords(any())).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> retValue = service.getAdherenceRecords(TEST_APP_ID, search);
        assertSame(retValue, page);
        
        Map<String, Object> rp = retValue.getRequestParams();
        assertEquals(rp.size(), 15);
        assertEquals(rp.get(ASSESSMENT_IDS), ImmutableSet.of());
        assertEquals(rp.get(START_TIME).toString(), CREATED_ON.toString());
        assertEquals(rp.get(END_TIME).toString(), MODIFIED_ON.toString());
        assertEquals(rp.get(EVENT_TIMESTAMPS), ImmutableMap.of("custom:event1", MODIFIED_ON));
        assertEquals(rp.get(INCLUDE_REPEATS), Boolean.TRUE);
        assertEquals(rp.get(CURRENT_TIMESTAMPS_ONLY), Boolean.FALSE);
        assertEquals(rp.get(INSTANCE_GUIDS), ImmutableSet.of("AAA@2015-01-26T23:38:32.486Z"));
        assertEquals(rp.get(OFFSET_BY), 10);
        assertEquals(rp.get(PAGE_SIZE), DEFAULT_PAGE_SIZE);
        assertEquals(rp.get(ADHERENCE_RECORD_TYPE), ASSESSMENT);
        assertEquals(rp.get(SESSION_GUIDS), ImmutableSet.of("BBB"));
        assertEquals(rp.get(SORT_ORDER), ASC);
        assertEquals(rp.get(STUDY_ID), TEST_STUDY_ID);
        assertEquals(rp.get(TIME_WINDOW_GUIDS), ImmutableSet.of("CCC"));
        
        verify(mockDao).getAdherenceRecords(searchCaptor.capture());
        AdherenceRecordsSearch adjSearch = searchCaptor.getValue();
        // verify that this was parsed
        assertEquals(adjSearch.getInstanceGuidStartedOnMap().get("AAA"), CREATED_ON);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getAdherenceRecords_notAuthorized() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID).build();

        service.getAdherenceRecords(TEST_APP_ID, search);
    }

    @Test
    public void cleanupSearchOptimizesWhenUnnecessary() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .build();
        
        AdherenceRecordsSearch retValue = service.cleanupSearch(TEST_APP_ID, search);
        assertSame(retValue, search);
    }

    @Test
    public void cleanupSearchNormalizesEventIds() {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("event1", IMMUTABLE, "event_2", IMMUTABLE));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withEventTimestamps(ImmutableMap.of("ENROLLMENT", CREATED_ON, 
                        "custom:event1", CREATED_ON, "event_2", CREATED_ON,
                        "custom:event3", CREATED_ON)).build();
        
        AdherenceRecordsSearch retValue = service.cleanupSearch(TEST_APP_ID, search);
        assertEquals(ImmutableSet.of("enrollment", "custom:event1", "custom:event_2"),
                retValue.getEventTimestamps().keySet());
    }
    
    @Test
    public void cleanupSearchParsesCompoundInstanceGuids() {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withInstanceGuids(ImmutableSet.of("AAA", "BBB@" + CREATED_ON.toString())).build();
        
        AdherenceRecordsSearch retValue = service.cleanupSearch(TEST_APP_ID, search);
        assertEquals(retValue.getInstanceGuids(), ImmutableSet.of("AAA"));
        assertEquals(retValue.getInstanceGuidStartedOnMap().size(), 1);
        assertEquals(retValue.getInstanceGuidStartedOnMap().get("BBB"), CREATED_ON);
    }
    
    @Test
    public void cleanupSearchRetrievesActivityEventsMap() {
        App app = App.create();
        app.setCustomEvents(ImmutableMap.of("event1", IMMUTABLE, "event2", IMMUTABLE));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withCurrentTimestampsOnly(TRUE)
                .withEventTimestamps(ImmutableMap.of("event1", CREATED_ON)).build();
        
        StudyActivityEvent event1 = new StudyActivityEvent();
        event1.setEventId("custom:event1");
        event1.setTimestamp(MODIFIED_ON);
        StudyActivityEvent event2 = new StudyActivityEvent();
        event2.setEventId("custom:event2");
        event2.setTimestamp(MODIFIED_ON);
        when(mockStudyActivityEventService.getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID))
            .thenReturn(new ResourceList<StudyActivityEvent>(ImmutableList.of(event1, event2)));
        
        AdherenceRecordsSearch retValue = service.cleanupSearch(TEST_APP_ID, search);
        
        verify(mockStudyActivityEventService)
            .getRecentStudyActivityEvents(TEST_APP_ID, TEST_USER_ID, TEST_STUDY_ID);
        
        // this first one is overridden by the values submitted by the client
        assertEquals(retValue.getEventTimestamps().get("custom:event1"), CREATED_ON);
        // the second one is added
        assertEquals(retValue.getEventTimestamps().get("custom:event2"), MODIFIED_ON);
    }
}
