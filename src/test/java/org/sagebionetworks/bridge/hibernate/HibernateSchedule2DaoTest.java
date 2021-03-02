package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;

public class HibernateSchedule2DaoTest extends Mockito {
    
    @Mock
    HibernateHelper mockHibernateHelper;

    @InjectMocks
    HibernateSchedule2Dao dao;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
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

        assertEquals(countQuery, "SELECT count(*) FROM Schedule2 WHERE appId=:appId AND deleted = 0");
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, "FROM Schedule2 WHERE appId=:appId AND deleted = 0");
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

        assertEquals(countQuery, "SELECT count(*) FROM Schedule2 WHERE appId=:appId");
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, "FROM Schedule2 WHERE appId=:appId");
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

        assertEquals(countQuery, "SELECT count(*) FROM Schedule2 WHERE appId=:appId AND ownerId=:ownerId AND deleted = 0");
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, "FROM Schedule2 WHERE appId=:appId AND ownerId=:ownerId AND deleted = 0");
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

        assertEquals(countQuery, "SELECT count(*) FROM Schedule2 WHERE appId=:appId AND ownerId=:ownerId");
        assertEquals(countParams.get("appId"), TEST_APP_ID);

        assertEquals(getQuery, "FROM Schedule2 WHERE appId=:appId AND ownerId=:ownerId");
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
        assertEquals(queryCaptor.getValue(), "FROM Schedule2 WHERE appId=:appId and guid=:guid");
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
        Schedule2 schedule = new Schedule2();
        
        Schedule2 retValue = dao.createSchedule(schedule);
        assertEquals(retValue, schedule);
        
        verify(mockHibernateHelper).create(schedule);
    }

    @Test
    public void updateSchedule() {
        Schedule2 schedule = new Schedule2();
        
        Schedule2 retValue = dao.updateSchedule(schedule);
        assertEquals(retValue, schedule);
        
        verify(mockHibernateHelper).update(schedule);        
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
        
        verify(mockHibernateHelper).deleteById(Schedule2.class, GUID);
    }
}
