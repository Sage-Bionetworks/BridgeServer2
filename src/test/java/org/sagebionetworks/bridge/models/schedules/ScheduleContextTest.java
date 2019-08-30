package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

public class ScheduleContextTest {

    private static final ScheduleContext EMPTY_CONTEXT = new ScheduleContext.Builder().build();
    private static final String ENROLLMENT = "enrollment";
    private static final String HEALTH_CODE = "healthCode";

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(ScheduleContext.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void quietlyReturnsFalseForEvents() {
        assertNull(EMPTY_CONTEXT.getEvent(ENROLLMENT));
        assertFalse(EMPTY_CONTEXT.hasEvents());
        
        ScheduleContext context = new ScheduleContext.Builder()
                .withEvents(new HashMap<String, DateTime>()).build();
        assertNull(context.getEvent(ENROLLMENT));
        assertFalse(context.hasEvents());
    }
    
    @Test
    public void defaultsTimeZoneMinimumAndClientInfo() {
        assertNotNull(EMPTY_CONTEXT.getStartsOn());
        assertEquals(EMPTY_CONTEXT.getMinimumPerSchedule(), 0);
    }
    
    @Test
    public void verifyBuilder() {
        DateTimeZone PST = DateTimeZone.forOffsetHours(-7);
        DateTime startsOn = DateTime.now().minusHours(2);
        DateTime endsOn = DateTime.now();
        DateTime accountCreatedOn = DateTime.now(DateTimeZone.UTC).minusDays(2);
        
        Map<String,DateTime> events = new HashMap<>();
        events.put(ENROLLMENT, DateTime.now());
        
        // All the individual fields work
        ScheduleContext context = new ScheduleContext.Builder()
                .withInitialTimeZone(PST)
                .withStartsOn(startsOn)
                .withEndsOn(endsOn)
                .withMinimumPerSchedule(3)
                .withEvents(events)
                .withHealthCode(HEALTH_CODE)
                .withAccountCreatedOn(accountCreatedOn).build();
        
        assertEquals(context.getInitialTimeZone(), PST);
        assertEquals(context.getEndsOn(), endsOn);
        assertEquals(context.getEvent(ENROLLMENT), events.get(ENROLLMENT));
        assertEquals(context.getMinimumPerSchedule(), 3);
        assertEquals(context.getHealthCode(), HEALTH_CODE);
        assertEquals(context.getStartsOn(), startsOn);
        assertEquals(context.getAccountCreatedOn(), accountCreatedOn);

        // Test the withContext() method.
        ScheduleContext copy = new ScheduleContext.Builder().withContext(context).build();
        assertEquals(copy, context);
    }
    
    @Test
    public void verifyNulls() {
        assertNull(EMPTY_CONTEXT.getInitialTimeZone());
        assertNull(EMPTY_CONTEXT.getEndsOn());
        assertFalse(EMPTY_CONTEXT.hasEvents());
        assertNull(EMPTY_CONTEXT.getEvent(ENROLLMENT));
        assertNull(EMPTY_CONTEXT.getEndsOn());
        assertEquals(EMPTY_CONTEXT.getMinimumPerSchedule(), 0);
        assertNull(EMPTY_CONTEXT.getAccountCreatedOn());
        assertNull(EMPTY_CONTEXT.getHealthCode());
        // And then there's this, which is not null
        assertNotNull(EMPTY_CONTEXT.getStartsOn());
        
        // Test the withContext() method.
        ScheduleContext copy = new ScheduleContext.Builder().withContext(EMPTY_CONTEXT).build();
        assertEquals(copy, EMPTY_CONTEXT);
    }
    
    @Test
    public void verifyAccountCreatedCopy() {
        // Null is safe and works
        ScheduleContext context = new ScheduleContext.Builder().build();
        ScheduleContext copy = new ScheduleContext.Builder().withContext(context).build();
        assertNull(copy.getAccountCreatedOn());
        
        // Non-null is properly copied
        DateTime now = DateTime.now(DateTimeZone.UTC);
        context = new ScheduleContext.Builder().withAccountCreatedOn(now).build();
        copy = new ScheduleContext.Builder().withContext(context).build();
        assertEquals(copy.getAccountCreatedOn(), now);
    }
    
    @Test
    public void eventTimesAreForcedToUTC() {
        ScheduleContext context = new ScheduleContext.Builder()
                .withAccountCreatedOn(DateTime.parse("2010-10-10T10:10:10.010+03:00"))
                .build();
        assertEquals(context.getAccountCreatedOn().toString(), "2010-10-10T07:10:10.010Z");
        
        ScheduleContext context2 = new ScheduleContext.Builder().withContext(context).build();
        assertEquals(context2.getAccountCreatedOn().toString(), "2010-10-10T07:10:10.010Z");
    }
    
}
