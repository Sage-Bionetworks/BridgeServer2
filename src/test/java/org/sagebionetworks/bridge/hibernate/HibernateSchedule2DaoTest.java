package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.AND_DELETED;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.BATCH_SIZE_PROPERTY;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_ORPHANED_SESSIONS;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_SESSIONS;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.DELETE_TIMELINE_RECORDS;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.GET_ALL_SCHEDULES;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.GET_ORG_SCHEDULES;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.GET_SCHEDULE;
import static org.sagebionetworks.bridge.hibernate.HibernateSchedule2Dao.SELECT_COUNT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Schedule2Test;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;

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
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
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
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(100), eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);
        
        Map<String,Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String,Object> getParams = paramsCaptor.getAllValues().get(1);

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
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50), eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);
        
        Map<String,Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String,Object> getParams = paramsCaptor.getAllValues().get(1);

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
        
        PagedResourceList<Schedule2> retValue = dao.getSchedulesForOrganization(TEST_APP_ID, TEST_ORG_ID, 10, 100, false);
        assertEquals(retValue.getItems(), list);
        assertEquals(retValue.getTotal(), Integer.valueOf(2));
        
        verify(mockHibernateHelper).queryCount(queryCaptor.capture(), paramsCaptor.capture());
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(10), eq(100), eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);
        
        Map<String,Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String,Object> getParams = paramsCaptor.getAllValues().get(1);

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
        verify(mockHibernateHelper).queryGet(queryCaptor.capture(), paramsCaptor.capture(), eq(0), eq(50), eq(Schedule2.class));

        String countQuery = queryCaptor.getAllValues().get(0);
        String getQuery = queryCaptor.getAllValues().get(1);
        
        Map<String,Object> countParams = paramsCaptor.getAllValues().get(0);
        Map<String,Object> getParams = paramsCaptor.getAllValues().get(1);

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
        
        verify(mockHibernateHelper).queryGet(
                queryCaptor.capture(), paramsCaptor.capture(), isNull(), isNull(), eq(Schedule2.class));
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
        verify(mockQuery).setParameter(HibernateSchedule2Dao.SCHEDULE_GUID, TestConstants.SCHEDULE_GUID);
        verify(mockQuery).executeUpdate();
        verify(mockSession, times(2)).flush();
        verify(mockSession, times(2)).clear();
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
    }
    
    @Test
    public void updateScheduleWithNoSessions() {
        Schedule2 schedule = new Schedule2();
        schedule.setGuid("ScheduleGuid");

        Schedule2 retValue = dao.updateSchedule(schedule);
        assertEquals(retValue, schedule);
        
        verify(mockSession).createNativeQuery(queryCaptor.capture());
        assertEquals(queryCaptor.getValue(), DELETE_SESSIONS);
        verify(mockQuery).setParameter("guid", "ScheduleGuid");
        verify(mockQuery).executeUpdate();
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
}
