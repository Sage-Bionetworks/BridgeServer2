package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.backfill.BackfillStatus;

public class DynamoBackfillTaskTest {

    @Test
    public void test() {
        final long timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        DynamoBackfillTask task = new DynamoBackfillTask("name", "user");
        assertEquals(task.getName(), "name");
        assertEquals(task.getUser(), "user");
        assertTrue(task.getTimestamp() >= timestamp);
        assertEquals(task.getStatus(), BackfillStatus.SUBMITTED.name());
    }

    @Test
    public void testId() {
        final long timestamp = DateTime.now(DateTimeZone.UTC).getMillis();
        DynamoBackfillTask task = new DynamoBackfillTask("name", "user");
        String id = task.getId();
        assertNotNull(id);
        String[] splits =id.split(":");
        assertEquals(splits.length, 2);
        assertEquals(splits[0], "name");
        assertTrue(Long.parseLong(splits[1]) >= timestamp);
        task = new DynamoBackfillTask(id);
        assertEquals(task.getName(), "name");
        assertNull(task.getUser());
        assertTrue(task.getTimestamp() >= timestamp);
        assertNull(task.getStatus());
    }
}
