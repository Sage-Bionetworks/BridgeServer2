package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

public class ActivityTypeTest {

    @Test
    public void fromPlural() {
        assertEquals(ActivityType.fromPlural("tasks"), ActivityType.TASK);
        assertEquals(ActivityType.fromPlural("surveys"), ActivityType.SURVEY);
        assertEquals(ActivityType.fromPlural("compoundactivities"), ActivityType.COMPOUND);
        assertNull(ActivityType.fromPlural("somenonsense"));
        assertNull(ActivityType.fromPlural(""));
        assertNull(ActivityType.fromPlural(null));
    }
}
