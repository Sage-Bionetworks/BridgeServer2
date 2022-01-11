package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_EXTERNAL_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.createEvent;
import static org.sagebionetworks.bridge.models.ResourceList.ADHERENCE_RECORD_TYPE;
import static org.sagebionetworks.bridge.models.ResourceList.ASSESSMENT_IDS;
import static org.sagebionetworks.bridge.models.ResourceList.CURRENT_TIMESTAMPS_ONLY;
import static org.sagebionetworks.bridge.models.ResourceList.END_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.EVENT_TIMESTAMPS;
import static org.sagebionetworks.bridge.models.ResourceList.INCLUDE_REPEATS;
import static org.sagebionetworks.bridge.models.ResourceList.INSTANCE_GUIDS;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.ResourceList.PREDICATE;
import static org.sagebionetworks.bridge.models.ResourceList.SESSION_GUIDS;
import static org.sagebionetworks.bridge.models.ResourceList.SORT_ORDER;
import static org.sagebionetworks.bridge.models.ResourceList.START_TIME;
import static org.sagebionetworks.bridge.models.ResourceList.STRING_SEARCH_POSITION;
import static org.sagebionetworks.bridge.models.ResourceList.STUDY_ID;
import static org.sagebionetworks.bridge.models.ResourceList.TIME_WINDOW_GUIDS;
import static org.sagebionetworks.bridge.models.SearchTermPredicate.AND;
import static org.sagebionetworks.bridge.models.StringSearchPosition.INFIX;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.ASC;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
import com.google.common.collect.Iterables;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.dao.AdherenceReportDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.timelines.MetadataContainer;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

public class AdherenceServiceTest extends Mockito {
    
    private static final DateTime STARTED_ON = CREATED_ON;
    private static final DateTime FINISHED_ON = MODIFIED_ON;
    private static final DateTime EVENT_TS = CREATED_ON.minusWeeks(1);

    @Mock
    AdherenceRecordDao mockRecordDao;
    
    @Mock
    AdherenceReportDao mockReportDao;
    
    @Mock
    StudyService mockStudyService;
    
    @Mock
    StudyActivityEventService mockStudyActivityEventService;
    
    @Mock
    Schedule2Service mockScheduleService;
    
    @Captor
    ArgumentCaptor<AdherenceRecordsSearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyActivityEvent> eventCaptor;
    
    @Captor
    ArgumentCaptor<AdherenceRecord> recordCaptor;

    @InjectMocks
    @Spy
    AdherenceService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getDateTime()).thenReturn(MODIFIED_ON);
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
        
        AdherenceRecordList records = mockRecordUpdate(
                ar(STARTED_ON, null, "AAA", false), 
                ar(null, null, "BBB", false), 
                null);
        
        service.updateAdherenceRecords(TEST_APP_ID, records);
        
        verify(mockRecordDao, times(3)).updateAdherenceRecord(recordCaptor.capture());
        assertEquals(recordCaptor.getAllValues().get(0).getInstanceGuid(), "AAA");
        assertEquals(recordCaptor.getAllValues().get(1).getInstanceGuid(), "BBB");
        assertEquals(recordCaptor.getAllValues().get(2).getInstanceGuid(), "sessionInstanceGuid");
        
        // Nothing is finished, nothing is published.
        verify(mockStudyActivityEventService, never()).publishEvent(any(), eq(false), eq(true));
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void updateAdherenceRecords_noRecords() {
        service.updateAdherenceRecords(TEST_APP_ID, new AdherenceRecordList(ImmutableList.of()));
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void updateAdherenceRecords_notAuthorized() {
        AdherenceRecord rec1 = mockAssessmentRecord("AAA");
        service.updateAdherenceRecords(TEST_APP_ID, new AdherenceRecordList(ImmutableList.of(rec1)));
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateAdherenceRecords_invalidRecord() {
        AdherenceRecord rec1 = mockAssessmentRecord("AAA");
        rec1.setStartedOn(null);
        AdherenceRecord rec2 = mockAssessmentRecord("BBB");
        rec2.setUserId(null);
        AdherenceRecordList records = new AdherenceRecordList(ImmutableList.of(rec1, rec2));

        service.updateAdherenceRecords(TEST_APP_ID, records);
    }
    
    @Test
    public void updateAdherenceRecords_recordSessionFinished() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        AdherenceRecordList list = mockRecordUpdate(
                ar(null, FINISHED_ON, "AAA", false), 
                ar(CREATED_ON, FINISHED_ON, "BBB", false), 
                null);
        
        service.updateAdherenceRecords(TEST_APP_ID, list);
        
        verify(mockRecordDao).updateAdherenceRecord(list.getRecords().get(0));
        verify(mockRecordDao).updateAdherenceRecord(list.getRecords().get(1));
        verify(mockStudyActivityEventService, times(3)).publishEvent(eventCaptor.capture(), eq(false), eq(true));
        
        StudyActivityEvent event = eventCaptor.getAllValues().get(2);
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "session:sessionGuid:finished");
        assertEquals(event.getTimestamp(), FINISHED_ON); 
    }

    @Test
    public void updateAdherenceRecords_recordAssessmentFinished() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        AdherenceRecordList list = mockRecordUpdate(
                ar(null, null, "AAA", false), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                null);
        
        service.updateAdherenceRecords(TEST_APP_ID, list);
        
        verify(mockRecordDao).updateAdherenceRecord(list.getRecords().get(0));
        verify(mockRecordDao).updateAdherenceRecord(list.getRecords().get(1));
        verify(mockStudyActivityEventService, times(1)).publishEvent(eventCaptor.capture(), eq(false), eq(true));
        
        StudyActivityEvent event = eventCaptor.getValue();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getEventId(), "assessment:idBBB:finished");
        assertEquals(event.getTimestamp(), FINISHED_ON); 
    }
    
    @Test
    public void updateAdherenceRecords_eventWithoutMetadata() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, null, "AAA", false), 
                null, 
                null);
        
        service.updateAdherenceRecords(TEST_APP_ID, list);
        
        verify(mockStudyActivityEventService, never()).publishEvent(any(), eq(false), eq(true));
    }
    
    private AdherenceRecord mockAssessmentRecord(String id) {
        // This is started, but not finished
        AdherenceRecord asmtRecord = new AdherenceRecord();
        asmtRecord.setAppId(TEST_APP_ID);
        asmtRecord.setUserId(TEST_USER_ID);
        asmtRecord.setStudyId(TEST_STUDY_ID);
        asmtRecord.setInstanceGuid(id);
        asmtRecord.setEventTimestamp(EVENT_TS);
        
        TimelineMetadata asmtMeta = new TimelineMetadata();
        asmtMeta.setSessionInstanceGuid("sessionInstanceGuid");
        asmtMeta.setAssessmentInstanceGuid(id);
        asmtMeta.setSessionStartEventId("enrollment");
        when(mockScheduleService.getTimelineMetadata(id)).thenReturn(Optional.of(asmtMeta));
        
        return asmtRecord;
    }
    
    @Test
    public void updateAdherenceRecords_doNothingWithStartedSession() {
        AdherenceRecordList list = mockRecordUpdate(
                null, 
                ar(STARTED_ON, null, "BBB", false), 
                sar(null, STARTED_ON, false));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(captured.getStartedOn(), STARTED_ON);
    }

    @Test
    public void updateAdherenceRecords_doNothingWithFinishedSession() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", false), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(STARTED_ON, FINISHED_ON, true));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        
        verify(mockRecordDao, never()).updateAdherenceRecord(any());
        verify(mockStudyActivityEventService, never()).publishEvent(any(), eq(false), eq(true));
    }
    
    @Test
    public void updateAdherenceRecords_createAndStartSessionRecord() {
        AdherenceRecordList list = mockRecordUpdate(
                null, 
                ar(STARTED_ON, null, "BBB", false), 
                null);
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
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
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", false), 
                ar(STARTED_ON, null, "BBB", false), 
                null);
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(container.getSessionUpdates().size(), 1);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertNull(captured.getFinishedOn());
    }

    @Test
    public void updateAdherenceRecords_startExistingSessionRecord() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, null, "AAA", false), 
                null, 
                sar(null, null, false));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertNull(captured.getFinishedOn());
        assertFalse(captured.isDeclined()); 
        assertEquals(captured.getEventTimestamp(), EVENT_TS);
    }

    @Test
    public void updateAdherenceRecords_finishExistingSessionRecord() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", false), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(null, null, true));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertEquals(captured.getFinishedOn(), FINISHED_ON);
        assertFalse(captured.isDeclined()); // we now calculate this on the submitted records 
        assertEquals(captured.getEventTimestamp(), EVENT_TS);
    }
    
    @Test
    public void updateAdherenceRecords_declineExistingSessionRecord() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", true), 
                ar(STARTED_ON, FINISHED_ON, "BBB", true), 
                sar(null, null, false));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(captured.getAppId(), TEST_APP_ID);
        assertEquals(captured.getUserId(), TEST_USER_ID);
        assertEquals(captured.getStudyId(), TEST_STUDY_ID);
        assertEquals(captured.getInstanceGuid(), "sessionInstanceGuid");
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertEquals(captured.getFinishedOn(), FINISHED_ON);
        assertTrue(captured.isDeclined()); // we now calculate this on the submitted records 
        assertEquals(captured.getEventTimestamp(), EVENT_TS);
    }

    @Test
    public void updateAdherenceRecords_doesNotSetDeclinedSessionRecord() { 
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", true), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(null, null, false));
     
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));

        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(captured.getStartedOn(), STARTED_ON);
        assertEquals(captured.getFinishedOn(), FINISHED_ON);
        assertFalse(captured.isDeclined());
    }

    @Test
    public void updateAdherenceRecords_maintainsDeclinedSessionRecord() { 
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", true), 
                ar(STARTED_ON, FINISHED_ON, "BBB", true), 
                sar(STARTED_ON, FINISHED_ON, true));
     
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));

        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertTrue(captured.isDeclined());
    }

    @Test
    public void updateAdherenceRecords_undoDeclinedSessionRecord() { 
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", true), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(STARTED_ON, FINISHED_ON, true));
     
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));

        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertFalse(captured.isDeclined());
    }
    
    @Test
    public void updateAdherenceRecords_unfinishExistingSessionRecord() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, null, "AAA", false), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(STARTED_ON, FINISHED_ON, false));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertNull(captured.getFinishedOn());
        assertFalse(captured.isDeclined());
        
        verify(mockStudyActivityEventService, never()).publishEvent(any(), eq(false), eq(true));
    }
    
    @Test
    public void updateAdherenceRecords_unstartExistingSessionRecord() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(null, null, "AAA", false), 
                null, 
                sar(STARTED_ON, FINISHED_ON, false));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));
        
        AdherenceRecord captured = Iterables.getFirst(container.getSessionUpdates(), null);
        assertNull(captured.getStartedOn());
        assertNull(captured.getFinishedOn());
        assertFalse(captured.isDeclined()); // used the persisted record 
    }
    
    @Test
    public void updateAdherenceRecords_doesNotUpdateSessionRecordMultipleTimes() {
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", false), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(STARTED_ON, FINISHED_ON, true));
        
        MetadataContainer container = new MetadataContainer(mockScheduleService, list.getRecords());
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(0));
        service.updateSessionState(TEST_APP_ID, container, list.getRecords().get(1));
        
        assertEquals(container.getSessionUpdates().size(), 1);
    }
    
    @Test
    public void updateAdherenceRecords_sessionUpdatesMergedCorrectlyWithExistingRecord() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        AdherenceRecordList list = mockRecordUpdate(
                ar(STARTED_ON, FINISHED_ON, "AAA", false), 
                ar(STARTED_ON, FINISHED_ON, "BBB", false), 
                sar(STARTED_ON, null, false));

        AdherenceRecord sess = new AdherenceRecord();
        sess.setAppId(TEST_APP_ID);
        sess.setUserId(TEST_USER_ID);
        sess.setStudyId(TEST_STUDY_ID);
        sess.setInstanceGuid("sessionInstanceGuid");
        sess.setFinishedOn(FINISHED_ON.plusHours(1)); // different and should be ignored
        sess.setDeclined(true); // different and should be ignored
        sess.setEventTimestamp(EVENT_TS);
        list.getRecords().add(sess);
        
        service.updateAdherenceRecords(TEST_APP_ID, list);
        
        verify(mockRecordDao, times(3)).updateAdherenceRecord(recordCaptor.capture());
        
        AdherenceRecord session = recordCaptor.getAllValues().get(2);
        assertEquals(session.getStartedOn(), STARTED_ON);
        // based on the assessment records, any value submitted for session is ignored
        assertEquals(session.getFinishedOn(), FINISHED_ON);
        assertFalse(session.isDeclined());
    }
    
    @Test
    public void getAdherenceRecords() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(
                new StudyCustomEvent("event1", IMMUTABLE)));
        
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);

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
        
        List<AdherenceRecord> list = ImmutableList.of(mockAssessmentRecord("AAA"), mockAssessmentRecord("BBB"));
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<AdherenceRecord>(list, 100);
        when(mockRecordDao.getAdherenceRecords(any())).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> retValue = service.getAdherenceRecords(TEST_APP_ID, search);
        assertSame(retValue, page);
        
        Map<String, Object> rp = retValue.getRequestParams();
        assertEquals(rp.size(), 17);
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
        assertEquals(rp.get(PREDICATE), AND);
        assertEquals(rp.get(STRING_SEARCH_POSITION), INFIX);
        
        verify(mockRecordDao).getAdherenceRecords(searchCaptor.capture());
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
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(
                new StudyCustomEvent("event1", IMMUTABLE), 
                new StudyCustomEvent("event2", IMMUTABLE)));
        
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);
        
        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withEventTimestamps(ImmutableMap.of("enrollment", CREATED_ON, 
                        "custom:event1", CREATED_ON, "event_2", CREATED_ON,
                        "custom:event3", CREATED_ON)).build();
        
        // This now actually removes invalid event IDs, which seems okay.
        AdherenceRecordsSearch retValue = service.cleanupSearch(TEST_APP_ID, search);
        assertEquals(retValue.getEventTimestamps().keySet(), 
                ImmutableSet.of("enrollment", "custom:event1"));
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
        StudyActivityEventIdsMap eventMap = new StudyActivityEventIdsMap();
        eventMap.addCustomEvents(ImmutableList.of(
                new StudyCustomEvent("event1", IMMUTABLE), 
                new StudyCustomEvent("event2", IMMUTABLE)));
        
        when(mockStudyService.getStudyActivityEventIdsMap(TEST_APP_ID, TEST_STUDY_ID)).thenReturn(eventMap);

        AdherenceRecordsSearch search = new AdherenceRecordsSearch.Builder()
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withCurrentTimestampsOnly(TRUE)
                .withEventTimestamps(ImmutableMap.of("event1", CREATED_ON)).build();
        
        StudyActivityEvent event1 = createEvent("custom:event1", MODIFIED_ON, null);
        StudyActivityEvent event2 = createEvent("custom:event2", MODIFIED_ON, null);
        when(mockStudyActivityEventService.getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID))
            .thenReturn(new ResourceList<StudyActivityEvent>(ImmutableList.of(event1, event2)));
        
        AdherenceRecordsSearch retValue = service.cleanupSearch(TEST_APP_ID, search);
        
        verify(mockStudyActivityEventService)
            .getRecentStudyActivityEvents(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        
        // this first one is overridden by the values submitted by the client
        assertEquals(retValue.getEventTimestamps().get("custom:event1"), CREATED_ON);
        // the second one is added
        assertEquals(retValue.getEventTimestamps().get("custom:event2"), MODIFIED_ON);
    }
    
    @Test
    public void updateSessionState_sessionUpdateUsesPersistedSession() {
        AdherenceRecord asmt = new AdherenceRecord();
        asmt.setInstanceGuid("asmtInstanceGuid");
        asmt.setEventTimestamp(EVENT_TS);
        asmt.setStartedOn(STARTED_ON);
        asmt.setFinishedOn(FINISHED_ON);
        
        TimelineMetadata asmtMeta = new TimelineMetadata();
        asmtMeta.setSessionInstanceGuid("sessionInstanceGuid");
        asmtMeta.setSessionStartEventId("enrollment");
        when(mockScheduleService.getTimelineMetadata("asmtInstanceGuid"))
            .thenReturn(Optional.of(asmtMeta));
        when(mockScheduleService.getSessionAssessmentMetadata("sessionInstanceGuid"))
            .thenReturn(ImmutableList.of(asmtMeta));

        AdherenceRecord session = new AdherenceRecord();
        session.setInstanceGuid("sessionInstanceGuid");
        session.setEventTimestamp(EVENT_TS);
        session.setStartedOn(STARTED_ON);
        session.setClientTimeZone("America/Los_Angeles");
        
        TimelineMetadata sessionTm = new TimelineMetadata();
        sessionTm.setSessionInstanceGuid("sessionInstanceGuid");
        sessionTm.setSessionStartEventId("enrollment");
        when(mockScheduleService.getTimelineMetadata("sessionInstanceGuid"))
            .thenReturn(Optional.of(sessionTm));

        MetadataContainer container = new MetadataContainer(mockScheduleService, ImmutableList.of(asmt));
        
        // Note that because we save the assessments, when we later query for them,
        // they are always present in this call.
        when(mockRecordDao.getAdherenceRecords(any()))
                .thenReturn(new PagedResourceList<>(ImmutableList.of(asmt, session), 1, true));
        
        service.updateSessionState(TEST_APP_ID, container, asmt);
        assertFalse(container.getSessionUpdates().isEmpty());
        
        AdherenceRecord update = Iterables.getFirst(container.getSessionUpdates(), null);
        assertEquals(update.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(update.getStartedOn(), STARTED_ON);
        assertEquals(update.getFinishedOn(), FINISHED_ON);
    }

    @Test
    public void deleteAdherenceRecord_persistentTimeWindow() {
        AdherenceRecord record = ar(STARTED_ON, FINISHED_ON, "fake-guid", false);
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setTimeWindowPersistent(true);

        when(mockScheduleService.getTimelineMetadata(any())).thenReturn(Optional.of(timelineMetadata));

        service.deleteAdherenceRecord(record);

        verify(mockRecordDao).deleteAdherenceRecordPermanently(eq(record));
        assertEquals(record.getInstanceTimestamp(), record.getStartedOn());
    }

    @Test
    public void deleteAdherenceRecord_notPersistentTimeWindow() {
        AdherenceRecord record = ar(STARTED_ON, FINISHED_ON, "fake-guid", false);
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        TimelineMetadata timelineMetadata = new TimelineMetadata();
        timelineMetadata.setTimeWindowPersistent(false);

        when(mockScheduleService.getTimelineMetadata(any())).thenReturn(Optional.of(timelineMetadata));

        service.deleteAdherenceRecord(record);

        verify(mockRecordDao).deleteAdherenceRecordPermanently(eq(record));
        assertEquals(record.getInstanceTimestamp(), record.getEventTimestamp());
    }

    @Test
    public void deleteAdherenceRecord_missingMetadata() {
        AdherenceRecord record = ar(STARTED_ON, FINISHED_ON, "fake-guid", false);
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        when(mockScheduleService.getTimelineMetadata(any())).thenReturn(Optional.empty());

        service.deleteAdherenceRecord(record);

        verifyZeroInteractions(mockRecordDao);
        assertNull(record.getInstanceTimestamp());
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteAdherenceRecord_notAuthorized() {
        AdherenceRecord record = ar(STARTED_ON, FINISHED_ON, "fake-guid", false);
        service.deleteAdherenceRecord(record);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void deleteAdherenceRecord_missingEventTimestamp() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        AdherenceRecord record = ar(STARTED_ON, FINISHED_ON, "fake-guid", false);
        record.setEventTimestamp(null);

        service.deleteAdherenceRecord(record);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void deleteAdherenceRecord_missingStartedOnTimestamp() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        AdherenceRecord record = ar(STARTED_ON, FINISHED_ON, "fake-guid", false);
        record.setStartedOn(null);

        service.deleteAdherenceRecord(record);
    }
    
    @Test
    public void getEventStreamAdherenceReport_studyHasNoSchedule() { 
        Study study = Study.create();
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        EventStreamAdherenceReport report = service.getEventStreamAdherenceReport(TEST_APP_ID, TEST_STUDY_ID,
                TEST_USER_ID, EVENT_TS, TEST_CLIENT_TIME_ZONE, true);
        assertTrue(report.getStreams().isEmpty());
        assertEquals(report.getTimestamp(), EVENT_TS.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(report.getAdherencePercent(), 100);
        assertTrue(report.isActiveOnly());
    }
    
    @Test
    public void getEventStreamAdherenceReport() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        List<TimelineMetadata> metadata = ImmutableList.of();
        when(mockScheduleService.getScheduleMetadata(SCHEDULE_GUID)).thenReturn(metadata);
        
        ResourceList<StudyActivityEvent> events = new ResourceList<>(ImmutableList.of(), true);
        when(mockStudyActivityEventService.getRecentStudyActivityEvents(
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)).thenReturn(events);
        
        PagedResourceList<AdherenceRecord> adherencePage = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockRecordDao.getAdherenceRecords(any())).thenReturn(adherencePage);
        
        EventStreamAdherenceReport report = service.getEventStreamAdherenceReport(
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, EVENT_TS, TEST_CLIENT_TIME_ZONE, true);
        assertTrue(report.getStreams().isEmpty());
        assertEquals(report.getTimestamp(), EVENT_TS.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(report.getAdherencePercent(), 100);
        assertTrue(report.isActiveOnly());
        
        verify(mockRecordDao).getAdherenceRecords(searchCaptor.capture());
        AdherenceRecordsSearch search = searchCaptor.getValue();
        assertTrue(search.getCurrentTimestampsOnly());
        assertTrue(search.getIncludeRepeats());
        assertEquals(search.getAdherenceRecordType(), AdherenceRecordType.SESSION);
        assertEquals(search.getStudyId(), TEST_STUDY_ID);
        assertEquals(search.getUserId(), TEST_USER_ID);
    }
    
    @Test
    public void getWeeklyAdherenceReport() throws Exception {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setFirstName("firstName");
        account.setLastName("lastName");
        account.setEmail(TestConstants.EMAIL);
        account.setPhone(TestConstants.PHONE);
        account.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        account.getEnrollments().add(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, TEST_EXTERNAL_ID));
        
        Study study = Study.create();
        study.setScheduleGuid(SCHEDULE_GUID);
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        List<StudyActivityEvent> events = ImmutableList.of();
        ResourceList<StudyActivityEvent> page = new ResourceList<>(events, true);
        when(mockStudyActivityEventService.getRecentStudyActivityEvents(
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> page2 = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockRecordDao.getAdherenceRecords(any())).thenReturn(page2);
        
        WeeklyAdherenceReport retValue = service.getWeeklyAdherenceReport(
                TEST_APP_ID, TEST_STUDY_ID, account);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getStudyId(), TEST_STUDY_ID);
        assertEquals(retValue.getUserId(), TEST_USER_ID);
        assertEquals(retValue.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        assertEquals(retValue.getCreatedOn(), MODIFIED_ON.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(retValue.getWeeklyAdherencePercent(), 100);
        assertEquals(retValue.getParticipant().getIdentifier(), TEST_USER_ID);
        assertEquals(retValue.getParticipant().getFirstName(), "firstName");
        assertEquals(retValue.getParticipant().getLastName(), "lastName");
        assertEquals(retValue.getParticipant().getEmail(), TestConstants.EMAIL);
        assertEquals(retValue.getParticipant().getPhone(), TestConstants.PHONE);
        assertEquals(retValue.getParticipant().getExternalId(), TEST_EXTERNAL_ID);
        
        verify(mockReportDao).saveWeeklyAdherenceReport(retValue);
        // The contents of the weekly report are tested separately by testing the generator
    }
    
    @Test
    public void getWeeklyAdherenceReport_studyHasNoSchedule() { 
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setId(TEST_USER_ID);
        account.setFirstName("firstName");
        account.setLastName("lastName");
        account.setEmail(TestConstants.EMAIL);
        account.setPhone(TestConstants.PHONE);
        account.setClientTimeZone(TEST_CLIENT_TIME_ZONE);
        account.getEnrollments().add(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, TEST_EXTERNAL_ID));
        
        Study study = Study.create();
        when(mockStudyService.getStudy(TEST_APP_ID, TEST_STUDY_ID, true)).thenReturn(study);
        
        List<StudyActivityEvent> events = ImmutableList.of();
        ResourceList<StudyActivityEvent> page = new ResourceList<>(events, true);
        when(mockStudyActivityEventService.getRecentStudyActivityEvents(
                TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)).thenReturn(page);
        
        PagedResourceList<AdherenceRecord> page2 = new PagedResourceList<>(ImmutableList.of(), 0);
        when(mockRecordDao.getAdherenceRecords(any())).thenReturn(page2);
        
        WeeklyAdherenceReport retValue = service.getWeeklyAdherenceReport(
                TEST_APP_ID, TEST_STUDY_ID, account);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getStudyId(), TEST_STUDY_ID);
        assertEquals(retValue.getUserId(), TEST_USER_ID);
        assertEquals(retValue.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        assertEquals(retValue.getCreatedOn(), MODIFIED_ON.withZone(DateTimeZone.forID(TEST_CLIENT_TIME_ZONE)));
        assertEquals(retValue.getWeeklyAdherencePercent(), 100);
        assertEquals(retValue.getParticipant().getIdentifier(), TEST_USER_ID);
        
        verify(mockReportDao).saveWeeklyAdherenceReport(retValue);
        // The contents of the weekly report are tested separately by testing the generator
    }
    
    @Test
    public void getWeeklyAdherenceReports() {
        
        service.getWeeklyAdherenceReports(TEST_APP_ID, TEST_STUDY_ID, "label", 75, 100, 50);
        
    }
    
    private AdherenceRecord ar(DateTime startedOn, DateTime finishedOn, String guid, boolean declined) {
        AdherenceRecord sess = new AdherenceRecord();
        sess.setAppId(TEST_APP_ID);
        sess.setUserId(TEST_USER_ID);
        sess.setStudyId(TEST_STUDY_ID);
        sess.setInstanceGuid(guid);
        sess.setStartedOn(startedOn);
        sess.setFinishedOn(finishedOn);
        sess.setDeclined(TRUE.equals(declined));
        sess.setEventTimestamp(EVENT_TS);
        return sess;
    }

    private AdherenceRecord sar(DateTime startedOn, DateTime finishedOn, boolean declined) {
        AdherenceRecord sess = new AdherenceRecord();
        sess.setAppId(TEST_APP_ID);
        sess.setUserId(TEST_USER_ID);
        sess.setStudyId(TEST_STUDY_ID);
        sess.setInstanceGuid("sessionInstanceGuid");
        sess.setStartedOn(startedOn);
        sess.setFinishedOn(finishedOn);
        sess.setDeclined(TRUE.equals(declined));
        sess.setEventTimestamp(EVENT_TS);
        return sess;
    }
    
    private AdherenceRecordList mockRecordUpdate(AdherenceRecord rec1, AdherenceRecord rec2, 
            AdherenceRecord sessionRecord) {
        List<AdherenceRecord> records = new ArrayList<>();
        List<TimelineMetadata> metas = new ArrayList<>();
        
        if (rec1 != null) {
            records.add(rec1);
                
            TimelineMetadata meta1 = new TimelineMetadata();
            meta1.setGuid(rec1.getInstanceGuid());
            meta1.setSessionInstanceGuid("sessionInstanceGuid");
            meta1.setAssessmentInstanceGuid(rec1.getInstanceGuid());
            meta1.setSessionStartEventId("enrollment");
            meta1.setAssessmentId("id"+rec1.getInstanceGuid());
            metas.add(meta1);
            when(mockScheduleService.getTimelineMetadata(rec1.getInstanceGuid())).thenReturn(Optional.of(meta1));
        }
        if (rec2 != null) {
            rec2.setInstanceGuid(rec2.getInstanceGuid());
            records.add(rec2);
                
            TimelineMetadata meta2 = new TimelineMetadata();
            meta2.setGuid(rec2.getInstanceGuid());
            meta2.setSessionInstanceGuid("sessionInstanceGuid");
            meta2.setAssessmentInstanceGuid(rec2.getInstanceGuid());
            meta2.setSessionStartEventId("enrollment");
            meta2.setAssessmentId("id"+rec2.getInstanceGuid());
            metas.add(meta2);
            when(mockScheduleService.getTimelineMetadata(rec2.getInstanceGuid())).thenReturn(Optional.of(meta2));
        }
        
        when(mockScheduleService.getSessionAssessmentMetadata("sessionInstanceGuid"))
            .thenReturn(ImmutableList.copyOf(metas));
        
        if (sessionRecord != null) {
            records.add(sessionRecord);
        }
        TimelineMetadata sessMeta = new TimelineMetadata();
        sessMeta.setGuid("sessionInstanceGuid");
        sessMeta.setSessionInstanceGuid("sessionInstanceGuid");
        sessMeta.setSessionGuid("sessionGuid");
        sessMeta.setSessionStartEventId("enrollment");
        metas.add(sessMeta);
        
        when(mockScheduleService.getTimelineMetadata("sessionInstanceGuid"))
            .thenReturn(Optional.of(sessMeta));
        
        PagedResourceList<AdherenceRecord> page = new PagedResourceList<>(records, records.size(), true);
        when(mockRecordDao.getAdherenceRecords(any())).thenReturn(page);
        
        return new AdherenceRecordList(records);
    }
}
