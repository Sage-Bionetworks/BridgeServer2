package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.validators.Schedule2Validator.INSTANCE;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;

public class Schedule2ValidatorTest extends Mockito {

    Schedule2 createSchedule() {
        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setOwnerId(TEST_ORG_ID);
        schedule.setName("NAME");
        schedule.setGuid(GUID);
        schedule.setDuration(Period.parse("P3Y"));
        schedule.setDurationStartEventId("activities_retrieved");
        schedule.setCreatedOn(CREATED_ON);
        schedule.setModifiedOn(MODIFIED_ON);
        schedule.setDeleted(true);
        schedule.setVersion(10L);
        return schedule;
    }
    
    @Test
    public void passes() {
        Schedule2 schedule = createSchedule();
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*name cannot be null or blank.*")
    public void nameBlank() {
        Schedule2 schedule = createSchedule();
        schedule.setName(" ");
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*name cannot be null or blank.*")
    public void nameNull() {
        Schedule2 schedule = createSchedule();
        schedule.setName(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*ownerId is not a valid organization ID.*")
    public void ownerIdBlank() {
        Schedule2 schedule = createSchedule();
        schedule.setOwnerId(" ");
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*ownerId is not a valid organization ID.*")
    public void ownerIdNull() {
        Schedule2 schedule = createSchedule();
        schedule.setOwnerId(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*appId cannot be null or blank.*")
    public void appIdBlank() {
        Schedule2 schedule = createSchedule();
        schedule.setAppId(" ");
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*appId cannot be null or blank.*")
    public void appIdNull() {
        Schedule2 schedule = createSchedule();
        schedule.setAppId(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*guid cannot be null or blank.*")
    public void guidBlank() {
        Schedule2 schedule = createSchedule();
        schedule.setGuid(" ");
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*guid cannot be null or blank.*")
    public void guidNull() {
        Schedule2 schedule = createSchedule();
        schedule.setGuid(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*duration cannot be null.*")
    public void durationNull() {
        Schedule2 schedule = createSchedule();
        schedule.setDuration(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*durationStartEventId is not a valid event ID.*")
    public void durationStartEventIdBlank() {
        Schedule2 schedule = createSchedule();
        schedule.setDurationStartEventId(" ");
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*durationStartEventId is not a valid event ID.*")
    public void durationStartEventIdNull() {
        Schedule2 schedule = createSchedule();
        schedule.setDurationStartEventId(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*createdOn cannot be null.*")
    public void createdOnNull() {
        Schedule2 schedule = createSchedule();
        schedule.setCreatedOn(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }

    @Test(expectedExceptions = InvalidEntityException.class,
            expectedExceptionsMessageRegExp = ".*modifiedOn cannot be null.*")
    public void modifiedOnNull() {
        Schedule2 schedule = createSchedule();
        schedule.setModifiedOn(null);
        Validate.entityThrowingException(INSTANCE, schedule);
    }
}
