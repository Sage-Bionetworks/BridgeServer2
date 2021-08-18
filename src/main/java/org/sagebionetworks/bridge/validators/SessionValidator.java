package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.periodInMinutes;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateColorScheme;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLengthPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateLabels;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateMessages;

import java.util.Comparator;
import java.util.List;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.joda.time.LocalTime;
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
    static final String MESSAGES_FIELD = "messages";
    static final String NAME_FIELD = "name";
    static final String NOTIFICATIONS_FIELD = "notifications";
    static final String NOTIFY_AT_FIELD = "notifyAt";
    static final String OCCURRENCES_FIELD = "occurrences";
    static final String OFFSET_FIELD = "offset";
    static final String PERFORMANCE_ORDER_FIELD = "performanceOrder";
    static final String START_EVENT_IDS_FIELD = "startEventIds";
    static final String START_TIME_FIELD = "startTime";
    static final String TIME_WINDOWS_FIELD = "timeWindows";
    
    static final String EXPIRATION_LONGER_THAN_INTERVAL_ERROR = "cannot be longer than the session interval";
    static final String EXPIRATION_REQUIRED_ERROR = "is required when a session has an interval";
    static final String LONGER_THAN_WINDOW_EXPIRATION_ERROR = "cannot be longer than the shortest window expiration";
    static final String START_TIME_MILLIS_INVALID_ERROR = "cannot specify milliseconds";
    static final String START_TIME_SECONDS_INVALID_ERROR = "cannot specify seconds";
    static final String WINDOW_OVERLAPS_ERROR = "overlaps another time window";
    static final String WINDOW_SHORTER_THAN_DAY_ERROR = "cannot be set when the shortest window is less than a day";
    static final String LESS_THAN_ONE_ERROR = "cannot be less than one";
    
    public static final SessionValidator INSTANCE = new SessionValidator();
    
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
        }
        if (session.getStartEventIds().isEmpty()) {
            errors.rejectValue(START_EVENT_IDS_FIELD, CANNOT_BE_EMPTY);
        } else {
            for (int i=0; i < session.getStartEventIds().size(); i++) {
                String oneEventId = session.getStartEventIds().get(i);
                errors.pushNestedPath(START_EVENT_IDS_FIELD + '[' + i + ']');
                if (oneEventId == null) {
                    errors.rejectValue("", CANNOT_BE_NULL);
                } else if (isBlank(oneEventId)) {
                    errors.rejectValue("", CANNOT_BE_BLANK);
                }                
                errors.popNestedPath();
            }
        }
        if (session.getOccurrences() != null && session.getOccurrences() < 1) {
            errors.rejectValue(OCCURRENCES_FIELD, LESS_THAN_ONE_ERROR);
        }
        validateFixedLengthPeriod(errors, session.getDelay(), DELAY_FIELD, false);
        validateFixedLengthLongPeriod(errors, session.getInterval(), INTERVAL_FIELD, false);
        if (session.getPerformanceOrder() == null) {
            errors.rejectValue(PERFORMANCE_ORDER_FIELD, CANNOT_BE_NULL);
        }
        if (!session.getLabels().isEmpty()) {
            validateLabels(errors, session.getLabels());
        }
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
                        errors.rejectValue(EXPIRATION_FIELD, EXPIRATION_REQUIRED_ERROR);
                    } else {
                        long intervalMin = periodInMinutes(session.getInterval());
                        long expMin = periodInMinutes(window.getExpiration());
                        if (expMin > intervalMin) {
                            errors.rejectValue(EXPIRATION_FIELD, EXPIRATION_LONGER_THAN_INTERVAL_ERROR);
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
            if (isBlank(asmt.getAppId())) {
                errors.rejectValue(APP_ID_FIELD, CANNOT_BE_BLANK);
            }
            validateLabels(errors, asmt.getLabels());
            validateColorScheme(errors, asmt.getColorScheme(), COLOR_SCHEME_FIELD);
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
            }
            errors.popNestedPath();
        }
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
