package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.schedules2.SessionTest.createValidSession;
import static org.sagebionetworks.bridge.validators.SessionValidator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL_OR_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EVENT_ID;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_LONG_PERIOD;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_PERIOD;

import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.Label;
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
        assertValidatorMessage(INSTANCE, session, "startEventId", INVALID_EVENT_ID);
    }
    
    @Test
    public void startEventIdNull() {
        Session session = createValidSession();
        session.setStartEventId(null);
        assertValidatorMessage(INSTANCE, session, "startEventId", INVALID_EVENT_ID);
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
    public void assessmentRefIdentifierNull() {
        Session session = createValidSession();
        session.getAssessments().get(0).setIdentifier(null);
        assertValidatorMessage(INSTANCE, session, "assessments[0].identifier", CANNOT_BE_BLANK);
    }
    
    @Test
    public void assessmentRefIdnetifierEmpty() {
        Session session = createValidSession();
        session.getAssessments().get(0).setIdentifier("");
        assertValidatorMessage(INSTANCE, session, "assessments[0].identifier", CANNOT_BE_BLANK);
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
}
