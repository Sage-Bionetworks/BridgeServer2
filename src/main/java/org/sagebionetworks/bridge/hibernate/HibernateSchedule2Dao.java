package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toSet;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Resource;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.hibernate.jdbc.Work;
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
    static final String INSERT = "INSERT INTO TimelineMetadata (appId, assessmentGuid, assessmentId, assessmentInstanceGuid, "
            + "assessmentRevision, scheduleGuid, scheduleModifiedOn, schedulePublished, sessionGuid, sessionInstanceEndDay, "
            + "sessionInstanceGuid, sessionInstanceStartDay, sessionStartEventId, timeWindowGuid, timeWindowPersistent, guid, "
            + "studyBurstId, studyBurstNum) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String DELETE_TIMELINE_RECORDS = "DELETE FROM TimelineMetadata WHERE scheduleGuid = :scheduleGuid";
    static final String SELECT_ASSESSMENTS_FOR_SESSION_INSTANCE = "SELECT * FROM TimelineMetadata WHERE sessionInstanceGuid = :instanceGuid AND assessmentInstanceGuid IS NOT NULL";
    static final String DELETE_ALL_SCHEDULES = "DELETE FROM Schedules WHERE appId = :appId";
    static final String BATCH_SIZE_PROPERTY = "schedule.batch.size";

    static final String APP_ID = "appId";
    static final String INSTANCE_GUID = "instanceGuid";
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

        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            session.save(schedule);
            deleteAndRecreateTimelineMetadataRecords(session, schedule, false);
            return schedule;
        });
        return schedule;
    }

    @Override
    public Schedule2 updateSchedule(Schedule2 schedule) {
        checkNotNull(schedule);

        // Update the schedule
        hibernateHelper.executeWithExceptionHandling(schedule, (session) -> {
            Set<String> sessionGuids = schedule.getSessions().stream().map(Session::getGuid).collect(toSet());

            // Although orphanRemoval = true is set for the session collection, they do not
            // delete when removed. So we are manually finding the removed sessions and
            // deleting them before persisting the new set.
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
            deleteAndRecreateTimelineMetadataRecords(session, schedule, true);
            return schedule;
        });
        return schedule;
    }

    private void deleteAndRecreateTimelineMetadataRecords(org.hibernate.Session session, Schedule2 schedule, boolean deleteFirst) {
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();

        // batch these operations. Improves network performance
        session.setJdbcBatchSize(batchSize);

        if (deleteFirst) {
            Stopwatch deleteMetadataStopwatch = Stopwatch.createStarted();
            NativeQuery<?> query = session.createNativeQuery(DELETE_TIMELINE_RECORDS);
            query.setParameter(SCHEDULE_GUID, schedule.getGuid());
            query.executeUpdate();
            deleteMetadataStopwatch.stop();

            LOG.info("Batch deleting timeline metadata records in " + 
                    deleteMetadataStopwatch.elapsed(MILLISECONDS) + " ms");
        }
        
        // This is necessary or the timeline records fail for lack of a session guid (hasn't been registered yet
        // on creates).
        session.flush();

        // Hibernate’s session.save() does an insert and then an update operation on each record, so 
        // switching to JDBC to do an insert only, halves the time it takes to do this operation
        // even without further batch optimizations. I was not able to determine why Hibernate is doing 
        // this (it’s not the most frequently cited culprit, an @Id generator, because we don’t use one).
        Stopwatch createMetadataStopwatch = Stopwatch.createStarted();
        session.doWork(persistRecordsInBatches(metadata));
        createMetadataStopwatch.stop();

        LOG.info("Persisting " + metadata.size() + " timeline metadata records in "
                + createMetadataStopwatch.elapsed(MILLISECONDS) + " ms (batchSize = " + batchSize + ")");
    }

    /**
     * For batch operations to work efficiently using the MySQL driver, rewriteBatchedStatements=true 
     * must be included in the connector string, auto commit must be off, and you must use the batch 
     * commit method. A batch size of 100 seems about optimal (values below lose performance but I 
     * cannot measure any benefit to having larger values).
     */
    protected Work persistRecordsInBatches(List<TimelineMetadata> metadata) {
        return (connection) -> {
            try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
                connection.setAutoCommit(false);

                for (int i = 0, len = metadata.size(); i < len; i++) {
                    TimelineMetadata meta = metadata.get(i);
                    updatePreparedStatement(ps, meta);
                    if (i > 0 && (i % batchSize) == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
        };
    }

    // For testability, removing this to a separate method
    protected void updatePreparedStatement(PreparedStatement ps, TimelineMetadata meta) throws SQLException {
        ps.setString(1, meta.getAppId());
        ps.setString(2, meta.getAssessmentGuid());
        ps.setString(3, meta.getAssessmentId());
        ps.setString(4, meta.getAssessmentInstanceGuid());
        // why does setInt alone not like a null value? Not sure
        if (meta.getAssessmentRevision() == null) {
            ps.setNull(5, Types.NULL);
        } else {
            ps.setInt(5, meta.getAssessmentRevision());
        }
        ps.setString(6, meta.getScheduleGuid());
        ps.setLong(7, meta.getScheduleModifiedOn().getMillis());
        ps.setBoolean(8, meta.isSchedulePublished());
        ps.setString(9, meta.getSessionGuid());
        ps.setInt(10, meta.getSessionInstanceEndDay());
        ps.setString(11, meta.getSessionInstanceGuid());
        ps.setInt(12, meta.getSessionInstanceStartDay());
        ps.setString(13, meta.getSessionStartEventId());
        ps.setString(14, meta.getTimeWindowGuid());
        ps.setBoolean(15, meta.isTimeWindowPersistent());
        ps.setString(16, meta.getGuid());
        System.out.println(meta.getStudyBurstId());
        ps.setString(17, meta.getStudyBurstId());
        System.out.println(meta.getStudyBurstNum());
        if (meta.getStudyBurstNum() == null) {
            ps.setInt(18, Types.NULL);
        } else {
            ps.setInt(18, meta.getStudyBurstNum());    
        }
        ps.addBatch();
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

    @Override
    public List<TimelineMetadata> getAssessmentsForSessionInstance(String instanceGuid) {
        checkNotNull(instanceGuid);

        QueryBuilder builder = new QueryBuilder();
        builder.append(SELECT_ASSESSMENTS_FOR_SESSION_INSTANCE, INSTANCE_GUID, instanceGuid);

        return hibernateHelper.nativeQueryGet(builder.getQuery(), builder.getParameters(), null, null,
                TimelineMetadata.class);
    }
    
    @Override
    public void deleteAllSchedules(String appId) {
        checkNotNull(appId);

        QueryBuilder builder = new QueryBuilder();
        builder.append(DELETE_ALL_SCHEDULES, APP_ID, appId);

        hibernateHelper.nativeQueryUpdate(builder.getQuery(), builder.getParameters());
    }
}
