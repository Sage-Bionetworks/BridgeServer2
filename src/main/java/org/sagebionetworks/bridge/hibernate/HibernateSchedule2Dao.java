package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.query.NativeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.Schedule2Dao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;

@Component
public class HibernateSchedule2Dao implements Schedule2Dao {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateSchedule2Dao.class);

    static final String SELECT_COUNT = "SELECT count(*) ";
    static final String GET_ALL_SCHEDULES = "FROM Schedule2 WHERE appId=:appId";
    static final String GET_ORG_SCHEDULES = GET_ALL_SCHEDULES + " AND ownerId=:ownerId";
    static final String GET_SCHEDULE = "FROM Schedule2 WHERE appId=:appId and guid=:guid";
    static final String DELETE_SESSIONS = "DELETE FROM Sessions where scheduleGuid = :guid";
    static final String DELETE_ORPHANED_SESSIONS = "DELETE FROM Sessions where scheduleGuid = :guid AND guid NOT IN (:guids)";
    static final String AND_DELETED = "AND deleted = 0";
    static final String AND_NOT_IN_GUIDS = "AND guid NOT IN (:guids)";
    static final String DELETE_TIMELINE_RECORDS = "DELETE FROM TimelineMetadata WHERE scheduleGuid = :scheduleGuid";

    static final String BATCH_SIZE_PROPERTY = "schedule.batch.size";

    static final String APP_ID = "appId";
    static final String OWNER_ID = "ownerId";
    static final String GUID = "guid";
    static final String GUIDS = "guids";
    static final String SCHEDULE_GUID = "scheduleGuid";

    private HibernateHelper hibernateHelper;
    private int batchSize;

    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Autowired
    public void setBridgeConfig(BridgeConfig config) {
        this.batchSize = config.getInt(BATCH_SIZE_PROPERTY);
    }

    @Override
    public PagedResourceList<Schedule2> getSchedules(String appId, int offsetBy, int pageSize, boolean includeDeleted) {
        checkNotNull(appId);

        QueryBuilder query = new QueryBuilder();
        query.append(GET_ALL_SCHEDULES, APP_ID, appId);
        if (!includeDeleted) {
            query.append(AND_DELETED);
        }
        List<Schedule2> results = hibernateHelper.queryGet(query.getQuery(), query.getParameters(), offsetBy, pageSize,
                Schedule2.class);

        int total = hibernateHelper.queryCount(SELECT_COUNT + query.getQuery(), query.getParameters());

        return new PagedResourceList<>(ImmutableList.copyOf(results), total);
    }

    @Override
    public PagedResourceList<Schedule2> getSchedulesForOrganization(String appId, String orgId, int offsetBy,
            int pageSize, boolean includeDeleted) {
        checkNotNull(appId);
        checkNotNull(orgId);

        QueryBuilder query = new QueryBuilder();
        query.append(GET_ORG_SCHEDULES, APP_ID, appId, OWNER_ID, orgId);
        if (!includeDeleted) {
            query.append(AND_DELETED);
        }

        List<Schedule2> results = hibernateHelper.queryGet(query.getQuery(), query.getParameters(), offsetBy, pageSize,
                Schedule2.class);

        int total = hibernateHelper.queryCount(SELECT_COUNT + query.getQuery(), query.getParameters());

        return new PagedResourceList<>(ImmutableList.copyOf(results), total);
    }

    @Override
    public Optional<Schedule2> getSchedule(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);

        List<Schedule2> results = hibernateHelper.queryGet(GET_SCHEDULE, ImmutableMap.of(APP_ID, appId, GUID, guid),
                null, null, Schedule2.class);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    @Override
    public Schedule2 createSchedule(Schedule2 schedule) {
        checkNotNull(schedule);

        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();

        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            // batch these operations. Improves network performance
            session.setJdbcBatchSize(batchSize);

            session.save(schedule);

            Stopwatch stopwatch = Stopwatch.createStarted();
            // Batch deleting/recreating rather than updating is 2-3 orders of magnitude faster.
            NativeQuery<?> query = session.createNativeQuery(DELETE_TIMELINE_RECORDS);
            query.setParameter(SCHEDULE_GUID, schedule.getGuid());
            query.executeUpdate();

            for (int i = 0, len = metadata.size(); i < len; i++) {
                TimelineMetadata meta = metadata.get(i);
                session.save(meta);
                if (i > 0 && (i % batchSize) == 0) {
                    session.flush();
                    session.clear();
                }
            }
            stopwatch.stop();
            LOG.info("Persisting " + metadata.size() + " timeline metadata records in "
                    + stopwatch.elapsed(MILLISECONDS) + " ms (batchSize = " + batchSize + ")");

            return null;
        });
        return schedule;
    }

    @Override
    public Schedule2 updateSchedule(Schedule2 schedule) {
        checkNotNull(schedule);

        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();

        Set<String> sessionGuids = schedule.getSessions().stream().map(Session::getGuid).collect(toSet());

        // Although orphanRemoval = true is set for the session collection, they do not
        // delete when removed. So we are manually finding the removed sessions and deleting
        // them before persisting.
        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            // batch these operations. Improves network performance
            session.setJdbcBatchSize(batchSize);

            QueryBuilder builder = new QueryBuilder();
            builder.append(DELETE_SESSIONS, GUID, schedule.getGuid());
            if (!sessionGuids.isEmpty()) {
                builder.append(AND_NOT_IN_GUIDS, GUIDS, sessionGuids);
            }
            NativeQuery<?> query = session.createNativeQuery(builder.getQuery());
            for (Map.Entry<String, Object> entry : builder.getParameters().entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            query.executeUpdate();

            session.update(schedule);

            Stopwatch stopwatch = Stopwatch.createStarted();
            
            // Batch deleting/recreating rather than updating is 2-3 orders of magnitude faster.
            query = session.createNativeQuery(DELETE_TIMELINE_RECORDS);
            query.setParameter(SCHEDULE_GUID, schedule.getGuid());
            query.executeUpdate();
            // Create a new set of records
            for (int i = 0, len = metadata.size(); i < len; i++) {
                TimelineMetadata meta = metadata.get(i);
                session.save(meta);
                if (i > 0 && (i % batchSize) == 0) {
                    session.flush();
                    session.clear();
                }
            }
            stopwatch.stop();
            LOG.info("Persisting " + metadata.size() + " timeline metadata records in "
                    + stopwatch.elapsed(MILLISECONDS) + " ms (batchSize = " + batchSize + ")");

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

        // Although orphanRemoval = true is set for the session collection, they do not
        // delete when we delete the schedule. So we are manually deleting the sessions
        // before deleting the schedule.
        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            NativeQuery<?> query = session.createNativeQuery(DELETE_SESSIONS);
            query.setParameter(GUID, schedule.getGuid());
            query.executeUpdate();

            session.remove(schedule);
            return null;
        });
    }

    @Override
    public Optional<TimelineMetadata> getTimelineMetadata(String instanceGuid) {
        checkNotNull(instanceGuid);

        TimelineMetadata tm = hibernateHelper.getById(TimelineMetadata.class, instanceGuid);
        return Optional.ofNullable(tm);
    }
}
