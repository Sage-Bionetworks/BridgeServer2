package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.schedules2.Schedule2Test.createValidSchedule;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.CANNOT_BE_GREATER_THAN_20;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.CANNOT_BE_LONGER_THAN_FIVE_YEARS;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.CANNOT_BE_LONGER_THAN_SCHEDULE;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.INSTANCE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_DUPLICATE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_ZERO_OR_NEGATIVE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.WRONG_LONG_PERIOD;

import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.SessionTest;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;

public class Schedule2ValidatorTest extends Mockito {

    @Test
    public void passes() {
        Schedule2 schedule = createValidSchedule();
        Validate.entityThrowingException(INSTANCE, schedule);
    }

    @Test
    public void nameBlank() {
        Schedule2 schedule = createValidSchedule();
        schedule.setName(" ");
        assertValidatorMessage(INSTANCE, schedule, "name", CANNOT_BE_BLANK);
    }

    @Test
    public void nameNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setName(null);
        assertValidatorMessage(INSTANCE, schedule, "name", CANNOT_BE_BLANK);
    }

    @Test
    public void ownerIdBlank() {
        Schedule2 schedule = createValidSchedule();
        schedule.setOwnerId(" ");
        assertValidatorMessage(INSTANCE, schedule, "ownerId", CANNOT_BE_BLANK);
    }

    @Test
    public void ownerIdNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setOwnerId(null);
        assertValidatorMessage(INSTANCE, schedule, "ownerId", CANNOT_BE_BLANK);
    }

    @Test
    public void appIdBlank() {
        Schedule2 schedule = createValidSchedule();
        schedule.setAppId(" ");
        assertValidatorMessage(INSTANCE, schedule, "appId", CANNOT_BE_BLANK);
    }

    @Test(expectedExceptions = InvalidEntityException.class, expectedExceptionsMessageRegExp = ".*appId cannot be null or blank.*")
    public void appIdNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setAppId(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }

    @Test
    public void guidBlank() {
        Schedule2 schedule = createValidSchedule();
        schedule.setGuid(" ");
        assertValidatorMessage(INSTANCE, schedule, "guid", CANNOT_BE_BLANK);
    }

    @Test
    public void guidNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setGuid(null);
        assertValidatorMessage(INSTANCE, schedule, "guid", CANNOT_BE_BLANK);
    }

    @Test
    public void durationNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setDuration(null);
        assertValidatorMessage(INSTANCE, schedule, "duration", CANNOT_BE_NULL);
    }

    @Test
    public void durationInvalidValue() {
        Schedule2 schedule = createValidSchedule();
        schedule.setDuration(Period.parse("P3Y"));
        assertValidatorMessage(INSTANCE, schedule, "duration", WRONG_LONG_PERIOD);
    }

    @Test
    public void durationTooLong() {
        Schedule2 schedule = createValidSchedule();
        schedule.setDuration(Period.parse("P260W6D"));
        assertValidatorMessage(INSTANCE, schedule, "duration", CANNOT_BE_LONGER_THAN_FIVE_YEARS);
    }

    @Test
    public void durationInvalidShortValue() {
        Schedule2 schedule = createValidSchedule();
        schedule.setDuration(Period.parse("PT30M"));
        assertValidatorMessage(INSTANCE, schedule, "duration", WRONG_LONG_PERIOD);
    }

    @Test
    public void createdOnNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setCreatedOn(null);
        assertValidatorMessage(INSTANCE, schedule, "createdOn", CANNOT_BE_NULL);
    }

    @Test
    public void modifiedOnNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.setModifiedOn(null);
        assertValidatorMessage(INSTANCE, schedule, "modifiedOn", CANNOT_BE_NULL);
    }

    @Test
    public void validatesSessions() {
        Schedule2 schedule = createValidSchedule();
        Session session1 = spy(SessionTest.createValidSession());
        session1.setName("Session 1");
        Session session2 = spy(SessionTest.createValidSession());
        session2.setName("Session 2");
        schedule.setSessions(ImmutableList.of(session1, session2));

        Validate.entityThrowingException(INSTANCE, schedule);

        // Actual tests of session validation occur in SessionValidatorTest.
        verify(session1).getName();
        verify(session2).getName();
    }

    @Test
    public void sessionDelayCannotBeLongerThanScheduleDuration() {
        Schedule2 schedule = createValidSchedule();
        schedule.getSessions().get(0).setDelay(Period.parse("P8WT2M"));
        assertValidatorMessage(INSTANCE, schedule, "sessions[0].delay", CANNOT_BE_LONGER_THAN_SCHEDULE);
    }

    @Test
    public void sessionIntervalCannotBeLongerThanScheduleDuration() {
        Schedule2 schedule = createValidSchedule();
        schedule.getSessions().get(0).setInterval(Period.parse("P9W"));
        assertValidatorMessage(INSTANCE, schedule, "sessions[0].interval", CANNOT_BE_LONGER_THAN_SCHEDULE);
    }
    
    @Test
    public void studyBurstIdentifierCannotBeNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setIdentifier(null);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].identifier", CANNOT_BE_BLANK);
    }

    @Test
    public void studyBurstIdentifierCannotBeBlank() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setIdentifier("");
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].identifier", CANNOT_BE_BLANK);
    }

    @Test
    public void studyBurstOriginEventIdCannotBeNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setOriginEventId(null);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].originEventId", CANNOT_BE_BLANK);
    }

    @Test
    public void studyBurstOriginEventIdCannotBeBlank() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setOriginEventId("");
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].originEventId", CANNOT_BE_BLANK);
    }

    @Test
    public void studyBurstIntervalCannotBeNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setInterval(null);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].interval", CANNOT_BE_NULL);
    }

    @Test
    public void studyBurstOccurrencesCannotBeNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setOccurrences(null);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].occurrences", CANNOT_BE_NULL);
    }

    @Test
    public void studyBurstOccurrencesCannotBeZero() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setOccurrences(0);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].occurrences", CANNOT_BE_ZERO_OR_NEGATIVE);
    }

    @Test
    public void studyBurstOccurrencesCannotBeNegative() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setOccurrences(-2);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].occurrences", CANNOT_BE_ZERO_OR_NEGATIVE);
    }

    @Test
    public void studyBurstOccurrencesCannotBeTooBig() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setOccurrences(30);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].occurrences", CANNOT_BE_GREATER_THAN_20);
    }

    @Test
    public void studyBurstUpdateTypeCannotBeNull() {
        Schedule2 schedule = createValidSchedule();
        schedule.getStudyBursts().get(0).setUpdateType(null);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[0].updateType", CANNOT_BE_NULL);
    }

    @Test
    public void studyBurstsCannotDuplicateIds() {
        Schedule2 schedule = createValidSchedule();
        
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("burst1");
        burst.setOriginEventId(ENROLLMENT.name().toLowerCase());
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(2);
        burst.setUpdateType(FUTURE_ONLY);
        schedule.setStudyBursts(ImmutableList.of( schedule.getStudyBursts().get(0), burst ));
        
        schedule.getStudyBursts().get(0).setUpdateType(null);
        assertValidatorMessage(INSTANCE, schedule, "studyBursts[1].identifier", CANNOT_BE_DUPLICATE);
    }
}
