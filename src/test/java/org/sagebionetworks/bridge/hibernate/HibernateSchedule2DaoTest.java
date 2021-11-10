package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.AND_DELETED;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.BATCH_SIZE_PROPERTY;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_ALL_SCHEDULES;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_ORPHANED_SESSIONS;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_SESSIONS;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_TIMELINE_RECORDS;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.GET_ALL_SCHEDULES;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.GET_ORG_SCHEDULES;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.GET_SCHEDULE;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.INSERT;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.INSTANCE_GUID;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.SELECT_ASSESSMENTS_FOR_SESSION_INSTANCE;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.SELECT_COUNT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.hibernate.query.NativeQuery;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

public class HibernateSchedule2DaoTest extends Mockito {

    @Mock
    HibernateHelper mockHibernateHelper;

    @Mock
    Session mockSession;

    @Mock
    NativeQuery<Schedule2> mockQuery;

    @Mock
    BridgeConfig mockConfig;

    @InjectMocks
    HibernateSchedule2Dao dao;

    @Captor
    ArgumentCaptor<String> queryCaptor;

    @Captor
    ArgumentCaptor<Map<String, Object>> paramsCaptor;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        when(mockConfig.getInt(BATCH_SIZE_PROPERTY)).thenReturn(10);

        dao.setBridgeConfig(mockConfig);

        when(mockSession.createNativeQuery(any())).thenReturn(mockQuery);

        when(mockHibernateHelper.executeWithExceptionHandling(any(), any())).thenAnswer(args -> {
            Function<Session, Schedule2> func = args.getArgument(1);
            func.apply(mockSession);
            return args.getArgument(0);
        });
    }

    @Test
    public void getSchedulesWithoutDeleted() {
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(Schedule2.class))).thenReturn(list);
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(2);

        PagedResourceList<Schedule2> retValue = dao.getSchedules(TEST_APP_ID, 10, 100, false);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(2));

        verify(mockHibernateHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(100),
                eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);

        Map<String, Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String, Object> getParams = paramsCaptor.getAllValues().get(1);

        assertEquals(countQuery, SELECT_COUNT + GET_ALL_SCHEDULES + " " + AND_DELETED);
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, GET_ALL_SCHEDULES + " " + AND_DELETED);
        assertEquals(getParams.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getSchedulesWithDeleted() {
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(Schedule2.class))).thenReturn(list);
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(20);

        PagedResourceList<Schedule2> retValue = dao.getSchedules(TEST_APP_ID, 0, 50, true);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(20));

        verify(mockHibernateHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50),
                eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);

        Map<String, Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String, Object> getParams = paramsCaptor.getAllValues().get(1);

        assertEquals(countQuery, SELECT_COUNT + GET_ALL_SCHEDULES);
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, GET_ALL_SCHEDULES);
        assertEquals(getParams.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getSchedulesForOrganizationWithoutDeleted() {
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(Schedule2.class))).thenReturn(list);
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(2);

        PagedResourceList<Schedule2> retValue = dao.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 10, 100,
                false);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(2));

        verify(mockHibernateHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(100),
                eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);

        Map<String, Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String, Object> getParams = paramsCaptor.getAllValues().get(1);

        assertEquals(countQuery, SELECT_COUNT + GET_ORG_SCHEDULES + " " + AND_DELETED);
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, GET_ORG_SCHEDULES + " " + AND_DELETED);
        assertEquals(getParams.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getSchedulesForOrganizationWithDeleted() {
        List<Schedule2> list = ImmutableList.of(new Schedule2(), new Schedule2());
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(Schedule2.class))).thenReturn(list);
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(20);

        PagedResourceList<Schedule2> retValue = dao.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 0, 50, true);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(20));

        verify(mockHibernateHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50),
                eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);

        Map<String, Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String, Object> getParams = paramsCaptor.getAllValues().get(1);

        assertEquals(countQuery, SELECT_COUNT + GET_ORG_SCHEDULES);
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, GET_ORG_SCHEDULES);
        assertEquals(getParams.get("appId"), TEST_APP_ID);
    }

    @Test
    public void getScheduleSucceeds() {
        Schedule2 schedule = new Schedule2();
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(Schedule2.class)))
                .thenReturn(ImmutableList.of(schedule));

        Optional<Schedule2> retValue = dao.getSchedule(TEST_APP_ID, GUID);
        assertTrue(retValue.isPresent());
        assertEquals(retValue.get(), schedule);

        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), isNull(), isNull(),
                eq(Schedule2.class));
        assertEquals(queryCaptor.getValue(), GET_SCHEDULE);
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
        assertEquals(paramsCaptor.getValue().get("guid"), GUID);
    }

    @Test
    public void getScheduleFails() {
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(Schedule2.class)))
                .thenReturn(ImmutableList.of());

        Optional<Schedule2> retValue = dao.getSchedule(TEST_APP_ID, GUID);
        assertFalse(retValue.isPresent());
    }

    @Test
    public void createSchedule() {
        Schedule2 schedule = Schedule2Test.createValidSchedule();

        when(mockSession.createNativeQuery(DELETE_TIMELINE_RECORDS)).thenReturn(mockQuery);

        Schedule2 retValue = dao.createSchedule(schedule);
        assertEquals(retValue, schedule);

        verify(mockSession).setJdbcBatchSize(10);
        verify(mockSession).save(schedule);
        verify(mockSession).doWork(any());
    }

    @Test
    public void updateSchedule() {
        Schedule2 schedule = Schedule2Test.createValidSchedule();

        org.sagebionetworks.bridge.models.schedules2.Session session1 = SessionTest.createValidSession();
        session1.setGuid(SESSION_GUID_1);

        org.sagebionetworks.bridge.models.schedules2.Session session2 = SessionTest.createValidSession();
        session2.setGuid(SESSION_GUID_2);
        schedule.setSessions(ImmutableList.of(session1, session2));

        Schedule2 retValue = dao.updateSchedule(schedule);
        assertEquals(retValue, schedule);

        verify(mockSession, times(2)).createNativeQuery(queryCaptor.capture());
        assertEquals(queryCaptor.getAllValues().get(0), DELETE_ORPHANED_SESSIONS);
        assertEquals(queryCaptor.getAllValues().get(1), DELETE_TIMELINE_RECORDS);
        verify(mockQuery).setParameter("guid", SCHEDULE_GUID);
        verify(mockQuery).setParameter("guids", ImmutableSet.of(SESSION_GUID_1, SESSION_GUID_2));
        verify(mockQuery, times(2)).executeUpdate();
        verify(mockSession).update(schedule);
        verify(mockSession).doWork(any());
    }

    @Test
    public void persistRecordsInBatches() throws SQLException {
        Schedule2 schedule = Schedule2Test.createValidSchedule();
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();

        Work work = dao.persistRecordsInBatches(metadata);

        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        when(mockConnection.prepareStatement(INSERT)).thenReturn(mockStatement);

        work.execute(mockConnection);

        // 84 records (2 for each record due to two event IDs and two study burst
        // IDs) at a batch size of 10 generates 9 executeBatch statements. The 
        // content of the statements is tested in updatePreparedStatement().
        assertEquals(metadata.size(), 84);
        verify(mockConnection).setAutoCommit(false);
        verify(mockConnection).prepareStatement(INSERT);
        verify(mockStatement, times(9)).executeBatch();
    }

    @Test
    public void updatePreparedStatement() throws Exception {
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        Schedule2 schedule = Schedule2Test.createValidSchedule();
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        TimelineMetadata meta = timeline.getMetadata().get(0);

        dao.updatePreparedStatement(mockStatement, meta);

        verify(mockStatement).setString(1, meta.getAppId());
        verify(mockStatement).setString(2, meta.getAssessmentGuid());
        verify(mockStatement).setString(3, meta.getAssessmentId());
        verify(mockStatement).setString(4, meta.getAssessmentInstanceGuid());
        verify(mockStatement).setNull(5, Types.NULL);
        verify(mockStatement).setString(6, meta.getScheduleGuid());
        verify(mockStatement).setLong(7, meta.getScheduleModifiedOn().getMillis());
        verify(mockStatement).setBoolean(8, meta.isSchedulePublished());
        verify(mockStatement).setString(9, meta.getSessionGuid());
        verify(mockStatement).setInt(10, meta.getSessionInstanceEndDay());
        verify(mockStatement).setString(11, meta.getSessionInstanceGuid());
        verify(mockStatement).setInt(12, meta.getSessionInstanceStartDay());
        verify(mockStatement).setString(13, meta.getSessionStartEventId());
        verify(mockStatement).setString(14, meta.getTimeWindowGuid());
        verify(mockStatement).setBoolean(15, meta.isTimeWindowPersistent());
        verify(mockStatement).setString(16, meta.getGuid());
        verify(mockStatement).addBatch();

        reset(mockStatement);
        meta = timeline.getMetadata().get(1);

        dao.updatePreparedStatement(mockStatement, meta);

        // this also works
        verify(mockStatement).setInt(5, meta.getAssessmentRevision());
    }

    @Test
    public void updateScheduleWithNoSessions() {
        Schedule2 schedule = new Schedule2();
        schedule.setGuid("ScheduleGuid");

        Schedule2 retValue = dao.updateSchedule(schedule);
        assertEquals(retValue, schedule);

        verify(mockSession).createNativeQuery(DELETE_TIMELINE_RECORDS);
        verify(mockSession).createNativeQuery(DELETE_SESSIONS);
        verify(mockQuery).setParameter("guid", "ScheduleGuid");
        verify(mockQuery, times(2)).executeUpdate();
        verify(mockSession).update(schedule);
    }

    @Test
    public void deleteSchedule() {
        Schedule2 schedule = new Schedule2();

        dao.deleteSchedule(schedule);
        assertTrue(schedule.isDeleted());

        verify(mockHibernateHelper).update(schedule);
    }

    @Test
    public void deleteSchedulePermanently() {
        Schedule2 schedule = new Schedule2();
        schedule.setGuid(GUID);

        dao.deleteSchedulePermanently(schedule);

        verify(mockSession).createNativeQuery(queryCaptor.capture());
        assertEquals(queryCaptor.getValue(), DELETE_SESSIONS);
        verify(mockQuery).setParameter("guid", schedule.getGuid());
        verify(mockQuery).executeUpdate();
        verify(mockSession).remove(schedule);
    }

    @Test
    public void getTimelineMetadata() {
        TimelineMetadata metadata = new TimelineMetadata();
        when(mockHibernateHelper.getById(TimelineMetadata.class, GUID)).thenReturn(metadata);

        Optional<TimelineMetadata> retValue = dao.getTimelineMetadata(GUID);
        assertEquals(retValue.get(), metadata);

        verify(mockHibernateHelper).getById(TimelineMetadata.class, GUID);
    }

    @Test
    public void getTimelineMetadataNull() {
        when(mockHibernateHelper.getById(TimelineMetadata.class, GUID)).thenReturn(null);

        Optional<TimelineMetadata> retValue = dao.getTimelineMetadata(GUID);
        assertFalse(retValue.isPresent());
    }

    @Test
    public void getAssessmentsForSessionInstance() {
        List<TimelineMetadata> results = ImmutableList.of();
        when(mockHibernateHelper.nativeQueryGet(any(), any(), any(), any(), eq(TimelineMetadata.class)))
                .thenReturn(results);

        List<TimelineMetadata> retValue = dao.getAssessmentsForSessionInstance(GUID);
        assertEquals(retValue, results);

        verify(mockHibernateHelper).nativeQueryGet(queryCaptor.capture(), paramsCaptor.capture(), isNull(), isNull(),
                eq(TimelineMetadata.class));

        assertEquals(queryCaptor.getValue(), SELECT_ASSESSMENTS_FOR_SESSION_INSTANCE);
        assertEquals(paramsCaptor.getValue().get(INSTANCE_GUID), GUID);
    }
    
    @Test
    public void deleteAllSchedules() {
        dao.deleteAllSchedules(TEST_APP_ID);
        
        verify(mockHibernateHelper).nativeQueryUpdate(eq(DELETE_ALL_SCHEDULES), paramsCaptor.capture());
        assertEquals(paramsCaptor.getValue().get("appId"), TEST_APP_ID);
    }
}
