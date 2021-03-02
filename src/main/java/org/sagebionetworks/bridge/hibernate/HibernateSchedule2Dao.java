package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.Schedule2Dao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;

@Component
public class HibernateSchedule2Dao implements Schedule2Dao {
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public PagedResourceList<Schedule2> getSchedules(String appId, int offsetBy, int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        
        QueryBuilder query = new QueryBuilder();
        query.append("FROM Schedule2 WHERE appId=:appId", "appId", appId);
        if (!includeDeleted) {
            query.append("AND deleted = 0");
        }
        List<Schedule2> results = hibernateHelper.queryGet(
                query.getQuery(), query.getParameters(), offsetBy, pageSize, Schedule2.class);
        
        int total = hibernateHelper.queryCount("SELECT count(*) " + query.getQuery(), query.getParameters());
        
        return new PagedResourceList<>(ImmutableList.copyOf(results), total);
    }

    @Override
    public PagedResourceList<Schedule2> getSchedulesForOrganization(String appId, String ownerId, int offsetBy,
            int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        checkNotNull(ownerId);
        
        QueryBuilder query = new QueryBuilder();
        query.append("FROM Schedule2 WHERE appId=:appId AND ownerId=:ownerId", 
                "appId", appId, "ownerId", ownerId);
        if (!includeDeleted) {
            query.append("AND deleted = 0");
        }
        
        List<Schedule2> results = hibernateHelper.queryGet(
                query.getQuery(), query.getParameters(), offsetBy, pageSize, Schedule2.class);
        
        int total = hibernateHelper.queryCount("SELECT count(*) " + query.getQuery(), query.getParameters());
        
        return new PagedResourceList<>(ImmutableList.copyOf(results), total);
    }

    @Override
    public Optional<Schedule2> getSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        List<Schedule2> results = hibernateHelper.queryGet(
                "FROM Schedule2 WHERE appId=:appId and guid=:guid", 
                ImmutableMap.of("appId", appId, "guid", guid), null, null, Schedule2.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    @Override
    public Schedule2 createSchedule(Schedule2 schedule) {
        checkNotNull(schedule);
        
        hibernateHelper.create(schedule);
        return schedule;
    }

    @Override
    public Schedule2 updateSchedule(Schedule2 schedule) {
        checkNotNull(schedule);

        Set<String> sessionGuids = schedule.getSessions().stream()
                .map(Session::getGuid).collect(toSet());

        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            QueryBuilder builder = new QueryBuilder();
            builder.append("DELETE FROM ScheduleSessions WHERE scheduleGuid = :scheduleGuid",
                    "scheduleGuid", schedule.getGuid());
            if (!sessionGuids.isEmpty()) {
                builder.append("AND guid NOT IN (:guids)", "guids", sessionGuids);
            }
            NativeQuery<?> query = session.createNativeQuery(builder.getQuery());
            for (Map.Entry<String,Object> entry : builder.getParameters().entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());    
            }
            query.executeUpdate();
            
            session.update(schedule);
            return null;
        });
        return schedule;
    }
    
    @Override
    public void deleteSchedule(Schedule2 schedule) {
        checkNotNull(schedule);
        
        schedule.setDeleted(true);
        hibernateHelper.update(schedule);
    }

    @Override
    public void deleteSchedulePermanently(Schedule2 schedule) {
        checkNotNull(schedule);
        
        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            NativeQuery<?> query = session.createNativeQuery("DELETE FROM ScheduleSessions where scheduleGuid = :guid");
            query.setParameter("guid", schedule.getGuid());
            query.executeUpdate();
            
            session.remove(schedule);
            return null;
        });
    }
}
