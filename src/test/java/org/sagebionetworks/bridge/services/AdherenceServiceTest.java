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
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.ASC;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
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
    
    private static final DateTime STARTED_ON = CREATED_ON;
    private static final DateTime FINISHED_ON = MODIFIED_ON;
    private static final DateTime EVENT_TS = CREATED_ON.minusWeeks(1);

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
    
    @Captor
    ArgumentCaptor<AdherenceRecord> recordCaptor;

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
        
        verify(mockDao).updateAdherenceRecord(rec1);
        verify(mockDao).updateAdherenceRecord(rec2);
        verifyNoMoreInteractions(mockStudyActivityEventService);
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
        
        verify(mockDao).updateAdherenceRecord(rec1);
        verify(mockDao).updateAdherenceRecord(rec2);
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
        meta.setSessionStartEventId("enrollment");
        when(mockScheduleService.getTimelineMetadata("BBB")).thenReturn(Optional.of(meta));
        when(mockDao.getAdherenceRecords(any())).thenReturn(new PagedResourceList<>(ImmutableList.of(), 0));
        
        AdherenceRecord rec1 = getAdherenceRecord("AAA");
        rec1.setEventTimestamp(DateTime.now());
        AdherenceRecord rec2 = getAdherenceRecord("BBB");
        rec2.setFinishedOn(FINISHED_ON);
        rec2.setEventTimestamp(DateTime.now());
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1, rec2));
        
        service.updateAdherenceRecords(TEST_APP_ID, records);
        
        verify(mockDao).updateAdherenceRecord(rec1);
        verify(mockDao).updateAdherenceRecord(rec2);
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
    
    private AdherenceRecord mockAssessmentRecord() { 
        // This is started, but not finished
        AdherenceRecord asmtRecord = new AdherenceRecord();
        asmtRecord.setAppId(TEST_APP_ID);
        asmtRecord.setUserId(TEST_USER_ID);
        asmtRecord.setStudyId(TEST_STUDY_ID);
        asmtRecord.setInstanceGuid("assessmentInstanceGuid");
        asmtRecord.setEventTimestamp(EVENT_TS);
        
        TimelineMetadata asmtMeta = new TimelineMetadata();
        asmtMeta.setSessionInstanceGuid("sessionInstanceGuid");
        asmtMeta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        asmtMeta.setSessionStartEventId("enrollment");
        when(mockScheduleService.getTimelineMetadata("assessmentInstanceGuid"))
            .thenReturn(Optional.of(asmtMeta));
        
        return asmtRecord;
    }
    
    private void mockAssessmentRecordSet(DateTime started1, DateTime finished1, DateTime started2, DateTime finished2,
            boolean withSessionRecord, DateTime sessionStarted, DateTime sessionFinished) {
        
        List<AdherenceRecord> asmtList = new ArrayList<>();
        
        AdherenceRecord asmtAr1 = new AdherenceRecord();
        asmtAr1.setInstanceGuid("assessmentInstanceGuid");
        asmtAr1.setStartedOn(started1);
        asmtAr1.setFinishedOn(finished1);
        asmtList.add(asmtAr1);
        
        AdherenceRecord asmtAr2 = new AdherenceRecord();
        asmtAr2.setInstanceGuid("differentAssessmentInstanceGuid");
        asmtAr2.setStartedOn(started2);
        asmtAr2.setFinishedOn(finished2);
        asmtList.add(asmtAr2);
        
        if (withSessionRecord) {
            AdherenceRecord session = new AdherenceRecord();
            session.setInstanceGuid("sessionInstanceGuid");
            session.setStartedOn(sessionStarted);
            session.setFinishedOn(sessionFinished);
            session.setDeclined(true); // so we can verify this record was used, not a new one 
            asmtList.add(session);
        }
        
        when(mockDao.getAdherenceRecords(any())).thenReturn(new PagedResourceList<>(asmtList, asmtList.size()));
        
        List<TimelineMetadata> metadataList = ImmutableList.of(new TimelineMetadata(), new TimelineMetadata());
        when(mockScheduleService.getSessionAssessmentMetadata("sessionInstanceGuid")).thenReturn(metadataList);
    }

    @Test
    public void updateAdherenceRecords_doNothingWithStartedSession() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests):
        mockAssessmentRecordSet(null, null, STARTED_ON, null, true, STARTED_ON, null);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao, never()).updateAdherenceRecord(any());
        verify(mockStudyActivityEventService, never()).publishEvent(any());
    }

    @Test
    public void updateAdherenceRecords_doNothingWithFinishedSession() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests):
        mockAssessmentRecordSet(STARTED_ON, FINISHED_ON, STARTED_ON, FINISHED_ON, true, STARTED_ON, FINISHED_ON);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao, never()).updateAdherenceRecord(any());
        verify(mockStudyActivityEventService, never()).publishEvent(any());
    }
    
    @Test
    public void updateAdherenceRecords_createAndStartSessionRecord() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests):
        mockAssessmentRecordSet(null, null, STARTED_ON, null, false, null, null);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao).updateAdherenceRecord(recordCaptor.capture());
        AdherenceRecord captured = recordCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertNull(captured.getFinishedOn());
        assertEquals(captured.getEventTimestamp(), EVENT_TS);
    }
    
    @Test
    public void updateAdherenceRecords_createAndFinishSessionRecord() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests):
        mockAssessmentRecordSet(STARTED_ON, FINISHED_ON, STARTED_ON, FINISHED_ON, false, null, null);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao).updateAdherenceRecord(recordCaptor.capture());
        AdherenceRecord captured = recordCaptor.getValue();
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertEquals(captured.getFinishedOn(), FINISHED_ON);
    }

    @Test
    public void updateAdherenceRecords_startExistingSessionRecord() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests). 
        mockAssessmentRecordSet(null, null, STARTED_ON, null, true, null, null);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao).updateAdherenceRecord(recordCaptor.capture());
        AdherenceRecord captured = recordCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertNull(captured.getFinishedOn());
        assertTrue(captured.isDeclined()); // used the persisted record 
        assertEquals(captured.getEventTimestamp(), EVENT_TS);
    }

    @Test
    public void updateAdherenceRecords_finishExistingSessionRecord() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests). 
        mockAssessmentRecordSet(STARTED_ON, FINISHED_ON, STARTED_ON, FINISHED_ON, true, null, null);
        
        TimelineMetadata sessionMeta = new TimelineMetadata();
        sessionMeta.setSessionGuid("sessionGuid");
        when(mockScheduleService.getTimelineMetadata("sessionInstanceGuid"))
            .thenReturn(Optional.of(sessionMeta));
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao).updateAdherenceRecord(recordCaptor.capture());
        AdherenceRecord captured = recordCaptor.getValue();
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertEquals(captured.getFinishedOn(), FINISHED_ON);
        assertTrue(captured.isDeclined()); // used the persisted record 
        assertEquals(captured.getEventTimestamp(), EVENT_TS);
        
        verify(mockStudyActivityEventService).publishEvent(requestCaptor.capture());
        StudyActivityEventRequest request = requestCaptor.getValue();
        assertEquals(request.getEventType(), FINISHED);
        assertEquals(request.getObjectType(), ActivityEventObjectType.SESSION);
        assertEquals(request.getTimestamp(), FINISHED_ON);
        assertEquals(request.getObjectId(), "sessionGuid");
    }
    
    @Test
    public void updateAdherenceRecords_unfinishExistingSessionRecord() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests). 
        mockAssessmentRecordSet(STARTED_ON, null, STARTED_ON, FINISHED_ON, true, STARTED_ON, FINISHED_ON);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao).updateAdherenceRecord(recordCaptor.capture());
        AdherenceRecord captured = recordCaptor.getValue();
        assertNull(captured.getFinishedOn());
        assertTrue(captured.isDeclined()); // used the persisted record
        
        verify(mockStudyActivityEventService, never()).publishEvent(any());
    }
    
    @Test
    public void updateAdherenceRecords_unstartExistingSessionRecord() {
        // This is started, but not finished. We don't really care because we're 
        // going to look at the entire set of assessment records
        AdherenceRecord asmtRecord = mockAssessmentRecord();
        
        // Now mock the set of assessment records (we do want to change these across
        // a number of tests). 
        mockAssessmentRecordSet(null, null, null, null, true, STARTED_ON, FINISHED_ON);
        
        service.updateSessionState(TEST_APP_ID, asmtRecord);
        
        verify(mockDao).updateAdherenceRecord(recordCaptor.capture());
        AdherenceRecord captured = recordCaptor.getValue();
        assertNull(captured.getStartedOn());
        assertNull(captured.getFinishedOn());
        assertTrue(captured.isDeclined()); // used the persisted record 
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
                .withEventTimestamps(ImmutableMap.of("enrollment", CREATED_ON, 
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
