package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.schedules2.SessionTest.createValidSession;
import static org.sagebionetworks.bridge.validators.SessionValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_LANG;
import static org.sagebionetworks.bridge.validators.Validate.WRONG_PERIOD;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules2.Label;
import org.sagebionetworks.bridge.models.schedules2.Message;
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
        assertValidatorMessage(INSTANCE, session, "interval", WRONG_PERIOD);
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
    public void labelsLabelBlank() {
        Session session = createValidSession();
        session.setLabels(ImmutableList.of(new Label("en", "")));
        assertValidatorMessage(INSTANCE, session, "labels[0].label", CANNOT_BE_BLANK);
    }
    
    @Test
    public void labelsLabelNull() {
        Session session = createValidSession();
        session.setLabels(ImmutableList.of(new Label("en", null)));
        assertValidatorMessage(INSTANCE, session, "labels[0].label", CANNOT_BE_BLANK);
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
    public void timeWindowExpirationEmptyIsValid( ) {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setExpiration(null);
        Validate.entityThrowingException(INSTANCE, session);
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
        session.setRemindMinBefore(null);
        assertValidatorMessage(INSTANCE, session, "remindMinBefore", "must be set if remindAt is set");
    }
    
    @Test
    public void remindMinBeforeSetButRemindAtNotSet() {
        Session session = createValidSession();
        session.setRemindAt(null);
        assertValidatorMessage(INSTANCE, session, "remindAt", "must be set if remindMinBefore is set");
    }
    
    @Test
    public void remindMinBeforeAndRemindAtNullOK() {
        // No reminder (second notifiaction) should be shown. This is valid
        Session session = createValidSession();
        session.setRemindMinBefore(null);
        session.setRemindAt(null);
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void remindMinBeforeNegative() {
        Session session = createValidSession();
        session.setRemindMinBefore(-10);
        assertValidatorMessage(INSTANCE, session, "remindMinBefore", CANNOT_BE_NEGATIVE);
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
        Validate.entityThrowingException(INSTANCE, session);
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
        Message message = new Message("yyy", "Subject", "Body");

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
    public void messageBodyBlank() {
        Session session = createValidSession();
        session.setMessages(updateBody(session.getMessages(), "\t\n"));
        assertValidatorMessage(INSTANCE, session, "messages[0].body", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageBodyNull() {
        Session session = createValidSession();
        session.setMessages(updateBody(session.getMessages(), null));
        assertValidatorMessage(INSTANCE, session, "messages[0].body", CANNOT_BE_BLANK);
    }
    
    @Test
    public void messageBodyTooLong() {
        Session session = createValidSession();
        session.setMessages(updateBody(session.getMessages(), StringUtils.repeat("X", 100)));
        assertValidatorMessage(INSTANCE, session, "messages[0].body", "must be 60 characters or less");
    }
    
    private List<Message> updateLanguage(List<Message> messages, String lang) {
        Message msg = messages.get(0);
        return ImmutableList.of(new Message(lang, msg.getSubject(), msg.getBody()), messages.get(1));
    }
    
    private List<Message> updateSubject(List<Message> messages, String subject) {
        Message msg = messages.get(0);
        return ImmutableList.of(new Message(msg.getLang(), subject, msg.getBody()), messages.get(1));
    }
    
    private List<Message> updateBody(List<Message> messages, String body) {
        Message msg = messages.get(0);
        return ImmutableList.of(new Message(msg.getLang(), msg.getSubject(), body), messages.get(1));
    }
}
