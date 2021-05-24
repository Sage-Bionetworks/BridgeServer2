package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_3;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.schedules2.SessionTest.createValidSession;
import static org.sagebionetworks.bridge.validators.SessionValidator.ASSESSMENTS_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.DELAY_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.EXPIRATION_LONGER_THAN_INTERVAL_ERROR;
import static org.sagebionetworks.bridge.validators.SessionValidator.EXPIRATION_REQUIRED_ERROR;
import static org.sagebionetworks.bridge.validators.SessionValidator.GUID_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.SessionValidator.INTERVAL_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.LESS_THAN_ONE_ERROR;
import static org.sagebionetworks.bridge.validators.SessionValidator.LONGER_THAN_WINDOW_EXPIRATION_ERROR;
import static org.sagebionetworks.bridge.validators.SessionValidator.NAME_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.NOTIFICATIONS_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.OCCURRENCES_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.PERFORMANCE_ORDER_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.START_EVENT_ID_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.TIME_WINDOWS_FIELD;
import static org.sagebionetworks.bridge.validators.SessionValidator.START_TIME_COMPARATOR;
import static org.sagebionetworks.bridge.validators.SessionValidator.WINDOW_OVERLAPS_ERROR;
import static org.sagebionetworks.bridge.validators.SessionValidator.WINDOW_SHORTER_THAN_DAY_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_LONG_PERIOD;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_PERIOD;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class SessionValidatorTest extends Mockito {
    
    @Test
    public void valid() {
        Session session = createValidSession();
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void nameBlank() {
        Session session = createValidSession();
        session.setName("");
        assertValidatorMessage(INSTANCE, session, NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void nameNull() {
        Session session = createValidSession();
        session.setName(null);
        assertValidatorMessage(INSTANCE, session, NAME_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void guidBlank() {
        Session session = createValidSession();
        session.setGuid("");
        assertValidatorMessage(INSTANCE, session, GUID_FIELD, CANNOT_BE_BLANK);
    }
    
    @Test
    public void guidNull() {
        Session session = createValidSession();
        session.setGuid(null);
        assertValidatorMessage(INSTANCE, session, GUID_FIELD, CANNOT_BE_BLANK);
    }

    @Test
    public void startEventIdBlank() {
        Session session = createValidSession();
        session.setStartEventId("");
        assertValidatorMessage(INSTANCE, session, START_EVENT_ID_FIELD, INVALID_EVENT_ID);
    }
    
    @Test
    public void startEventIdNull() {
        Session session = createValidSession();
        session.setStartEventId(null);
        assertValidatorMessage(INSTANCE, session, START_EVENT_ID_FIELD, INVALID_EVENT_ID);
    }
    
    @Test
    public void delayInvalidPeriod() { 
        Session session = createValidSession();
        session.setDelay(Period.parse("P3M"));
        assertValidatorMessage(INSTANCE, session, DELAY_FIELD, WRONG_PERIOD);
    }
    
    @Test
    public void intervalInvalidPeriod() { 
        Session session = createValidSession();
        session.setInterval(Period.parse("P3Y"));
        assertValidatorMessage(INSTANCE, session, INTERVAL_FIELD, WRONG_LONG_PERIOD);
    }
    
    
    @Test
    public void intervalInvalidShortPeriod() { 
        Session session = createValidSession();
        session.setInterval(Period.parse("PT3H"));
        assertValidatorMessage(INSTANCE, session, INTERVAL_FIELD, WRONG_LONG_PERIOD);
    }
    
    @Test
    public void occurrencesLessThanOne() {
        Session session = createValidSession();
        session.setOccurrences(Integer.valueOf("0"));
        assertValidatorMessage(INSTANCE, session, OCCURRENCES_FIELD, LESS_THAN_ONE_ERROR);
    }

    @Test
    public void occurrencesNegative() {
        Session session = createValidSession();
        session.setOccurrences(Integer.valueOf("-2"));
        assertValidatorMessage(INSTANCE, session, OCCURRENCES_FIELD, LESS_THAN_ONE_ERROR);
    }
    
    @Test
    public void occurrencesNullOk() {
        Session session = createValidSession();
        session.setOccurrences(null);
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void labelsEmptyIsValid() {
        Session session = createValidSession();
        session.setLabels(ImmutableList.of());
        Validate.entityThrowingException(INSTANCE, session);
    }

    @Test
    public void labelsInvalid() {
        Session session = createValidSession();
        session.setLabels(ImmutableList.of(new Label("en", "foo"), new Label("en", "bar")));
        assertValidatorMessage(INSTANCE, session, "labels[1].lang", DUPLICATE_LANG);
    }
    
    @Test
    public void labelsValueBlank() {
        Session session = createValidSession();
        session.setLabels(ImmutableList.of(new Label("en", "")));
        assertValidatorMessage(INSTANCE, session, "labels[0].value", CANNOT_BE_BLANK);
    }
    
    @Test
    public void labelsValueNull() {
        Session session = createValidSession();
        session.setLabels(ImmutableList.of(new Label("en", null)));
        assertValidatorMessage(INSTANCE, session, "labels[0].value", CANNOT_BE_BLANK);
    }
    
    @Test
    public void performanceOrderNull() {
        Session session = createValidSession();
        session.setPerformanceOrder(null);
        assertValidatorMessage(INSTANCE, session, PERFORMANCE_ORDER_FIELD, CANNOT_BE_NULL);
    }
    
    @Test
    public void timeWindowsNullOrEmpty() { 
        Session session = createValidSession();
        session.setTimeWindows(null);
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void timeWindowGuidBlank() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setGuid("\t");
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].guid", CANNOT_BE_BLANK);
    }

    @Test
    public void timeWindowGuidNull() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setGuid(null);
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].guid", CANNOT_BE_BLANK);
    }

    @Test
    public void timeWindowStartTimeNull() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setStartTime(null);
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].startTime", CANNOT_BE_NULL);
    }
    
    @Test
    public void timeWindowStartTimeSpecifiesSeconds() { 
        Session session = createValidSession();
        session.getTimeWindows().get(0).setStartTime(LocalTime.parse("10:00:03"));
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].startTime", "cannot specify seconds");
    }
    
    @Test
    public void timeWindowStartTimeSpecifiesMilliseconds() { 
        Session session = createValidSession();
        session.getTimeWindows().get(0).setStartTime(LocalTime.parse("10:00:00.123"));
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].startTime", "cannot specify milliseconds");
        
    }
    
    @Test
    public void timeWindowExpirationPeriodInvalid( ) {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setExpiration(Period.parse("P3M"));
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].expiration", WRONG_PERIOD);
    }
    
    @Test
    public void timeWindowExpirationEmptyIsValidForNotRepeatingSchedule() {
        Session session = createValidSession();
        session.setInterval(null);
        session.getTimeWindows().get(0).setExpiration(null);
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void timeWindowExpirationEmptyInvalidForRepeatingSchedule() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setExpiration(null);
        
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].expiration",
                EXPIRATION_REQUIRED_ERROR);
    }
    
    @Test
    public void timeWindowExpirationDurationTooLong() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setExpiration(Period.parse("P7DT1H"));
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[0].expiration",
                EXPIRATION_LONGER_THAN_INTERVAL_ERROR);
    }
    
    @Test
    public void timeWindowsDoNotOverlap() {
        Session session = makeWindows("08:00", "PT6H", "14:00", "PT6H", "20:00", "PT6H");
        
        Validate.entityThrowingException(INSTANCE, session);
    }

    @Test
    public void timeWindowsOverlapOutOfOrder() {
        Session session = makeWindows("08:00", "PT6H", "14:00", "PT6H", "10:00", "PT6H");
        
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[2]", WINDOW_OVERLAPS_ERROR);
    }
    
    @Test
    public void timeWindowsOverlapInOrder() {
        Session session = makeWindows("08:00", "PT6H15M", "14:14", "PT6H15M", "20:30", "PT6H");
        
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[1]", WINDOW_OVERLAPS_ERROR);
    }
    
    @Test
    public void timeWindowsDoesNotOverlapOverDays() {
        Session session = makeWindows("08:00", "PT6H", "14:00", "PT6H", "20:00", "P6D");
        
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void timeWindowsOverlapOverDaysAndOneMinute() {
        Session session = makeWindows("08:00", "PT6H", "14:00", "PT6H", "20:00", "P6DT20H");
        
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[2]", WINDOW_OVERLAPS_ERROR);
    }
    
    @Test
    public void timeWindowsOverlapOverDays() {
        Session session = makeWindows("08:00", "PT4H", "14:00", "PT4H", "20:30", "P7D");
        
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[2]", WINDOW_OVERLAPS_ERROR);
    }
    
    @Test
    public void timeWindowsOverlapOverDaysOrderDoesNotMatter() {
        Session session = makeWindows("08:00", "PT4H", "20:30", "P7D", "14:00", "PT4H");
        
        assertValidatorMessage(INSTANCE, session, TIME_WINDOWS_FIELD+"[1]", WINDOW_OVERLAPS_ERROR);
    }
    
    @Test
    public void timeWindowOverlapOkWhenNoSessionInterval() { 
        // Because this session never repeats, there is no "overlap" here.
        Session session = makeWindows("08:00", "PT4H", "12:30", "PT4H", "20:00", "P18D");
        session.setInterval(null);
        
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void assessmentsNullOrEmpty() {
        Session session = createValidSession();
        session.setAssessments(null);
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD, CANNOT_BE_NULL_OR_EMPTY);
    }    

    @Test
    public void assessmentRefGuidNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setGuid(null);
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD+"[0].guid", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefGuidEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setGuid("");
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD+"[0].guid", CANNOT_BE_BLANK);
    }

    @Test
    public void assessmentRefIdentifierNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setIdentifier(null);
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD+"[0].identifier", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefIdnetifierEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setIdentifier("");
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD+"[0].identifier", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefAppIdNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAppId(null);
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD+"[0].appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefAssessmentAppIdEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAppId("\t");
        assertValidatorMessage(INSTANCE, session, ASSESSMENTS_FIELD+"[0].appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void comparatorWorks() {
        Session session = makeWindows("08:13", "PT1M", "08:02", "PT1M", "09:01", "P7D");
        
        List<TimeWindow> windows = Lists.newArrayList(session.getTimeWindows());
        windows.sort(START_TIME_COMPARATOR);
        
        assertEquals(windows.get(0).getStartTime(), LocalTime.parse("08:02"));
        assertEquals(windows.get(1).getStartTime(), LocalTime.parse("08:13"));
        assertEquals(windows.get(2).getStartTime(), LocalTime.parse("09:01"));
    }
    
    @Test
    public void comparatorHandlesNulls() {
        Session session = makeWindows("08:13", "PT1M", "08:02", "PT1M", "09:01", "P7D");
        session.getTimeWindows().get(1).setStartTime(null);
        
        List<TimeWindow> windows = Lists.newArrayList(session.getTimeWindows());
        windows.sort(START_TIME_COMPARATOR);
        
        assertEquals(windows.get(0).getStartTime(), LocalTime.parse("08:13"));
        assertEquals(windows.get(1).getStartTime(), LocalTime.parse("09:01"));
        assertNull(windows.get(2).getStartTime());
    }
    
    @Test
    public void notificationNotifyAtNull() {
        Session session = createValidSession();
        session.getNotifications().get(0).setNotifyAt(null);
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].notifyAt", CANNOT_BE_NULL);
    }
    
    @Test
    public void notificationOffsetNull() {
        Session session = createValidSession();
        session.getNotifications().get(0).setOffset(null);
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].offset", CANNOT_BE_NULL);
    }
    
    @Test
    public void notificationOffsetLongerThanSessionWindow() {
        Session session = makeWindows("08:00", "PT2H", "14:00", "PT6H", "20:00", "PT6H");
        session.getNotifications().get(0).setOffset(Period.parse("PT2H1M"));
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].offset", 
                LONGER_THAN_WINDOW_EXPIRATION_ERROR);
    }

    @Test
    public void notificationIntervalLongerThanWindowExpiration() {
        Session session = SessionTest.createValidSession();
        session.getTimeWindows().get(0).setStartTime(LocalTime.parse("20:00"));
        session.getTimeWindows().get(0).setExpiration(Period.parse("P1D"));
        session.getNotifications().get(0).setInterval(Period.parse("P1DT1M"));
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].interval", 
                LONGER_THAN_WINDOW_EXPIRATION_ERROR);
    }

    @Test
    public void notificationIntervalOnWindowsShorterThanDay() {
        Session session = makeWindows("08:00", "PT6H", "14:00", "PT6H", "20:00", "PT6H");
        session.getNotifications().get(0).setOffset(Period.parse("PT2H"));
        session.getNotifications().get(0).setInterval(Period.parse("P1D"));
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].interval", 
                WINDOW_SHORTER_THAN_DAY_ERROR);
    }
    
    @Test
    public void notificationWithInterval() {
        // Time window of 6 days starting at 8am, so a 2 day interval is fine
        Session session = SessionTest.createValidSession();
        session.getTimeWindows().get(0).setExpiration(Period.parse("P6D"));
        // notify first @ 10am on day 2
        session.getNotifications().get(0).setOffset(Period.parse("PT26H"));
        // then 10am on day 4 and day 6
        session.getNotifications().get(0).setInterval(Period.parse("P2D"));
        
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void notificationMessagesNull() {
        Session session = SessionTest.createValidSession();
        session.getNotifications().get(0).setMessages(null);
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].messages", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void notificationMessagesEmpty() {
        Session session = SessionTest.createValidSession();
        session.getNotifications().get(0).setMessages(ImmutableList.of());
        
        assertValidatorMessage(INSTANCE, session, NOTIFICATIONS_FIELD+"[0].messages", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void localTimeNullReturnsZero() {
        assertEquals(SessionValidator.localTimeInMinutes(null), 0L);
    }
    
    @Test
    public void localTimeNullReturnsWorks() {
        assertEquals(SessionValidator.localTimeInMinutes(LocalTime.parse("00:23")), 23L);
        assertEquals(SessionValidator.localTimeInMinutes(LocalTime.parse("13:23")), (13L*60L) + 23L);
        assertEquals(SessionValidator.localTimeInMinutes(LocalTime.parse("13:00")), 13L*60L);
    }
    
    private Session makeWindows(String time1, String exp1, String time2, String exp2, 
            String time3, String exp3) {
        Session session = createValidSession();
        TimeWindow win1 = new TimeWindow();
        win1.setGuid(SESSION_GUID_1);
        win1.setStartTime(LocalTime.parse(time1));
        win1.setExpiration(Period.parse(exp1));

        TimeWindow win2 = new TimeWindow();
        win2.setGuid(SESSION_GUID_2);
        win2.setStartTime(LocalTime.parse(time2));
        win2.setExpiration(Period.parse(exp2));

        TimeWindow win3 = new TimeWindow();
        win3.setGuid(SESSION_GUID_3);
        win3.setStartTime(LocalTime.parse(time3));
        win3.setExpiration(Period.parse(exp3));
        
        session.setTimeWindows(ImmutableList.of(win1, win2, win3));
        return session;
    }
}
