package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.getAdherenceRecord;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType.SESSION;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.ASC;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SortOrder.DESC;
import static org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator.DEFAULT_PAGE_SIZE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordId;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;

public class HibernateAdherenceRecordDaoTest extends Mockito {

    private static final String BASE_QUERY = HibernateAdherenceRecordDao.BASE_QUERY
            + " WHERE ar.userId = :userId AND ar.studyId = :studyId";
    private static final String BASE_QUERY_WITHOUT_USER_ID = HibernateAdherenceRecordDao.BASE_QUERY
            + " WHERE ar.studyId = :studyId";
    private static final String ORDER = " ORDER BY ar.startedOn ASC";

    // It doesn't matter what these strings are, we're just verifying they are passed
    // correctly through the code.
    private static final Set<String> STRING_SET = TestConstants.USER_STUDY_IDS;

    @Mock
    HibernateHelper mockHelper;

    @Mock
    Session mockSession;

    @Mock
    NativeQuery<Schedule2> mockQuery;

    @Captor
    ArgumentCaptor<AdherenceRecordId> recordIdCaptor;

    @Captor
    ArgumentCaptor<AdherenceRecordId> idCaptor;

    @InjectMocks
    HibernateAdherenceRecordDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        when(mockSession.createNativeQuery(any())).thenReturn(mockQuery);
        when(mockHelper.executeWithExceptionHandling(any(), any())).thenAnswer(args -> {
            Function<Session, Schedule2> func = args.getArgument(1);
            func.apply(mockSession);
            return args.getArgument(0);
        });
    }

    @Test
    public void getAdherenceRecords() {
        AdherenceRecord rec1 = getAdherenceRecord(GUID);
        AdherenceRecord rec2 = getAdherenceRecord(GUID);
        List<AdherenceRecord> list = ImmutableList.of(rec1, rec2);

        AdherenceRecordsSearch search = search().build();

        when(mockHelper.nativeQueryGet("SELECT * " + BASE_QUERY + ORDER,
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID), 0, DEFAULT_PAGE_SIZE,
                AdherenceRecord.class)).thenReturn(list);

        when(mockHelper.nativeQueryCount("SELECT count(*) " + BASE_QUERY + ORDER,
                ImmutableMap.of("studyId", TEST_STUDY_ID, "userId", TEST_USER_ID))).thenReturn(150);

        PagedResourceList<AdherenceRecord> retValue = dao.getAdherenceRecords(search);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(150));
    }

    @Test
    public void createQuery_everything() {
        AdherenceRecordsSearch search = search().build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }
    
    // Study-scoped query.
    @Test
    public void createQuery_noUserId() {
        AdherenceRecordsSearch.Builder searchBuilder = search();
        searchBuilder.withUserId(null);
        
        QueryBuilder builder = dao.createQuery(searchBuilder.build());
        assertEquals(builder.getQuery(), BASE_QUERY_WITHOUT_USER_ID + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertNull(builder.getParameters().get("userId"));
    }

    @Test
    public void createQuery_assessmentIds() {
        AdherenceRecordsSearch search = search().withAssessmentIds(STRING_SET).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND tm.assessmentId IN :assessmentIds" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("assessmentIds"), STRING_SET);
    }

    @Test
    public void createQuery_sessionGuids() {
        AdherenceRecordsSearch search = search().withSessionGuids(STRING_SET).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND tm.sessionGuid IN :sessionGuids" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("sessionGuids"), STRING_SET);
    }

    @Test
    public void createQuery_instanceGuids() {
        AdherenceRecordsSearch search = search().withInstanceGuids(STRING_SET).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND ar.instanceGuid IN :instanceGuids" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("instanceGuids"), STRING_SET);
    }

    @Test
    public void createQuery_timeWindowGuids() {
        AdherenceRecordsSearch search = search().withTimeWindowGuids(STRING_SET).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND tm.timeWindowGuid IN :timeWindowGuids" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("timeWindowGuids"), STRING_SET);
    }

    @Test
    public void createQuery_noRepeatsAsc() {
        AdherenceRecordsSearch search = search().withIncludeRepeats(Boolean.FALSE).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(),
                BASE_QUERY + " AND ar.startedOn = (SELECT startedOn FROM AdherenceRecords "
                        + "WHERE userId = :userId AND instanceGuid = ar.instanceGuid ORDER BY "
                        + "startedOn ASC LIMIT 1)" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }

    @Test
    public void createQuery_noRepeatsDesc() {
        AdherenceRecordsSearch search = search().withIncludeRepeats(Boolean.FALSE).withSortOrder(DESC).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(),
                BASE_QUERY + " AND ar.startedOn = (SELECT startedOn FROM AdherenceRecords "
                        + "WHERE userId = :userId AND instanceGuid = ar.instanceGuid ORDER BY "
                        + "startedOn DESC LIMIT 1) ORDER BY ar.startedOn DESC");
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }

    @Test
    public void createQuery_withEventTimestamps() {
        Map<String, DateTime> events = ImmutableMap.of("e1", CREATED_ON, "e2", MODIFIED_ON);
        AdherenceRecordsSearch search = search().withEventTimestamps(events).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(),
                BASE_QUERY + " AND ( (tm.sessionStartEventId = :evtKey0 AND ar.eventTimestamp = "
                        + ":evtVal0) OR (tm.sessionStartEventId = :evtKey1 AND " + "ar.eventTimestamp = :evtVal1) )"
                        + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("evtKey0"), "e1");
        assertEquals(builder.getParameters().get("evtVal0"), CREATED_ON.getMillis());
        assertEquals(builder.getParameters().get("evtKey1"), "e2");
        assertEquals(builder.getParameters().get("evtVal1"), MODIFIED_ON.getMillis());
    }

    @Test
    public void createQuery_startTime() {
        AdherenceRecordsSearch search = search().withStartTime(CREATED_ON).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND ar.startedOn >= :startTime" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("startTime"), CREATED_ON.getMillis());
    }

    @Test
    public void createQuery_endTime() {
        AdherenceRecordsSearch search = search().withEndTime(MODIFIED_ON).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND ar.startedOn <= :endTime" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
        assertEquals(builder.getParameters().get("endTime"), MODIFIED_ON.getMillis());
    }

    @Test
    public void createQuery_sessionRecordsOnly() {
        AdherenceRecordsSearch search = search().withAdherenceRecordType(SESSION).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND tm.assessmentGuid IS NULL" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }

    @Test
    public void createQuery_assessmentRecordsOnly() {
        AdherenceRecordsSearch search = search().withAdherenceRecordType(ASSESSMENT).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND tm.assessmentGuid IS NOT NULL" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }

    @Test
    public void createQuery_orderAsc() {
        AdherenceRecordsSearch search = search().withSortOrder(ASC).build();

        QueryBuilder builder = dao.createQuery(search);
        // This could be variable, but since it comes from an enum's name, it
        // seems safe to just concatenate it
        assertEquals(builder.getQuery(), BASE_QUERY + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }

    @Test
    public void createQuery_orderDesc() {
        AdherenceRecordsSearch search = search().withSortOrder(DESC).build();

        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " ORDER BY ar.startedOn DESC");
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }
    
    @Test
    public void createQuery_withBoolean() {
        AdherenceRecordsSearch search = search().withDeclined(true).build();
        
        QueryBuilder builder = dao.createQuery(search);
        assertEquals(builder.getQuery(), BASE_QUERY + " AND declined = 1" + ORDER);
        assertEquals(builder.getParameters().get("studyId"), TEST_STUDY_ID);
        assertEquals(builder.getParameters().get("userId"), TEST_USER_ID);
    }

    @Test
    public void updateAdherenceRecord_saveStarted() {
        AdherenceRecord record = new AdherenceRecord();
        record.setStartedOn(CREATED_ON);

        dao.updateAdherenceRecord(record);

        verify(mockHelper).saveOrUpdate(record);
    }

    @Test
    public void updateAdherenceRecord_saveDeclined() {
        AdherenceRecord record = new AdherenceRecord();
        record.setDeclined(true);

        dao.updateAdherenceRecord(record);

        verify(mockHelper).saveOrUpdate(record);
    }

    @Test
    public void updateAdherenceRecord_deleteOnUpdate() {
        AdherenceRecord record = new AdherenceRecord();
        record.setAppId(TEST_APP_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setUserId(TEST_USER_ID);
        record.setInstanceGuid(GUID);
        record.setEventTimestamp(MODIFIED_ON);
        record.setInstanceTimestamp(MODIFIED_ON.plusHours(1));

        AdherenceRecord persisted = new AdherenceRecord();
        when(mockHelper.getById(eq(AdherenceRecord.class), any())).thenReturn(persisted);

        dao.updateAdherenceRecord(record);

        verify(mockHelper).deleteById(eq(AdherenceRecord.class), idCaptor.capture());
        assertEquals(idCaptor.getValue().getUserId(), TEST_USER_ID);
        assertEquals(idCaptor.getValue().getStudyId(), TEST_STUDY_ID);
        assertEquals(idCaptor.getValue().getInstanceGuid(), GUID);
        assertEquals(idCaptor.getValue().getEventTimestamp(), MODIFIED_ON);
        assertEquals(idCaptor.getValue().getInstanceTimestamp(), MODIFIED_ON.plusHours(1));
    }

    @Test
    public void updateAdherenceRecord_deleteRecordThatDoesNotExist() {
        AdherenceRecord record = new AdherenceRecord();
        record.setAppId(TEST_APP_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setUserId(TEST_USER_ID);
        record.setInstanceGuid(GUID);
        record.setEventTimestamp(MODIFIED_ON);
        record.setInstanceTimestamp(MODIFIED_ON.plusHours(1));
        when(mockHelper.getById(eq(AdherenceRecord.class), any())).thenReturn(null);

        dao.updateAdherenceRecord(record);

        verify(mockHelper).getById(eq(AdherenceRecord.class), any());
        verifyNoMoreInteractions(mockHelper);
    }

    private AdherenceRecordsSearch.Builder search() {
        return new AdherenceRecordsSearch.Builder().withUserId(TEST_USER_ID).withStudyId(TEST_STUDY_ID);
    }

    @Test
    public void deleteAdherenceRecord() {
        AdherenceRecord record = new AdherenceRecord();
        record.setUserId(TEST_USER_ID);
        record.setStudyId(TEST_STUDY_ID);
        record.setInstanceGuid(GUID);
        record.setEventTimestamp(MODIFIED_ON);
        record.setInstanceTimestamp(MODIFIED_ON.plusHours(1));

        when(mockHelper.getById(eq(AdherenceRecord.class), any())).thenReturn(record);

        dao.deleteAdherenceRecordPermanently(record);

        verify(mockHelper).deleteById(eq(AdherenceRecord.class), idCaptor.capture());

        AdherenceRecordId capturedId = idCaptor.getValue();
        assertEquals(capturedId.getUserId(), TEST_USER_ID);
        assertEquals(capturedId.getStudyId(), TEST_STUDY_ID);
        assertEquals(capturedId.getInstanceGuid(), GUID);
        assertEquals(capturedId.getEventTimestamp(), MODIFIED_ON);
        assertEquals(capturedId.getInstanceTimestamp(), MODIFIED_ON.plusHours(1));
    }

    @Test
    public void deleteNonExistentAdherenceRecord() {
        AdherenceRecord record = new AdherenceRecord();

        when(mockHelper.getById(eq(AdherenceRecord.class), any())).thenReturn(null);

        dao.deleteAdherenceRecordPermanently(record);

        verify(mockHelper).getById(eq(AdherenceRecord.class), any());
        verifyNoMoreInteractions(mockHelper);
    }
}
