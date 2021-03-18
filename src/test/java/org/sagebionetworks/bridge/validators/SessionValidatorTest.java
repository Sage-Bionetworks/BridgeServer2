package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.schedules2.SessionTest.createValidSession;
import static org.sagebionetworks.bridge.validators.SessionValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.INVALID_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_LONG_PERIOD;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_PERIOD;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.schedules2.Session;

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
        assertValidatorMessage(INSTANCE, session, "name", CANNOT_BE_BLANK);
    }
    
    @Test
    public void nameNull() {
        Session session = createValidSession();
        session.setName(null);
        assertValidatorMessage(INSTANCE, session, "name", CANNOT_BE_BLANK);
    }
    
    @Test
    public void guidBlank() {
        Session session = createValidSession();
        session.setGuid("");
        assertValidatorMessage(INSTANCE, session, "guid", CANNOT_BE_BLANK);
    }
    
    @Test
    public void guidNull() {
        Session session = createValidSession();
        session.setGuid(null);
        assertValidatorMessage(INSTANCE, session, "guid", CANNOT_BE_BLANK);
    }

    @Test
    public void startEventIdBlank() {
        Session session = createValidSession();
        session.setStartEventId("");
        assertValidatorMessage(INSTANCE, session, "startEventId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void startEventIdNull() {
        Session session = createValidSession();
        session.setStartEventId(null);
        assertValidatorMessage(INSTANCE, session, "startEventId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void delayInvalidPeriod() { 
        Session session = createValidSession();
        session.setDelay(Period.parse("P3M"));
        assertValidatorMessage(INSTANCE, session, "delay", WRONG_PERIOD);
    }
    
    @Test
    public void intervalInvalidPeriod() { 
        Session session = createValidSession();
        session.setInterval(Period.parse("P3Y"));
        assertValidatorMessage(INSTANCE, session, "interval", WRONG_LONG_PERIOD);
    }
    
    
    @Test
    public void intervalInvalidShortPeriod() { 
        Session session = createValidSession();
        session.setInterval(Period.parse("PT3H"));
        assertValidatorMessage(INSTANCE, session, "interval", WRONG_LONG_PERIOD);
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
        assertValidatorMessage(INSTANCE, session, "performanceOrder", CANNOT_BE_NULL);
    }
    
    @Test
    public void timeWindowsNullOrEmpty() { 
        Session session = createValidSession();
        session.setTimeWindows(null);
        assertValidatorMessage(INSTANCE, session, "timeWindows", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void timeWindowGuidBlank() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setGuid("\t");
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].guid", CANNOT_BE_BLANK);
    }

    @Test
    public void timeWindowGuidNull() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setGuid(null);
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].guid", CANNOT_BE_BLANK);
    }

    @Test
    public void timeWindowStartTimeNull() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setStartTime(null);
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].startTime", CANNOT_BE_NULL);
    }
    
    @Test
    public void timeWindowExpirationPeriodInvalid( ) {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setExpiration(Period.parse("P3M"));
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].expiration", WRONG_PERIOD);
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
        
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].expiration",
                "is required when a session has an interval");
    }
    
    @Test
    public void timeWindowExpirationDurationTooLong() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setExpiration(Period.parse("P7DT1H"));
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].expiration",
                "cannot be longer in duration than the session’s interval");
    }
    
    @Test
    public void assessmentsNullOrEmpty() {
        Session session = createValidSession();
        session.setAssessments(null);
        assertValidatorMessage(INSTANCE, session, "assessments", CANNOT_BE_NULL_OR_EMPTY);
    }    

    @Test
    public void assessmentRefGuidNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setGuid(null);
        assertValidatorMessage(INSTANCE, session, "assessments[0].guid", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefGuidEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setGuid("");
        assertValidatorMessage(INSTANCE, session, "assessments[0].guid", CANNOT_BE_BLANK);
    }

    @Test
    public void assessmentRefAppIdNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAppId(null);
        assertValidatorMessage(INSTANCE, session, "assessments[0].appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefAssessmentAppIdEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAppId("\t");
        assertValidatorMessage(INSTANCE, session, "assessments[0].appId", CANNOT_BE_BLANK);
    }
    
    @Test
    public void remindAtSetButReminMinBeforeNotSet() {
        Session session = createValidSession();
        session.setReminderPeriod(null);
        assertValidatorMessage(INSTANCE, session, "reminderPeriod", "must be set if remindAt is set");
    }
    
    @Test
    public void remindMinBeforeSetButRemindAtNotSet() {
        Session session = createValidSession();
        session.setRemindAt(null);
        assertValidatorMessage(INSTANCE, session, "remindAt", "must be set if reminderPeriod is set");
    }
    
    @Test
    public void reminderPeriodAndRemindAtNullOK() {
        // No reminder (second notification) should be shown. This is valid
        Session session = createValidSession();
        session.setReminderPeriod(null);
        session.setRemindAt(null);
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void reminderPeriodNegative() {
        Session session = createValidSession();
        session.setReminderPeriod(Period.parse("PT-10M"));
        assertValidatorMessage(INSTANCE, session, "reminderPeriod", CANNOT_BE_NEGATIVE);
    }

    @Test
    public void allowSnoozeCannotBeTrueWhenNotificationsDisabled() {
        Session session = createValidSession();
        session.setNotifyAt(null);
        assertValidatorMessage(INSTANCE, session, "allowSnooze", "cannot be true if notifications are disabled");
    }
    
    @Test
    public void messagesNullOrEmpty() {
        Session session = createValidSession();
        session.setMessages(null);
        assertValidatorMessage(INSTANCE, session, "messages", CANNOT_BE_NULL_OR_EMPTY);
    }
    
    @Test
    public void messagesNullOrEmptyOKIfNoNotifications() {
        Session session = createValidSession();
        session.setNotifyAt(null);
        session.setRemindAt(null);
        session.setAllowSnooze(false);
        session.setReminderPeriod(null);
        session.setMessages(null);
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void messagesMustContainEnglishDefault() {
        Session session = createValidSession();
        session.setMessages(ImmutableList.of(
            new NotificationMessage.Builder().withLang("fr").build(),
            new NotificationMessage.Builder().withLang("de").build()
        ));
        
        assertValidatorMessage(INSTANCE, session, "messages", "must include an English-language message as a default");
    }
    
    @Test
    public void messageLanguageBlank() {
        Session session = createValidSession();
        session.setMessages(updateLanguage(session.getMessages(), ""));
        assertValidatorMessage(INSTANCE, session, "messages[0].lang", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageLanguageNull() {
        Session session = createValidSession();
        session.setMessages(updateLanguage(session.getMessages(), null));
        assertValidatorMessage(INSTANCE, session, "messages[0].lang", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageLanguageCodeDuplicated() throws Exception {
        Session session = createValidSession();
        session.setMessages(ImmutableList.of(session.getMessages().get(0), session.getMessages().get(0)));
        
        assertValidatorMessage(INSTANCE, session, "messages[1].lang", DUPLICATE_LANG);
    }
    
    @Test
    public void messsageLanguageCodeInvalid() {
        NotificationMessage message = new NotificationMessage.Builder()
                .withLang("yyy").withSubject("Subject").withMessage("Body").build();

        Session session = createValidSession();
        session.setMessages(ImmutableList.of(session.getMessages().get(0), message));
        assertValidatorMessage(INSTANCE, session, "messages[1].lang", INVALID_LANG);
    }
    
    @Test
    public void messageSubjectBlank() {
        Session session = createValidSession();
        session.setMessages(updateSubject(session.getMessages(), "\t\n"));
        assertValidatorMessage(INSTANCE, session, "messages[0].subject", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageSubjectNull() {
        Session session = createValidSession();
        session.setMessages(updateSubject(session.getMessages(), null));
        assertValidatorMessage(INSTANCE, session, "messages[0].subject", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageSubjectTooLong() {
        Session session = createValidSession();
        session.setMessages(updateSubject(session.getMessages(), StringUtils.repeat("X", 100)));
        assertValidatorMessage(INSTANCE, session, "messages[0].subject", "must be 40 characters or less");
    }
    
    @Test
    public void messageBlank() {
        Session session = createValidSession();
        session.setMessages(updateMessage(session.getMessages(), "\t\n"));
        assertValidatorMessage(INSTANCE, session, "messages[0].message", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageNull() {
        Session session = createValidSession();
        session.setMessages(updateMessage(session.getMessages(), null));
        assertValidatorMessage(INSTANCE, session, "messages[0].message", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageTooLong() {
        Session session = createValidSession();
        session.setMessages(updateMessage(session.getMessages(), StringUtils.repeat("X", 100)));
        assertValidatorMessage(INSTANCE, session, "messages[0].message", "must be 60 characters or less");
    }
    
    private List<NotificationMessage> updateLanguage(List<NotificationMessage> messages, String lang) {
        NotificationMessage msg = messages.get(0);
        return ImmutableList.of(new NotificationMessage.Builder().withLang(lang).withSubject(msg.getSubject())
                .withMessage(msg.getMessage()).build(), messages.get(1));
    }
    
    private List<NotificationMessage> updateSubject(List<NotificationMessage> messages, String subject) {
        NotificationMessage msg = messages.get(0);
        return ImmutableList.of(new NotificationMessage.Builder().withLang(msg.getLang()).withSubject(subject)
                .withMessage(msg.getMessage()).build(), messages.get(1));
    }
    
    private List<NotificationMessage> updateMessage(List<NotificationMessage> messages, String message) {
        NotificationMessage msg = messages.get(0);
        return ImmutableList.of(new NotificationMessage.Builder().withLang(msg.getLang()).withSubject(msg.getSubject())
                .withMessage(message).build(), messages.get(1));
    }
}
