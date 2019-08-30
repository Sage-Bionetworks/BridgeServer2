package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;

public class ScheduleContextValidatorTest {

    private ScheduleContextValidator validator = new ScheduleContextValidator();

    @Test
    public void validContext() {
        // The minimum you need to have a valid schedule context.
        ScheduleContext context = new ScheduleContext.Builder()
            .withEndsOn(DateTime.now().plusDays(2))
            .withInitialTimeZone(DateTimeZone.forOffsetHours(-3))
            .withAccountCreatedOn(DateTime.now())
            .withHealthCode("AAA")
            .build();
        
        Validate.nonEntityThrowingException(validator, context);
    }
    
    @Test
    public void requiredFields() {
        ScheduleContext context = new ScheduleContext.Builder().build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("offset must set a time zone offset"));
            assertTrue(e.getMessage().contains("healthCode is required"));
            assertTrue(e.getMessage().contains("endsOn is required"));
            assertTrue(e.getMessage().contains("accountCreatedOn is required"));
        }
    }

    @Test
    public void endsOnAfterNow() {
        ScheduleContext context = new ScheduleContext.Builder()
            .withInitialTimeZone(DateTimeZone.UTC)
            .withEndsOn(DateTime.now().minusHours(1)).withHealthCode("healthCode").build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("endsOn must be after startsOn"));
        }
    }
    
    @Test
    public void endsOnBeforeMaxNumDays() {
        // Setting this two days past the maximum. Will always fail.
        DateTime endsOn = DateTime.now().plusDays(ScheduleContextValidator.MAX_DATE_RANGE_IN_DAYS+2);
        ScheduleContext context = new ScheduleContext.Builder()
            .withInitialTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn).withHealthCode("healthCode").build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("endsOn must be less than 32 days"));
        }
    }
    
    @Test
    public void minimumActivitiesAreGreaterThanZero() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withMinimumPerSchedule(-1).build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("minimumPerSchedule cannot be negative"));
        }
    }
    
    @Test
    public void minimumActivitiesAreNotGreaterThanMax() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withMinimumPerSchedule(ScheduleContextValidator.MAX_MIN_ACTIVITY_COUNT + 1).build();
        try {
            Validate.nonEntityThrowingException(validator, context);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertTrue(e.getMessage().contains("minimumPerSchedule cannot be greater than 5"));
        }
    }
}
