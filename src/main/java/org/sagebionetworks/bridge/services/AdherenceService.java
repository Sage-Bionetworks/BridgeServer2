package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthUtils.CAN_ACCESS_ADHERENCE_DATA;
import static org.sagebionetworks.bridge.BridgeUtils.formatActivityEventId;
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
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SESSION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.INSTANCE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.dao.AdherenceReportDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.timelines.MetadataContainer;
import org.sagebionetworks.bridge.models.schedules2.timelines.SessionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AdherenceService {
    private static final Logger LOG = LoggerFactory.getLogger(AdherenceService.class);

    private AdherenceRecordDao recordDao;
    
    private AdherenceReportDao reportDao;
    
    private StudyService studyService;

    private StudyActivityEventService studyActivityEventService;
    
    private Schedule2Service scheduleService;
    
    @Autowired
    final void setAdherenceRecordDao(AdherenceRecordDao recordDao) {
        this.recordDao = recordDao;
    }

    @Autowired
    final void setAdherenceReportDao(AdherenceReportDao reportDao) {
        this.reportDao = reportDao;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setStudyActivityEventService(StudyActivityEventService studyActivityEventService) {
        this.studyActivityEventService = studyActivityEventService;
    }
    
    @Autowired
    final void setSchedule2Service(Schedule2Service scheduleService) {
        this.scheduleService = scheduleService;
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }
    
    public void updateAdherenceRecords(String appId, AdherenceRecordList recordList) {
        checkNotNull(recordList);
        
        if (recordList.getRecords().isEmpty()) {
            throw new BadRequestException("No adherence records submitted for update.");
        }
        
        Validate.entityThrowingException(INSTANCE, recordList);

        // The only caller of this method sets all the userId and studyId fields, 
        // so this only needs to be called once.
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                AuthEvaluatorField.STUDY_ID, recordList.getRecords().get(0).getStudyId(), 
                AuthEvaluatorField.USER_ID, recordList.getRecords().get(0).getUserId());
        
        MetadataContainer container = new MetadataContainer(scheduleService, recordList.getRecords());
        
        // Update assessments
        for (AdherenceRecord record : container.getAssessments()) {
            TimelineMetadata meta = container.getMetadata(record.getInstanceGuid());
            recordDao.updateAdherenceRecord(record);
            publishEvent(appId, meta, record);
        }
        // Update sessions implied by assessments
        for (AdherenceRecord record : container.getAssessments()) {
            updateSessionState(appId, container, record);
        }
        // Update sessions
        for (AdherenceRecord record : container.getSessionUpdates()) {
            TimelineMetadata sessionMeta = container.getMetadata(record.getInstanceGuid());
            recordDao.updateAdherenceRecord(record);
            publishEvent(appId, sessionMeta, record);
        }
    }
    
    protected void updateSessionState(String appId, MetadataContainer container, AdherenceRecord asmt) {
        TimelineMetadata asmtMeta = container.getMetadata(asmt.getInstanceGuid());
        String sessionInstanceGuid = asmtMeta.getSessionInstanceGuid();

        List<TimelineMetadata> asmtMetas = scheduleService.getSessionAssessmentMetadata(sessionInstanceGuid);

        Set<String> instanceGuids = asmtMetas.stream()
                .map(TimelineMetadata::getAssessmentInstanceGuid)
                .collect(toSet());
        instanceGuids.add(sessionInstanceGuid);
        
        PagedResourceList<AdherenceRecord> allRecords = recordDao.getAdherenceRecords(new AdherenceRecordsSearch.Builder()
                .withUserId(asmt.getUserId())
                .withStudyId(asmt.getStudyId())
                .withEventTimestamps(ImmutableMap.of(asmtMeta.getSessionStartEventId(), asmt.getEventTimestamp()))
                .withInstanceGuids(instanceGuids).build());
        
        SessionState state = new SessionState(asmtMetas.size());
        
        // The session record may have been submitted, it may be persisted, or
        // it may not yet exist, and we take the records in that order.
        AdherenceRecord sessionRecord = container.getRecord(sessionInstanceGuid);
        for (AdherenceRecord oneRecord : allRecords.getItems()) {
            if (sessionInstanceGuid.equals(oneRecord.getInstanceGuid())) {
                // The record was persisted
                if (sessionRecord == null) {
                    sessionRecord = oneRecord;
                }
            } else {
                state.add(oneRecord);
            }
        }
        // The record is new and needs to be created
        if (sessionRecord == null) {
            sessionRecord = new AdherenceRecord();
            sessionRecord.setAppId(asmt.getAppId());
            sessionRecord.setUserId(asmt.getUserId());
            sessionRecord.setStudyId(asmt.getStudyId());
            sessionRecord.setInstanceGuid(sessionInstanceGuid);
            sessionRecord.setEventTimestamp(asmt.getEventTimestamp());
        }
        if (state.updateSessionRecord(sessionRecord)) {
            container.addRecord(sessionRecord);
        }
    }

    protected void publishEvent(String appId, TimelineMetadata meta, AdherenceRecord record) {
        if (meta != null && record.getFinishedOn() != null) {
            StudyActivityEvent.Builder builder = new StudyActivityEvent.Builder()
                    .withAppId(appId)
                    .withStudyId(record.getStudyId())
                    .withUserId(record.getUserId())
                    .withEventType(FINISHED)
                    .withTimestamp(record.getFinishedOn());
            if (meta.getAssessmentInstanceGuid() == null) {
                builder.withObjectType(SESSION);
                builder.withObjectId(meta.getSessionGuid());
            } else {
                // Shared and local assessment ID are conceptually different but not 
                // differentiated for events scheduling. It might be helpful to end
                // users or we might need to change this.
                builder.withObjectType(ASSESSMENT);
                builder.withObjectId(meta.getAssessmentId());
            }
            studyActivityEventService.publishEvent(builder.build(), false, true);
        }
    }

    public PagedResourceList<AdherenceRecord> getAdherenceRecords(String appId, AdherenceRecordsSearch search) {
        
        Set<String> originalInstanceGuids = ImmutableSet.copyOf(search.getInstanceGuids());
        
        search = cleanupSearch(appId, search);
        
        Validate.entityThrowingException(AdherenceRecordsSearchValidator.INSTANCE, search);
        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                AuthEvaluatorField.STUDY_ID, search.getStudyId(), 
                AuthEvaluatorField.USER_ID, search.getUserId());
        
        return recordDao.getAdherenceRecords(search)
                .withRequestParam(ASSESSMENT_IDS, search.getAssessmentIds())
                .withRequestParam(CURRENT_TIMESTAMPS_ONLY, search.getCurrentTimestampsOnly())
                .withRequestParam(END_TIME, search.getEndTime())
                .withRequestParam(EVENT_TIMESTAMPS, search.getEventTimestamps())
                .withRequestParam(INCLUDE_REPEATS, search.getIncludeRepeats())
                .withRequestParam(INSTANCE_GUIDS, originalInstanceGuids)
                .withRequestParam(OFFSET_BY, search.getOffsetBy())
                .withRequestParam(PAGE_SIZE, search.getPageSize())
                .withRequestParam(PREDICATE, search.getPredicate())
                .withRequestParam(ADHERENCE_RECORD_TYPE, search.getAdherenceRecordType())
                .withRequestParam(SESSION_GUIDS, search.getSessionGuids())
                .withRequestParam(SORT_ORDER, search.getSortOrder())
                .withRequestParam(START_TIME, search.getStartTime())
                .withRequestParam(STRING_SEARCH_POSITION, search.getStringSearchPosition())
                .withRequestParam(STUDY_ID, search.getStudyId())
                .withRequestParam(TIME_WINDOW_GUIDS, search.getTimeWindowGuids());
    }

    protected AdherenceRecordsSearch cleanupSearch(String appId, AdherenceRecordsSearch search) {
        checkNotNull(appId);
        checkNotNull(search);
        
        // optimization: skip all this if not relevant to the search
        boolean skipFixes = search.getEventTimestamps().isEmpty() && search.getInstanceGuids().isEmpty()
                && Boolean.FALSE.equals(search.getCurrentTimestampsOnly());
        if (skipFixes) {
            return search;
        }
        AdherenceRecordsSearch.Builder builder = search.toBuilder();
        
        if (TRUE.equals(search.getCurrentTimestampsOnly()) || !search.getEventTimestamps().isEmpty()) {
            StudyActivityEventIdsMap eventMap = studyService.getStudyActivityEventIdsMap(appId, search.getStudyId());

            Map<String, DateTime> fixedMap = new HashMap<>();
            if (TRUE.equals(search.getCurrentTimestampsOnly())) {
                // This adds current server timestamps to the search filters
                Map<String, DateTime> events = studyActivityEventService
                        .getRecentStudyActivityEvents(appId, search.getStudyId(), search.getUserId())
                        .getItems().stream()
                        .collect(toMap(StudyActivityEvent::getEventId, StudyActivityEvent::getTimestamp));
                addToMap(events, eventMap, fixedMap);
            }
            if (!search.getEventTimestamps().isEmpty()) {
                // This fixes things like failing to put a "custom:" prefix on a custom event.
                addToMap(search.getEventTimestamps(), eventMap, fixedMap);
            }
            builder.withEventTimestamps(fixedMap);
        }
        // parse any compound instance IDs that include a startedOn value, and move them 
        // to a different map.
        if (!search.getInstanceGuids().isEmpty()) {
            Map<String, DateTime> map = new HashMap<>();
            Set<String> instanceGuids = new HashSet<>();
            
            for (String instanceGuid : search.getInstanceGuids()) {
                String[] split = instanceGuid.split("@", 2);
                if (split.length == 2) {
                    map.put(split[0], DateTime.parse(split[1]));
                } else {
                    instanceGuids.add(instanceGuid);
                }
            }
            builder.withInstanceGuidStartedOnMap(map);
            builder.withInstanceGuids(instanceGuids);
        }
        return builder.build();
    }

    protected void addToMap(Map<String, DateTime> events, StudyActivityEventIdsMap eventMap, Map<String, DateTime> fixedMap) {
        for (Map.Entry<String, DateTime> entry : events.entrySet()) {
            String eventId = entry.getKey();
            String fixedEventId = formatActivityEventId(eventMap, eventId);
            if (fixedEventId != null) {
                fixedMap.put(fixedEventId, entry.getValue());    
            }
        }
    }
    
    public void deleteAdherenceRecord(AdherenceRecord record) {
        checkNotNull(record);

        CAN_ACCESS_ADHERENCE_DATA.checkAndThrow(
                AuthEvaluatorField.STUDY_ID, record.getStudyId(),
                AuthEvaluatorField.USER_ID, record.getUserId()
        );

        if (record.getEventTimestamp() == null) {
            throw new BadRequestException("eventTimestamp can not be null");
        }
        if (record.getStartedOn() == null) {
            throw new BadRequestException("startedOn can not be null");
        }

        Optional<TimelineMetadata> timelineMetadata = scheduleService.getTimelineMetadata(record.getInstanceGuid());
        if (timelineMetadata.isPresent()) {
            if (timelineMetadata.get().isTimeWindowPersistent()) {
                record.setInstanceTimestamp(record.getStartedOn());
            } else {
                record.setInstanceTimestamp(record.getEventTimestamp());
            }

            recordDao.deleteAdherenceRecordPermanently(record);
        }
    }
    
    public EventStreamAdherenceReport getEventStreamAdherenceReport(String appId, String studyId, String userId,
            DateTime now, String clientTimeZone, boolean showActiveOnly) {

        Stopwatch watch = Stopwatch.createStarted();
        
        EventStreamAdherenceReport report = getEventStreamAdherenceReportInternal(
                appId, studyId, userId, now, clientTimeZone, showActiveOnly);
        
        watch.stop();
        LOG.info("Event stream adherence report took " + watch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return report;
    }
    
    private EventStreamAdherenceReport getEventStreamAdherenceReportInternal(String appId, String studyId, String userId,
            DateTime now, String clientTimeZone, boolean showActiveOnly) {
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withShowActive(showActiveOnly);
        builder.withNow(now);
        builder.withClientTimeZone(clientTimeZone);

        Study study = studyService.getStudy(appId, studyId, true);
        if (study.getScheduleGuid() == null) {
            return EventStreamAdherenceReportGenerator.INSTANCE.generate(builder.build());
        }
        List<TimelineMetadata> metadata = scheduleService.getScheduleMetadata(study.getScheduleGuid());

        List<StudyActivityEvent> events = studyActivityEventService.getRecentStudyActivityEvents(
                appId, studyId, userId).getItems();

        List<AdherenceRecord> adherenceRecords = getAdherenceRecords(appId, new AdherenceRecordsSearch.Builder()
                .withCurrentTimestampsOnly(true)
                // If includeRepeats=false (which is counter-intuitive) you will not get declined sessions with no 
                // startedOn value, but it's not needed because persistent time windows are excluded from adherence.
                .withIncludeRepeats(true) 
                .withAdherenceRecordType(AdherenceRecordType.SESSION)
                .withStudyId(studyId)
                .withUserId(userId)
                .build()).getItems();
        
        builder.withMetadata(metadata);
        builder.withEvents(events);
        builder.withAdherenceRecords(adherenceRecords);

        return EventStreamAdherenceReportGenerator.INSTANCE.generate(builder.build());
    }
    
    public WeeklyAdherenceReport getWeeklyAdherenceReport(String appId, String studyId, Account account) {

        Stopwatch watch = Stopwatch.createStarted();
        
        DateTime createdOn = getDateTime();

        WeeklyAdherenceReport report = getWeeklyAdherenceReportInternal(
                appId, studyId, account.getId(), createdOn, account.getClientTimeZone());
        report.setAppId(appId);
        report.setStudyId(studyId);
        report.setUserId(account.getId());
        report.setParticipant(new AccountRef(account, studyId));
        
        reportDao.saveWeeklyAdherenceReport(report);
        
        watch.stop();
        LOG.info("Weekly adherence report took " + watch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return report;
    }
    
    public PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(String appId, String studyId,
            String labelFilter, Integer complianceUnder, Integer offsetBy, Integer pageSize) {
        
        
        return reportDao.getWeeklyAdherenceReports(appId, studyId, labelFilter, complianceUnder, offsetBy, pageSize)
                .withRequestParam(PagedResourceList.LABEL_FILTER, labelFilter)
                .withRequestParam(PagedResourceList.COMPLIANCE_UNDER, complianceUnder)
                .withRequestParam(PagedResourceList.OFFSET_BY, offsetBy)
                .withRequestParam(PagedResourceList.PAGE_SIZE, pageSize);
    }
    
    private WeeklyAdherenceReport getWeeklyAdherenceReportInternal(String appId, String studyId, String userId,
            DateTime createdOn, String clientTimeZone) {
        
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withNow(createdOn);
        builder.withClientTimeZone(clientTimeZone);
        
        Study study = studyService.getStudy(appId, studyId, true);
        if (study.getScheduleGuid() == null) {
            return WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
        }
        List<TimelineMetadata> metadata = scheduleService.getScheduleMetadata(study.getScheduleGuid());

        List<StudyActivityEvent> events = studyActivityEventService.getRecentStudyActivityEvents(
                appId, studyId, userId).getItems();

        List<AdherenceRecord> adherenceRecords = getAdherenceRecords(appId, new AdherenceRecordsSearch.Builder()
                .withCurrentTimestampsOnly(true)
                // If includeRepeats=false (which is counter-intuitive) you will not get declined sessions with no 
                // startedOn value, but it's not needed because persistent time windows are excluded from adherence.
                .withIncludeRepeats(true)
                .withAdherenceRecordType(AdherenceRecordType.SESSION)
                .withStudyId(studyId)
                .withUserId(userId)
                .build()).getItems();
        
        builder.withMetadata(metadata);
        builder.withEvents(events);
        builder.withAdherenceRecords(adherenceRecords);
        return WeeklyAdherenceReportGenerator.INSTANCE.generate(builder.build());
    }
}