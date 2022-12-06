package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_RELAXED_ID_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.BRIDGE_RELAXED_ID_PATTERN;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_DUPLICATE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateColorScheme;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateJsonLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateMessages;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Notification;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class SessionValidator implements Validator {

    static final String APP_ID_FIELD = "appId";
    static final String ASSESSMENTS_FIELD = "assessments";
    static final String COLOR_SCHEME_FIELD = "colorScheme";
    static final String DELAY_FIELD = "delay";
    static final String EXPIRATION_FIELD = "expiration";
    static final String GUID_FIELD = "guid";
    static final String IDENTIFIER_FIELD = "identifier";
    static final String INTERVAL_FIELD = "interval";
    static final String LABELS_FIELD = "labels";
    static final String MESSAGES_FIELD = "messages";
    static final String NAME_FIELD = "name";
    static final String NOTIFICATIONS_FIELD = "notifications";
    static final String NOTIFY_AT_FIELD = "notifyAt";
    static final String OCCURRENCES_FIELD = "occurrences";
    static final String OFFSET_FIELD = "offset";
    static final String PERFORMANCE_ORDER_FIELD = "performanceOrder";
    static final String START_EVENT_IDS_FIELD = "startEventIds";
    static final String START_TIME_FIELD = "startTime";
    static final String STUDY_BURST_IDS_FIELD = "studyBurstIds";
    static final String SYMBOL_FIELD = "symbol";
    static final String TIME_WINDOWS_FIELD = "timeWindows";
    static final String TITLE_FIELD = "title";
    
    static final String DELAY_LONGER_THAN_SCHEDULE_DURATION_ERROR = "cannot be longer than the schedule duration";
    static final String EXPIRATION_LONGER_THAN_INTERVAL_ERROR = "cannot be longer than the session interval";
    static final String EXPIRATION_REQUIRED_FOR_INTERVAL_ERROR = "is required when a session has an interval";
    static final String EXPIRATION_REQUIRED_FOR_OCCURRENCES_ERROR = "is required when a session has more than one occurrence";
    static final String LONGER_THAN_WINDOW_EXPIRATION_ERROR = "cannot be longer than the shortest window expiration";
    static final String MUST_DEFINE_TRIGGER_ERROR = "must define one or more startEventIds or studyBurstIds";
    static final String REQUIRES_INTERVAL = "requires that an interval be set";
    static final String START_TIME_MILLIS_INVALID_ERROR = "cannot specify milliseconds";
    static final String START_TIME_SECONDS_INVALID_ERROR = "cannot specify seconds";
    static final String WINDOW_OVERLAPS_ERROR = "overlaps another time window";
    static final String WINDOW_SHORTER_THAN_DAY_ERROR = "cannot be set when the shortest window is less than a day";
    static final String WINDOW_EXPIRATION_AFTER_SCHEDULE_DURATION = "cannot expire after schedule duration";
    static final String LESS_THAN_ONE_ERROR = "cannot be less than one";
    static final String UNDEFINED_STUDY_BURST = "does not refer to a defined study burst ID";
    
    private final Period scheduleDuration;
    private final Set<String> studyBurstIds;
    
    public SessionValidator(Period scheduleDuration, Set<String> studyBurstIds) {
        this.scheduleDuration = scheduleDuration;
        this.studyBurstIds = studyBurstIds;
    }
    
    public static final Comparator<TimeWindow> START_TIME_COMPARATOR = (a, b) -> {
        if (a.getStartTime() == null) {
            return 1;
        } else if (b.getStartTime() == null) {
            return -1;
        }
        int res = a.getStartTime().getHourOfDay() - b.getStartTime().getHourOfDay();
        if (res == 0) {
            return a.getStartTime().getMinuteOfHour() - b.getStartTime().getMinuteOfHour();
        }
        return res;
    };
    
    public static final Comparator<TimeWindow> TIME_WINDOW_LENGTH_COMPARATOR = (a, b) -> {
        long aMin = localTimeInMinutes(a.getStartTime()) + periodInMinutes(a.getExpiration());
        long bMin = localTimeInMinutes(b.getStartTime()) + periodInMinutes(b.getExpiration());
        return (int)aMin - (int)bMin;
    };
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Session.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Session session = (Session)obj;
        
        if (isBlank(session.getGuid())) {
            errors.rejectValue(GUID_FIELD, CANNOT_BE_BLANK);
        }
        if (isBlank(session.getName())) {
            errors.rejectValue(NAME_FIELD, CANNOT_BE_BLANK);
        } else if (!session.getName().matches(BRIDGE_RELAXED_ID_PATTERN)) {
            errors.rejectValue(NAME_FIELD, BRIDGE_RELAXED_ID_ERROR);
        }
        validateStringLength(errors, 255, session.getName(), NAME_FIELD);
        if (session.getStartEventIds().isEmpty() && session.getStudyBurstIds().isEmpty()) {
            errors.rejectValue("", MUST_DEFINE_TRIGGER_ERROR);
        }
        validateStringLength(errors, 32, session.getSymbol(), SYMBOL_FIELD);
        if (session.getSymbol() != null && isBlank(session.getSymbol())) {
            errors.rejectValue(SYMBOL_FIELD, CANNOT_BE_BLANK);
        }
        // Not catching duplicates, here and in study bursts
        Set<String> uniqueStartEventIds = new HashSet<>();
        for (int i=0; i < session.getStartEventIds().size(); i++) {
            String eventId = session.getStartEventIds().get(i);
            errors.pushNestedPath(START_EVENT_IDS_FIELD + '[' + i + ']');
            
            if (uniqueStartEventIds.contains(eventId)) {
                errors.rejectValue("", CANNOT_BE_DUPLICATE);
            }
            uniqueStartEventIds.add(eventId);
            if (eventId == null) {
                errors.rejectValue("", INVALID_EVENT_ID);
            } else if (isBlank(eventId)) {
                errors.rejectValue("", CANNOT_BE_BLANK);
            }                
            errors.popNestedPath();
        }
        
        Set<String> uniqueStudyBurstIds = new HashSet<>();
        for (int i=0; i < session.getStudyBurstIds().size(); i++) {
            String burstId = session.getStudyBurstIds().get(i);
            errors.pushNestedPath(STUDY_BURST_IDS_FIELD + '[' + i + ']');
            
            if (uniqueStudyBurstIds.contains(burstId)) {
                errors.rejectValue("", CANNOT_BE_DUPLICATE);
            }
            uniqueStudyBurstIds.add(burstId);
            if (burstId == null) {
                errors.rejectValue("", CANNOT_BE_NULL);
            } else if (isBlank(burstId)) {
                errors.rejectValue("", CANNOT_BE_BLANK);
            }
            if (!studyBurstIds.contains(burstId)) {
                errors.rejectValue("", UNDEFINED_STUDY_BURST);
            }
            errors.popNestedPath();
        }
        if (lessThanOne(session.getOccurrences())) {
            errors.rejectValue(OCCURRENCES_FIELD, LESS_THAN_ONE_ERROR);
        }
        if (greaterThanOne(session.getOccurrences()) && session.getInterval() == null) {
            errors.rejectValue(OCCURRENCES_FIELD, REQUIRES_INTERVAL);
        }
        validateFixedLengthPeriod(errors, session.getDelay(), DELAY_FIELD, false);
        if (scheduleDuration != null && session.getDelay() != null) {
            if (periodInMinutes(session.getDelay()) > periodInMinutes(scheduleDuration)) {
                errors.rejectValue(DELAY_FIELD, DELAY_LONGER_THAN_SCHEDULE_DURATION_ERROR);
            }
        }
        validateFixedLengthLongPeriod(errors, session.getInterval(), INTERVAL_FIELD, false);
        if (session.getPerformanceOrder() == null) {
            errors.rejectValue(PERFORMANCE_ORDER_FIELD, CANNOT_BE_NULL);
        }
        if (!session.getLabels().isEmpty()) {
            validateLabels(errors, session.getLabels());
        }
        validateJsonLength(errors, TEXT_SIZE, session.getLabels(), LABELS_FIELD);
        if (session.getTimeWindows().isEmpty()) {
            errors.rejectValue(TIME_WINDOWS_FIELD, CANNOT_BE_NULL_OR_EMPTY);
        } else {
            for (int i=0; i < session.getTimeWindows().size(); i++) {
                TimeWindow window = session.getTimeWindows().get(i);
                errors.pushNestedPath(TIME_WINDOWS_FIELD+"["+i+"]");
                
                if (isBlank(window.getGuid())) {
                    errors.rejectValue(GUID_FIELD, CANNOT_BE_BLANK);
                }
                if (window.getStartTime() == null) {
                    errors.rejectValue(START_TIME_FIELD, CANNOT_BE_NULL);
                } else if (window.getStartTime().getSecondOfMinute() > 0) {
                    errors.rejectValue(START_TIME_FIELD, START_TIME_SECONDS_INVALID_ERROR);
                } else if (window.getStartTime().getMillisOfSecond() > 0) {
                    errors.rejectValue(START_TIME_FIELD, START_TIME_MILLIS_INVALID_ERROR);
                }
                validateFixedLengthPeriod(errors, window.getExpiration(), EXPIRATION_FIELD, false);
                if (session.getInterval() != null) {
                    if (window.getExpiration() == null) {
                        errors.rejectValue(EXPIRATION_FIELD, EXPIRATION_REQUIRED_FOR_INTERVAL_ERROR);
                    } else {
                        long intervalMin = periodInMinutes(session.getInterval());
                        long expMin = periodInMinutes(window.getExpiration());
                        if (expMin > intervalMin) {
                            errors.rejectValue(EXPIRATION_FIELD, EXPIRATION_LONGER_THAN_INTERVAL_ERROR);
                        }
                    }
                }
                if (greaterThanOne(session.getOccurrences()) && window.getExpiration() == null) {
                    errors.rejectValue(EXPIRATION_FIELD, EXPIRATION_REQUIRED_FOR_OCCURRENCES_ERROR);
                }
                if (window.getExpiration() != null) {
                    long windowExpiration = periodInMinutes(window.getExpiration());
                    if (session.getDelay() != null) {
                        windowExpiration += periodInMinutes(session.getDelay());
                    }
                    if (scheduleDuration != null) {
                        if (windowExpiration > periodInMinutes(scheduleDuration)) {
                            errors.rejectValue(EXPIRATION_FIELD, WINDOW_EXPIRATION_AFTER_SCHEDULE_DURATION);
                        }
                    }
                }
                errors.popNestedPath();
            }
            validateTimeWindowOverlaps(errors, session);
        }
        for (int i=0; i < session.getAssessments().size(); i++) {
            AssessmentReference asmt = session.getAssessments().get(i);
            
            errors.pushNestedPath(ASSESSMENTS_FIELD+"["+i+"]");
            if (isBlank(asmt.getGuid())) {
                errors.rejectValue(GUID_FIELD, CANNOT_BE_BLANK);
            }
            if (isBlank(asmt.getIdentifier())) {
                errors.rejectValue(IDENTIFIER_FIELD, CANNOT_BE_BLANK);
            }
            validateStringLength(errors, 255, asmt.getIdentifier(), IDENTIFIER_FIELD);
            if (isBlank(asmt.getAppId())) {
                errors.rejectValue(APP_ID_FIELD, CANNOT_BE_BLANK);
            }
            validateStringLength(errors, 255, asmt.getTitle(), TITLE_FIELD);
            validateLabels(errors, asmt.getLabels());
            validateJsonLength(errors, TEXT_SIZE, asmt.getLabels(), LABELS_FIELD);
            validateColorScheme(errors, asmt.getColorScheme(), COLOR_SCHEME_FIELD);
            if (asmt.getImageResource() != null) {
                errors.pushNestedPath("imageResource");
                Validate.entity(ImageResourceValidator.INSTANCE, errors, asmt.getImageResource());
                errors.popNestedPath();
            }
            errors.popNestedPath();
        }
        for (int i=0; i < session.getNotifications().size(); i++) {
            Notification notification = session.getNotifications().get(i);
            
            errors.pushNestedPath(NOTIFICATIONS_FIELD+"["+i+"]");
            
            if (notification.getNotifyAt() == null) {
                errors.rejectValue(NOTIFY_AT_FIELD, CANNOT_BE_NULL);
            }
            validateFixedLengthPeriod(errors, notification.getOffset(), OFFSET_FIELD, false);
            validateFixedLengthPeriod(errors, notification.getInterval(), INTERVAL_FIELD, false);
            if (!session.getTimeWindows().isEmpty()) {
                TimeWindow shortestWindow = shortestTimeWindow(session);
                if (shortestWindow.getExpiration() != null) {
                    long winExpMinutes = periodInMinutes(shortestWindow.getExpiration());
                    if (periodInMinutes(notification.getOffset()) > winExpMinutes) {
                        errors.rejectValue(OFFSET_FIELD, LONGER_THAN_WINDOW_EXPIRATION_ERROR);
                    }
                    if (notification.getInterval() != null) {
                        if (winExpMinutes < (24*60)) {
                            errors.rejectValue(INTERVAL_FIELD, WINDOW_SHORTER_THAN_DAY_ERROR);
                        } else if (periodInMinutes(notification.getInterval()) > winExpMinutes) {
                            errors.rejectValue(INTERVAL_FIELD, LONGER_THAN_WINDOW_EXPIRATION_ERROR);
                        }
                    }
                }
            }
            if (notification.getMessages() == null || notification.getMessages().isEmpty()) {
                errors.rejectValue(MESSAGES_FIELD, CANNOT_BE_NULL_OR_EMPTY);
            } else {
                validateMessages(errors, notification.getMessages());
                validateJsonLength(errors, TEXT_SIZE, notification.getMessages(), MESSAGES_FIELD);
            }
            errors.popNestedPath();
        }
    }
    
    private boolean lessThanOne(Integer number) {
        return (number != null && number < 1);
    }

    private boolean greaterThanOne(Integer number) {
        return (number != null && number > 1);
    }
    
    private TimeWindow shortestTimeWindow(Session session) {
        if (session.getTimeWindows().size() < 2) {
            return Iterables.getFirst(session.getTimeWindows(), null);
        }
        List<TimeWindow> windowsInOrder = Lists.newArrayList(session.getTimeWindows());
        windowsInOrder.sort(TIME_WINDOW_LENGTH_COMPARATOR);
        return windowsInOrder.get(0);
    }
    
    private void validateTimeWindowOverlaps(Errors errors, Session session) {
        // no windows to overlap, or session doesn't repeat
        if (session.getTimeWindows().size() < 2) { 
            return;
        }
        // windows are not required to be in time order, so sort them
        List<TimeWindow> windowsInOrder = Lists.newArrayList(session.getTimeWindows());
        windowsInOrder.sort(START_TIME_COMPARATOR);
        
        long minuteInDay = 0;
        for (TimeWindow window : windowsInOrder) {
            LocalTime startTime = window.getStartTime();
            
            long winStartMinute = localTimeInMinutes(startTime);
            if (winStartMinute < minuteInDay) {
                addOverlapError(errors, session, window);
            }
            minuteInDay = winStartMinute + periodInMinutes(window.getExpiration());
        }
        // If the session repeats, then the longest time window should not overlap
        // with the first window in that sequence
        if (session.getInterval() != null) {
            TimeWindow longestWindow = longestTimeWindow(session);
            
            long intervalInMinutes = periodInMinutes(session.getInterval());
            long windowInMinutes = localTimeInMinutes(longestWindow.getStartTime()) + 
                    periodInMinutes(longestWindow.getExpiration());
            if (windowInMinutes > intervalInMinutes) {
                addOverlapError(errors, session, longestWindow);
            }
        }
    }
    
    static long localTimeInMinutes(LocalTime time) {
        return (time == null) ? 0 : ((time.getHourOfDay() * 60) + time.getMinuteOfHour());
    }
    
    private void addOverlapError(Errors errors, Session session, TimeWindow window) { 
        int index = session.getTimeWindows().indexOf(window);
        
        errors.pushNestedPath(TIME_WINDOWS_FIELD+"["+index+"]");
        errors.rejectValue("", WINDOW_OVERLAPS_ERROR);
        errors.popNestedPath();
    }
    
    private TimeWindow longestTimeWindow(Session session) {
        List<TimeWindow> windowsInOrder = Lists.newArrayList(session.getTimeWindows());
        windowsInOrder.sort(TIME_WINDOW_LENGTH_COMPARATOR);
        return Iterables.getLast(windowsInOrder);
    }
}
