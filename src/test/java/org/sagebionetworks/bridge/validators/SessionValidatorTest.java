package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.schedules2.NotificationType.AT_START_OF_WINDOW;
import static org.sagebionetworks.bridge.models.schedules2.ReminderType.BEFORE_WINDOW_END;
import static org.sagebionetworks.bridge.validators.SessionValidator.INSTANCE;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Message;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;

public class SessionValidatorTest extends Mockito {

    public static Session createValidSession() {
        Session session = new Session();
        session.setName("Do weekly survey");
        session.setGuid(GUID);
        session.setStartEventId("eventId1");
        session.setDelay(Period.parse("P1W"));
        session.setOccurrences(19);
        session.setInterval(Period.parse("P7D"));
        session.setBundled(true);
        session.setRandomized(true);
        session.setNotifyAt(AT_START_OF_WINDOW);
        session.setRemindAt(BEFORE_WINDOW_END);
        session.setRemindMinBefore(10);
        session.setAllowSnooze(true);
        
        TimeWindow window = new TimeWindow();
        window.setGuid(GUID);
        window.setStartTime(LocalTime.parse("08:00"));
        window.setExpiration(Period.parse("P6D"));
        window.setPersistent(true);
        session.setTimeWindows(ImmutableList.of(window));
        
        AssessmentReference asmt1 = new AssessmentReference();
        asmt1.setGuid("asmtRef1Guid");
        asmt1.setAssessmentGuid("asmt1Guid");
        asmt1.setAssessmentAppId("local");
        
        AssessmentReference asmt2 = new AssessmentReference();
        asmt2.setGuid("asmtRef2Guid");
        asmt2.setAssessmentGuid("asmt2Guid");
        asmt2.setAssessmentAppId("shared");
        session.setAssessments(ImmutableList.of(asmt1, asmt2));
        
        Message message = new Message();
        message.setLanguage("en");
        message.setSubject("Subject");
        message.setBody("Subject");
        session.setMessages(ImmutableList.of(message));
        
        return session;
    }
    
    @Test
    public void valid() {
        Session session = createValidSession();
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void validationSkipsNotificationInfoIfDisabled() {
        Session session = createValidSession();
        session.setNotifyAt(null);
        session.setRemindAt(null); // will not throw
        Validate.entityThrowingException(INSTANCE, session);
    }
    
    @Test
    public void blankName() {
        Session session = createValidSession();
        session.setName("");
        assertValidatorMessage(INSTANCE, session, "name", "cannot be null or blank");
    }
    
    @Test
    public void nullName() {
        Session session = createValidSession();
        session.setName(null);
        assertValidatorMessage(INSTANCE, session, "name", "cannot be null or blank");
    }
    
    @Test
    public void blankGuid() {
        Session session = createValidSession();
        session.setGuid("");
        assertValidatorMessage(INSTANCE, session, "guid", "cannot be null or blank");
    }
    
    @Test
    public void nullGuid() {
        Session session = createValidSession();
        session.setGuid(null);
        assertValidatorMessage(INSTANCE, session, "guid", "cannot be null or blank");
    }

    @Test
    public void blankStartEventId() {
        Session session = createValidSession();
        session.setStartEventId("");
        assertValidatorMessage(INSTANCE, session, "startEventId", "cannot be null or blank");
    }
    
    @Test
    public void nullStartEventId() {
        Session session = createValidSession();
        session.setStartEventId(null);
        assertValidatorMessage(INSTANCE, session, "startEventId", "cannot be null or blank");
    }
    
    @Test
    public void nullOrEmptyTimeWindows() { 
        Session session = createValidSession();
        session.setTimeWindows(null);
        assertValidatorMessage(INSTANCE, session, "timeWindows", "cannot be null or empty");
    }
    
    @Test
    public void timeWindowGuidBlank() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setGuid("\t");
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].guid", "cannot be null or blank");
    }

    @Test
    public void timeWindowGuidNull() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setGuid(null);
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].guid", "cannot be null or blank");
    }

    @Test
    public void timeWindowStartTimeNull() {
        Session session = createValidSession();
        session.getTimeWindows().get(0).setStartTime(null);
        assertValidatorMessage(INSTANCE, session, "timeWindows[0].startTime", "cannot be null");
    }
    
    @Test
    public void assessmentsNullOrEmpty() {
        Session session = createValidSession();
        session.setAssessments(null);
        assertValidatorMessage(INSTANCE, session, "assessments", "cannot be null or empty");
    }    

    @Test
    public void assessmentRefGuidNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setGuid(null);
        assertValidatorMessage(INSTANCE, session, "assessments[0].guid", "cannot be null or blank");
    }
    
    @Test
    public void assessmentRefGuidEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setGuid("");
        assertValidatorMessage(INSTANCE, session, "assessments[0].guid", "cannot be null or blank");
    }

    @Test
    public void assessmentRefAssessmentGuidNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAssessmentGuid(null);
        assertValidatorMessage(INSTANCE, session, "assessments[0].assessment.guid", "cannot be null or blank");
    }
    
    @Test
    public void assessmentRefAssessmentGuidEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAssessmentGuid("");
        assertValidatorMessage(INSTANCE, session, "assessments[0].assessment.guid", "cannot be null or blank");
    }

    @Test
    public void assessmentRefAssessmentAppIdNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAssessmentAppId(null);
        assertValidatorMessage(INSTANCE, session, "assessments[0].assessment.appId", "cannot be null or blank");
    }
    
    @Test
    public void assessmentRefAssessmentAppIdEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setAssessmentAppId("\t");
        assertValidatorMessage(INSTANCE, session, "assessments[0].assessment.appId", "cannot be null or blank");
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
        assertValidatorMessage(INSTANCE, session, "remindMinBefore", "cannot be negative");
    }
    
    @Test
    public void messagesNullOrEmpty() {
        Session session = createValidSession();
        session.setMessages(null);
        assertValidatorMessage(INSTANCE, session, "messages", "cannot be null or empty");
    }
    
    @Test
    public void messageLanguageBlank() {
        Session session = createValidSession();
        session.getMessages().get(0).setLanguage("");
        assertValidatorMessage(INSTANCE, session, "messages[0].language", "cannot be null or blank");
    }
    
    @Test
    public void messageLanguageNull() {
        Session session = createValidSession();
        session.getMessages().get(0).setLanguage(null);
        assertValidatorMessage(INSTANCE, session, "messages[0].language", "cannot be null or blank");
    }
    
    @Test
    public void messageLanguageCodeDuplicated() {
        Session session = createValidSession();
        session.setMessages(ImmutableList.of(session.getMessages().get(0), session.getMessages().get(0)));
        assertValidatorMessage(INSTANCE, session, 
                "messages[1].language", "is a duplicate message under the same language code");
    }
    
    @Test
    public void messsageLanguageCodeInvalid() {
        Message message = new Message();
        message.setLanguage("yyy");
        message.setSubject("Subject");
        message.setBody("Subject");

        Session session = createValidSession();
        session.setMessages(ImmutableList.of(session.getMessages().get(0), message));
        assertValidatorMessage(INSTANCE, session, "messages[1].language", "is not a valid ISO 639 alpha-2 or alpha-3 language code");
    }
    
    @Test
    public void messagesMissingEnglishDefault() {
        Session session = createValidSession();
        session.getMessages().get(0).setLanguage("fr");
        assertValidatorMessage(INSTANCE, session, "messages", "must contain an English message as a default");
    }
    
    @Test
    public void messageSubjectBlank() {
        Session session = createValidSession();
        session.getMessages().get(0).setSubject("\t\n");
        assertValidatorMessage(INSTANCE, session, "messages[0].subject", "cannot be null or blank");
    }
    
    @Test
    public void messageSubjectNull() {
        Session session = createValidSession();
        session.getMessages().get(0).setSubject(null);
        assertValidatorMessage(INSTANCE, session, "messages[0].subject", "cannot be null or blank");
    }
    
    @Test
    public void messageSubjectTooLong() {
        Session session = createValidSession();
        session.getMessages().get(0).setSubject(StringUtils.repeat("X", 100));
        assertValidatorMessage(INSTANCE, session, "messages[0].subject", "must be 40 characters or less");
    }
    
    @Test
    public void messageBodyBlank() {
        Session session = createValidSession();
        session.getMessages().get(0).setBody("\t\n");
        assertValidatorMessage(INSTANCE, session, "messages[0].body", "cannot be null or blank");
    }
    
    @Test
    public void messageBodyNull() {
        Session session = createValidSession();
        session.getMessages().get(0).setBody(null);
        assertValidatorMessage(INSTANCE, session, "messages[0].body", "cannot be null or blank");
    }
    
    @Test
    public void messageBodyTooLong() {
        Session session = createValidSession();
        session.getMessages().get(0).setBody(StringUtils.repeat("X", 100));
        assertValidatorMessage(INSTANCE, session, "messages[0].body", "must be 60 characters or less");
    }
}
