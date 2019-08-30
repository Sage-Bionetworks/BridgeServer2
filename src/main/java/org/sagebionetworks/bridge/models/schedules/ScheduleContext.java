package org.sagebionetworks.bridge.models.schedules;

import java.util.Map;
import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.collect.ImmutableMap;

/**
 * All the information necessary to convert a schedule into a set of activities, on a given request. 
 * Because some of these values derive from the user, there is a validator that is run on this object 
 * that verifies the four required values (studyId, initialTimeZone, endsOn and healthCode) are present.
 * 
 * @see org.sagebionetworks.bridge.validators.ScheduleContextValidator
 */
public final class ScheduleContext {
    
    private final DateTimeZone initialTimeZone;
    private final DateTime startsOn;
    private final DateTime endsOn;
    private final Map<String,DateTime> events;
    private final DateTime accountCreatedOn;
    private final int minimumPerSchedule;
    private final String healthCode;
    
    private ScheduleContext(DateTimeZone initialTimeZone, DateTime startsOn, DateTime endsOn,
            Map<String, DateTime> events, int minimumPerSchedule, DateTime accountCreatedOn, String healthCode) {
        this.initialTimeZone = initialTimeZone;
        this.endsOn = endsOn;
        this.events = events;
        this.startsOn = startsOn;
        this.minimumPerSchedule = minimumPerSchedule;
        this.accountCreatedOn = accountCreatedOn;
        this.healthCode = healthCode;
    }
    
    /**
     * The initial time zone of the user, the first time that user contacts the server for activities. This allows the 
     * scheduler to interpret events against the user's initial time zone.
     * @return
     */
    public DateTimeZone getInitialTimeZone() {
        return initialTimeZone;
    }
    
    /**
     * The current request is asking for activities up to a given end date.
     * @return
     */
    public DateTime getEndsOn() {
        return endsOn;
    }
    
    /**
     * Are there any events recorded for this participant? This should always return true since every 
     * participant should have an enrollment event, if nothing else.
     * @return
     */
    public boolean hasEvents() {
        return (events != null && !events.isEmpty());
    }
    
    /**
     * Get an event timestamp for a given event ID.
     * @param eventId
     * @return
     */
    public DateTime getEvent(String eventId) {
        return (events != null) ? events.get(eventId) : null;
    }
    
    /**
     * Start time of scheduling, in the user's initial time zone. This will be used to calculate 
     * the times of recurring activities.
     */
    public DateTime getStartsOn() {
        return startsOn;
    }
    
    public int getMinimumPerSchedule() {
        return minimumPerSchedule;
    }
    
    public DateTime getAccountCreatedOn() {
        return accountCreatedOn;
    }

    public String getHealthCode() {
        return healthCode;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(initialTimeZone, endsOn, events, startsOn, accountCreatedOn, minimumPerSchedule,
                healthCode);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleContext other = (ScheduleContext) obj;
        return (Objects.equals(endsOn, other.endsOn) && Objects.equals(initialTimeZone, other.initialTimeZone) &&
                Objects.equals(events, other.events) && Objects.equals(startsOn, other.startsOn) &&
                Objects.equals(minimumPerSchedule, other.minimumPerSchedule) &&
                Objects.equals(accountCreatedOn, other.accountCreatedOn) && 
                Objects.equals(healthCode, other.healthCode));
    }

    @Override
    public String toString() {
        return "ScheduleContext [initialTimeZone=" + initialTimeZone + ", endsOn=" + endsOn + ", events=" + events + ", startsOn=" + startsOn
                + ", accountCreatedOn=" + accountCreatedOn + ", minimumPerSchedule=" + minimumPerSchedule 
                + ", healthCode=[REDACTED]]";
    }


    public static class Builder {
        private DateTimeZone initialTimeZone;
        private DateTime startsOn;
        private DateTime endsOn;
        private Map<String,DateTime> events;
        private int minimumPerSchedule;
        private DateTime accountCreatedOn;
        private String healthCode;
        
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withMinimumPerSchedule(int minimumPerSchedule) {
            this.minimumPerSchedule = minimumPerSchedule;
            return this;
        }
        public Builder withInitialTimeZone(DateTimeZone initialTimeZone) {
            this.initialTimeZone = initialTimeZone;
            return this;
        }
        public Builder withEndsOn(DateTime endsOn) {
            this.endsOn = endsOn;
            return this;
        }
        public Builder withEvents(Map<String,DateTime> events) {
            if (events != null) {
                this.events = ImmutableMap.copyOf(events);    
            }
            return this;
        }
        public Builder withStartsOn(DateTime startsOn) {
            this.startsOn = startsOn;
            return this;
        }
        public Builder withAccountCreatedOn(DateTime accountCreatedOn) {
            if (accountCreatedOn != null) {
                this.accountCreatedOn = new DateTime(accountCreatedOn, DateTimeZone.UTC);    
            }
            return this;
        }
        public Builder withContext(ScheduleContext context) {
            withInitialTimeZone(context.initialTimeZone);
            withEndsOn(context.endsOn);
            withEvents(context.events);
            withStartsOn(context.startsOn);
            withMinimumPerSchedule(context.minimumPerSchedule);
            withAccountCreatedOn(context.accountCreatedOn);
            withHealthCode(context.healthCode);
            return this;
        }
        
        public ScheduleContext build() {
            // pretty much everything else is optional. I would like healthCode to be required, but it's not:
            // we use these selection criteria to select subpopulations on sign up.
            if (startsOn == null) {
                startsOn = (initialTimeZone == null) ? DateTime.now() : DateTime.now(initialTimeZone);
            }
            return new ScheduleContext(initialTimeZone, startsOn, endsOn, events, minimumPerSchedule, accountCreatedOn,
                    healthCode);
        }
    }
}
