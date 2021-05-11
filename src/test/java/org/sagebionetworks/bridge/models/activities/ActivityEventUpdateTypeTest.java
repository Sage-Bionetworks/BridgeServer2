package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITIES_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;

public class ActivityEventUpdateTypeTest extends Mockito {
    
    private static final ActivityEvent EVENT = new DynamoActivityEvent.Builder()
            .withTimestamp(DateTime.parse("2021-04-09T00:00:00Z"))
            .withObjectType(ACTIVITIES_RETRIEVED).build();

    private static final ActivityEvent PAST_EVENT = new DynamoActivityEvent.Builder()
            .withTimestamp(DateTime.parse("2021-04-08T00:00:00Z"))
            .withObjectType(ACTIVITIES_RETRIEVED).build();
    
    private static final ActivityEvent FUTURE_EVENT = new DynamoActivityEvent.Builder()
            .withTimestamp(DateTime.parse("2021-04-10T00:00:00Z"))
            .withObjectType(ACTIVITIES_RETRIEVED).build();
    
    @Test
    public void youCanNeverDeleteWhenEventDoesNotExist() {
        assertFalse(MUTABLE.canDelete(null, EVENT));
        assertFalse(IMMUTABLE.canDelete(null, EVENT));
        assertFalse(FUTURE_ONLY.canDelete(null, EVENT));
    }
    
    @Test
    public void youCanAlwaysUpdateWhenEventDoesNotExist() {
        assertTrue(MUTABLE.canUpdate(null, EVENT));
        assertTrue(IMMUTABLE.canUpdate(null, EVENT));
        assertTrue(FUTURE_ONLY.canUpdate(null, EVENT));
    }
    
    @Test
    public void mutableCanDelete() {
        assertTrue(MUTABLE.canDelete(EVENT, PAST_EVENT));
        assertTrue(MUTABLE.canDelete(EVENT, EVENT));
        assertTrue(MUTABLE.canDelete(EVENT, FUTURE_EVENT));
    }
    
    @Test
    public void immutableCannotDelete() { 
        assertFalse(IMMUTABLE.canDelete(EVENT, PAST_EVENT));
        assertFalse(IMMUTABLE.canDelete(EVENT, EVENT));
        assertFalse(IMMUTABLE.canDelete(EVENT, FUTURE_EVENT));
    }
    
    @Test
    public void futureOnlyCannotDelete() {
        assertFalse(FUTURE_ONLY.canDelete(EVENT, PAST_EVENT));
        assertFalse(FUTURE_ONLY.canDelete(EVENT, EVENT));
        assertFalse(FUTURE_ONLY.canDelete(EVENT, FUTURE_EVENT));
    }

    @Test
    public void mutableCanUpdate() {
        assertTrue(MUTABLE.canUpdate(EVENT, PAST_EVENT));
        assertFalse(MUTABLE.canUpdate(EVENT, EVENT));
        assertTrue(MUTABLE.canUpdate(EVENT, FUTURE_EVENT));
    }
    
    @Test
    public void immutableCannotUpdate() { 
        assertFalse(IMMUTABLE.canUpdate(EVENT, PAST_EVENT));
        assertFalse(IMMUTABLE.canUpdate(EVENT, EVENT));
        assertFalse(IMMUTABLE.canUpdate(EVENT, FUTURE_EVENT));
    }
    
    @Test
    public void futureOnlyCanOnlyUpdateInFuture() {
        assertFalse(FUTURE_ONLY.canUpdate(EVENT, PAST_EVENT));
        assertFalse(FUTURE_ONLY.canUpdate(EVENT, EVENT));
        assertTrue(FUTURE_ONLY.canUpdate(EVENT, FUTURE_EVENT));
    }
}
