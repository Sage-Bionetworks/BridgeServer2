package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthUtils.CAN_ACCESS_ADHERENCE_DATA;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
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
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateSessionState;
import static org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress.UNSTARTED;
import static org.sagebionetworks.bridge.validators.AdherenceRecordListValidator.INSTANCE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.Optional;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.adherence.detailed.DetailedAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.detailed.DetailedAdherenceReportAssessmentRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.detailed.DetailedAdherenceReportSessionRecord;
import org.sagebionetworks.bridge.models.schedules2.timelines.AssessmentInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledAssessment;
import org.sagebionetworks.bridge.models.schedules2.timelines.ScheduledSession;
import org.sagebionetworks.bridge.models.schedules2.timelines.StudyBurstInfo;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.AuthEvaluatorField;
import org.sagebionetworks.bridge.dao.AdherenceRecordDao;
import org.sagebionetworks.bridge.dao.AdherenceReportDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.AdherenceReportSearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.activities.StudyActivityEventIdsMap;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordList;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordType;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceStatistics;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.study.StudyAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.study.StudyAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.study.StudyReportWeek;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.timelines.MetadataContainer;
import org.sagebionetworks.bridge.models.schedules2.timelines.SessionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.sagebionetworks.bridge.models.studies.Alert;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.AdherenceRecordsSearchValidator;
import org.sagebionetworks.bridge.validators.AdherenceReportSearchValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AdherenceService {
    private static final Logger LOG = LoggerFactory.getLogger(AdherenceService.class);
    
    static final StudyReportWeek EMPTY_WEEK = new StudyReportWeek();
    static final String THRESHOLD_OUT_OF_RANGE_ERROR = "Adherence threshold must be from 1-100.";
    static final String NO_THRESHOLD_VALUE_ERROR = "An adherence threshold value must be supplied in the request or set as a study default.";

    private AdherenceRecordDao recordDao;
    
    private AdherenceReportDao reportDao;

    private AlertService alertService;
    
    private StudyService studyService;

    private StudyActivityEventService studyActivityEventService;
    
    private Schedule2Service scheduleService;
    
    private RequestInfoService requestInfoService;
    
    @Autowired
    final void setAdherenceRecordDao(AdherenceRecordDao recordDao) {
        this.recordDao = recordDao;
    }

    @Autowired
    final void setAdherenceReportDao(AdherenceReportDao reportDao) {
        this.reportDao = reportDao;
    }

    @Autowired
    final void setAlertService(AlertService alertService) {
        this.alertService = alertService;
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
    
    @Autowired
    final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }
    
    public void updateAdherenceRecords(String appId, AdherenceRecordList recordList) {
        checkNotNull(recordList);

        if (recordList.getRecords().isEmpty()) {
            throw new BadRequestException("No adherence records submitted for update.");
        }

        LOG.info("Update adherence records for app " + appId + " with " + recordList.getRecords().size() + " records");

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
        
        String zoneId = studyService.getZoneId(appId, studyId, clientTimeZone);
        
        EventStreamAdherenceReport report = generateReport(appId, studyId, userId, now,
                zoneId, (state, schedule) -> EventStreamAdherenceReportGenerator.INSTANCE.generate(state, schedule));
        
        watch.stop();
        LOG.info("Event stream adherence report took " + watch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return report;
    }
    
    public StudyAdherenceReport getStudyAdherenceReport(String appId, String studyId, Account account) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(account);
        
        Stopwatch watch = Stopwatch.createStarted();
        
        DateTime createdOn = getDateTime();
        String zoneId = studyService.getZoneId(appId, studyId, account.getClientTimeZone());

        StudyAdherenceReport report = generateReport(appId, studyId, account.getId(), createdOn, zoneId,
                (state, schedule) -> StudyAdherenceReportGenerator.INSTANCE.generate(state, schedule));
        report.setParticipant(new AccountRef(account, studyId));
        report.setTestAccount(account.getDataGroups().contains(TEST_USER_GROUP));
        report.setCreatedOn(createdOn);
        report.setClientTimeZone(zoneId);
        
        deriveWeeklyAdherenceFromStudyReportWeek(studyId, account, report);
        
        watch.stop();
        LOG.info("Study adherence report took " + watch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return report;
    }
    
    public WeeklyAdherenceReport getWeeklyAdherenceReport(String appId, String studyId, Account account) {

        Stopwatch watch = Stopwatch.createStarted();
        
        DateTime createdOn = getDateTime();
        String zoneId = studyService.getZoneId(appId, studyId, account.getClientTimeZone());

        StudyAdherenceReport report = generateReport(appId, studyId, account.getId(), createdOn, zoneId,
                (state, schedule) -> StudyAdherenceReportGenerator.INSTANCE.generate(state, schedule));
        report.setParticipant(new AccountRef(account, studyId));
        report.setTestAccount(account.getDataGroups().contains(TEST_USER_GROUP));
        report.setCreatedOn(createdOn);
        report.setClientTimeZone(zoneId);
        
        WeeklyAdherenceReport weeklyReport = deriveWeeklyAdherenceFromStudyReportWeek(studyId, account, report);

        watch.stop();
        LOG.info("Weekly adherence report took " + watch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return weeklyReport;
    }

    public WeeklyAdherenceReport getWeeklyAdherenceReportForWorker(String appId, String studyId, Account account) {
        WeeklyAdherenceReport weeklyReport = getWeeklyAdherenceReport(appId, studyId, account);

        // trigger alert for low weekly adherence
        Study study = studyService.getStudy(appId, studyId, true);
        if (weeklyReport.getWeeklyAdherencePercent() != null
                && study.getAdherenceThresholdPercentage() != null
                && weeklyReport.getWeeklyAdherencePercent() <= study.getAdherenceThresholdPercentage()) {
            alertService.createAlert(
                    Alert.lowAdherence(studyId, appId, account.getId(), study.getAdherenceThresholdPercentage()));
        }

        return weeklyReport;
    }

    protected WeeklyAdherenceReport deriveWeeklyAdherenceFromStudyReportWeek(String studyId, Account account,
            StudyAdherenceReport report) {
        
        WeeklyAdherenceReport weeklyReport = new WeeklyAdherenceReport();
        weeklyReport.setAppId(account.getAppId());
        weeklyReport.setStudyId(studyId);
        weeklyReport.setUserId(account.getId());
        weeklyReport.setParticipant(report.getParticipant());
        weeklyReport.setTestAccount(report.isTestAccount());
        weeklyReport.setClientTimeZone(report.getClientTimeZone());
        weeklyReport.setCreatedOn(report.getCreatedOn());
        
        RequestInfo info = requestInfoService.getRequestInfo(account.getId());
        if (info == null || info.getSignedInOn() == null) {
            // Pad out this report so it is similar to reports that have no current active
            // tasks for the participant.
            weeklyReport.setProgression(UNSTARTED);
            weeklyReport.setRows(EMPTY_WEEK.getRows());
            weeklyReport.setByDayEntries(EMPTY_WEEK.getByDayEntries());
        } else {
            weeklyReport.setProgression(report.getProgression());
            StudyReportWeek week = report.getWeekReport();
            weeklyReport.setSearchableLabels(week.getSearchableLabels());
            weeklyReport.setRows(week.getRows());
            weeklyReport.setByDayEntries(week.getByDayEntries());
            weeklyReport.setWeeklyAdherencePercent(week.getAdherencePercent());
            weeklyReport.setWeekInStudy(week.getWeekInStudy());
            weeklyReport.setStartDate(week.getStartDate());
            if (week.getRows().isEmpty()) {
                weeklyReport.setNextActivity(report.getNextActivity());    
            }
        }
        reportDao.saveWeeklyAdherenceReport(weeklyReport);
        return weeklyReport;
    }

    public PagedResourceList<WeeklyAdherenceReport> getWeeklyAdherenceReports(String appId, String studyId,
            AdherenceReportSearch search) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(search);
        
        Validate.entityThrowingException(AdherenceReportSearchValidator.INSTANCE, search);

        return reportDao.getWeeklyAdherenceReports(appId, studyId, search)
                .withRequestParam(PagedResourceList.TEST_FILTER, search.getTestFilter())
                .withRequestParam(PagedResourceList.LABEL_FILTERS, search.getLabelFilters())
                .withRequestParam(PagedResourceList.ADHERENCE_MIN, search.getAdherenceMin())
                .withRequestParam(PagedResourceList.ADHERENCE_MAX, search.getAdherenceMax())
                .withRequestParam(PagedResourceList.PROGRESSION_FILTERS, search.getProgressionFilters())
                .withRequestParam(PagedResourceList.ID_FILTER, search.getIdFilter())
                .withRequestParam(PagedResourceList.OFFSET_BY, search.getOffsetBy())
                .withRequestParam(PagedResourceList.PAGE_SIZE, search.getPageSize());
    }
    
    protected <T> T generateReport(String appId, String studyId, String userId,
            DateTime createdOn, String clientTimeZone, BiFunction<AdherenceState, Schedule2, T> func) {
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withNow(createdOn);
        builder.withClientTimeZone(clientTimeZone);
        
        Study study = studyService.getStudy(appId, studyId, true);
        if (study.getScheduleGuid() == null) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        Schedule2 schedule = scheduleService.getScheduleForStudy(appId, studyId)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
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
        builder.withStudyStartEventId(study.getStudyStartEventId());
        AdherenceState state = builder.build();
        return func.apply(state, schedule);
    }
    
    public AdherenceStatistics getAdherenceStatistics(String appId, String studyId, Integer adherenceThreshold) {
        checkNotNull(appId);
        checkNotNull(studyId);
        
        Study study = studyService.getStudy(appId, studyId, true);
        
        scheduleService.getScheduleForStudy(appId, studyId)
            .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        
        if (adherenceThreshold == null) {
            adherenceThreshold = study.getAdherenceThresholdPercentage();
        }
        if (adherenceThreshold == null) {
            throw new BadRequestException(NO_THRESHOLD_VALUE_ERROR);
        }
        if (adherenceThreshold < 1 || adherenceThreshold > 100) {
            throw new BadRequestException(THRESHOLD_OUT_OF_RANGE_ERROR);
        }
        return reportDao.getAdherenceStatistics(appId, studyId, adherenceThreshold);
    }
    
    public DetailedAdherenceReport getDetailedAdherenceReportForParticipant(String appId, String studyId, Account account) {
        checkNotNull(appId);
        checkNotNull(studyId);
        checkNotNull(account);
        
        // Collect Adherence State
        DateTime createdOn = getDateTime();
        String zoneId = studyService.getZoneId(appId, studyId, account.getClientTimeZone());
//        DateTimeZone clientTimeZone = DateTimeZone.forID(zoneId);
        
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withNow(createdOn);
        builder.withClientTimeZone(zoneId);
        
        Study study = studyService.getStudy(appId, studyId, true);
        if (study.getScheduleGuid() == null) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        Schedule2 schedule = scheduleService.getScheduleForStudy(appId, studyId)
                .orElseThrow(() -> new EntityNotFoundException(Schedule2.class));
        List<TimelineMetadata> metadata = scheduleService.getScheduleMetadata(study.getScheduleGuid());
        
        List<Session> sessionsInSchedule = schedule.getSessions();
        
        Timeline timeline = scheduleService.getTimelineForSchedule(appId, schedule.getGuid());
        
        List<StudyActivityEvent> events = studyActivityEventService.getRecentStudyActivityEvents(
                appId, studyId, account.getId()).getItems();
        
        List<AdherenceRecord> adherenceRecords = getAdherenceRecords(appId, new AdherenceRecordsSearch.Builder()
                .withCurrentTimestampsOnly(true)
                // If includeRepeats=false (which is counter-intuitive) you will not get declined sessions with no 
                // startedOn value, but it's not needed because persistent time windows are excluded from adherence.
                .withIncludeRepeats(true)
//                .withAdherenceRecordType(AdherenceRecordType.SESSION)
                .withStudyId(studyId)
                .withUserId(account.getId())
                .build()).getItems();
        
        builder.withMetadata(metadata);
        builder.withEvents(events);
        builder.withAdherenceRecords(adherenceRecords);
        builder.withStudyStartEventId(study.getStudyStartEventId());
        AdherenceState state = builder.build();
        
        
        // Format timeline for labeling
        List<AssessmentInfo> assessmentInfos = timeline.getAssessments();
        List<StudyBurstInfo> studyBurstInfos = timeline.getStudyBursts();
        List<ScheduledSession> scheduledSessions = timeline.getSchedule();

//        List<TimelineMetadata> timelineMetadataList = timeline.getMetadata();
        
        Map<String, AssessmentInfo> assessmentInfoMap = new HashMap<>();
        
        // TODO: the study burst map might not be necessary
//        Map<String, StudyBurstInfo> studyBurstInfoMap = new HashMap<>();
        Map<String, ScheduledSession> scheduledSessionMap = new HashMap<>();
        Map<String, ScheduledAssessment> scheduledAssessmentMap = new HashMap<>();
        // Map with assessment instanceGuid keys to session instanceGuid values
        Map<String, String> assessmentParentMap = new HashMap<>();

//        Map<String, Session> sessionMap = new HashMap<>();
        Map<String, AssessmentReference> assessmentReferenceMap = new HashMap<>();
        
        for (AssessmentInfo assessmentInfo : assessmentInfos) {
            assessmentInfoMap.put(assessmentInfo.getKey(), assessmentInfo);
        }
//        for (StudyBurstInfo studyBurstInfo : studyBurstInfos) {
//            studyBurstInfoMap.put(studyBurstInfo.getIdentifier(), studyBurstInfo);
//        }
        Map<String, Integer> sortReferenceMap = new HashMap<>();
        int sortPosition = 0;
        for (ScheduledSession scheduledSession : scheduledSessions) {
            System.out.println("SCHEDULED SESSION INSTANCE GUID: " + scheduledSession.getInstanceGuid());
            if (scheduledSession.isPersistent() != null && scheduledSession.isPersistent()) {
                continue;
            }
            scheduledSessionMap.put(scheduledSession.getInstanceGuid(), scheduledSession);
            sortReferenceMap.put(scheduledSession.getInstanceGuid(), sortPosition);
            sortPosition++;
            
            List<ScheduledAssessment> scheduledAssessmentList = scheduledSession.getAssessments();
            for (ScheduledAssessment scheduledAssessment : scheduledAssessmentList) {
                assessmentParentMap.put(scheduledAssessment.getInstanceGuid(), scheduledSession.getInstanceGuid());
                scheduledAssessmentMap.put(scheduledAssessment.getInstanceGuid(), scheduledAssessment);
                sortReferenceMap.put(scheduledAssessment.getInstanceGuid(), sortPosition);
                sortPosition++;
            }
            
            for (AssessmentReference assessmentReference : scheduledSession.getSession().getAssessments()) {
//            for (AssessmentReference assessmentReference : session.getAssessments()) {
                assessmentReferenceMap.put(assessmentReference.getGuid(), assessmentReference);
//            }
            }
        }
//        for (Session session : sessionsInSchedule) {
//            sessionMap.put(session.getGuid(), session);
//        }
        
        // Create report
        DetailedAdherenceReport detailedAdherenceReport = new DetailedAdherenceReport();
        
        detailedAdherenceReport.setParticipant(new AccountRef(account, studyId));
        detailedAdherenceReport.setTestAccount(account.getDataGroups().contains(TEST_USER_GROUP));
        detailedAdherenceReport.setClientTimeZone(zoneId);
        
        Map<String, DetailedAdherenceReportSessionRecord> sessionRecords = new HashMap<>();
        
        for (Enrollment enrollment : account.getEnrollments()) {
            if (enrollment.getStudyId().equals(studyId)) {
                detailedAdherenceReport.setJoinedDate(enrollment.getEnrolledOn().withZone(state.getTimeZone()));
            }
        }
        
        for (AdherenceRecord record : adherenceRecords) {
            // instanceGuid for a scheduled session or assessment on the timeline
            String instanceGuid = record.getInstanceGuid();
            
            if (scheduledAssessmentMap.containsKey(instanceGuid)) {
                DetailedAdherenceReportAssessmentRecord assessmentRecord = new DetailedAdherenceReportAssessmentRecord();
                
                String refKey = scheduledAssessmentMap.get(instanceGuid).getRefKey();
                System.out.println("REF KEY: " + refKey);
                System.out.println("ASSESSMENT INFO MAP: " + assessmentInfoMap);
                AssessmentInfo assessmentInfo = assessmentInfoMap.get(refKey);
                System.out.println("ASSESSMENT INFO: " + assessmentInfo);
                AssessmentReference assessmentReference = assessmentReferenceMap.get(assessmentInfo.getGuid());
                
                assessmentRecord.setAssessmentInstanceGuid(instanceGuid);
                assessmentRecord.setAssessmentId(assessmentInfo.getIdentifier());
                assessmentRecord.setAssessmentGuid(assessmentInfo.getGuid());
                assessmentRecord.setAssessmentName(assessmentReference.getTitle());
                
                if (record.getStartedOn() != null) {
                    assessmentRecord.setAssessmentStart(record.getStartedOn().withZone(state.getTimeZone()));
                }
                if (record.getFinishedOn() != null) {
                    assessmentRecord.setAssessmentCompleted(record.getFinishedOn().withZone(state.getTimeZone()));
                }
                if (record.getUploadedOn() != null) {
                    assessmentRecord.setAssessmentUploadedOn(record.getUploadedOn());
                }
                
                
                if (record.isDeclined()) {
                    assessmentRecord.setAssessmentStatus("Declined");
                } else if (record.getFinishedOn() != null) {
                    assessmentRecord.setAssessmentStatus("Completed");
                } else if (record.getStartedOn() != null) {
                    assessmentRecord.setAssessmentStatus("Not Completed");
                }
                
                assessmentRecord.setSortPriority(sortReferenceMap.get(instanceGuid));
                
                
                // add session/burst info
                String parentInstanceGuid = assessmentParentMap.get(instanceGuid);
                
                ScheduledSession scheduledSession = scheduledSessionMap.get(parentInstanceGuid);
                
                fillSessionRecord(sessionRecords, scheduledSession, //sessionMap, 
                        state, sortReferenceMap, null);
                
                Map<String, DetailedAdherenceReportAssessmentRecord> assessmentRecords = sessionRecords
                        .get(scheduledSession.getInstanceGuid()).getAssessmentRecordMap();
                assessmentRecords.put(assessmentRecord.getAssessmentInstanceGuid(), assessmentRecord);
                
            } else if (scheduledSessionMap.containsKey(instanceGuid)) {
                ScheduledSession scheduledSession = scheduledSessionMap.get(instanceGuid);
                
                fillSessionRecord(sessionRecords, scheduledSession,// sessionMap,
                        state, sortReferenceMap, record);
            }
        }
        
        detailedAdherenceReport.setSessionRecords(sessionRecords);
        
        
        
        return detailedAdherenceReport;
    }
    
    private void fillSessionRecord(Map<String, DetailedAdherenceReportSessionRecord> sessionRecords,
                                   ScheduledSession scheduledSession,
//                                   Map<String, Session> sessionMap,
                                   AdherenceState state,
                                   Map<String, Integer> sortReferenceMap,
                                   AdherenceRecord record) {
        // If an adherence record is passed in, use it to update session adherence.
        // Otherwise just update the identifier fields.
        boolean updateSessionAdherence = (record != null);
        String instanceGuid = scheduledSession.getInstanceGuid();
        
        DetailedAdherenceReportSessionRecord sessionRecord;
        if (!sessionRecords.containsKey(instanceGuid)) {
            // The session record does not exist yet, so create it and update identifying fields
            sessionRecord = new DetailedAdherenceReportSessionRecord();
            
            if (scheduledSession.getStudyBurstId() != null) {
                int startDay = scheduledSession.getStartDay();
                int week = (startDay / 7) + 1;
                
                sessionRecord.setBurstName("Week " + week + "/Burst " + scheduledSession.getStudyBurstNum());
                sessionRecord.setBurstId(scheduledSession.getStudyBurstId());
            }
            
            // fill session info
//            String refGuid = scheduledSession.getRefGuid();
//            Session session = sessionMap.get(refGuid);

//            sessionRecord.setSessionName(session.getName());
            sessionRecord.setSessionName(scheduledSession.getSession().getName());
//            sessionRecord.setSessionGuid(refGuid);
            sessionRecord.setSessionGuid(scheduledSession.getSession().getGuid());
            sessionRecord.setSessionInstanceGuid(instanceGuid);
            
            sessionRecord.setSortPriority(sortReferenceMap.get(instanceGuid));
        } else {
            // The session record already exists.
            sessionRecord = sessionRecords.get(instanceGuid);
        }
        
        if (updateSessionAdherence) {
            // update session adherence fields
            if (record.getStartedOn() != null) {
                sessionRecord.setSessionStart(record.getStartedOn().withZone(state.getTimeZone()));
            }
            if (record.getFinishedOn() != null) {
                sessionRecord.setSessionCompleted(record.getFinishedOn().withZone(state.getTimeZone()));
            }
            
            int daysSinceEvent = state.getDaysSinceEventById(scheduledSession.getStartEventId());
            int startDay = scheduledSession.getStartDay();
            int endDay = scheduledSession.getEndDay();
            sessionRecord.setSessionStatus(calculateSessionState(record, startDay, endDay, daysSinceEvent));
            
            DateTime timestamp = state.getEventTimestampById(scheduledSession.getStartEventId());
            sessionRecord.setSessionExpiration(timestamp.plusDays(endDay).withZone(state.getTimeZone()));
        }
        
        sessionRecords.put(sessionRecord.getSessionInstanceGuid(), sessionRecord);
    }
}
