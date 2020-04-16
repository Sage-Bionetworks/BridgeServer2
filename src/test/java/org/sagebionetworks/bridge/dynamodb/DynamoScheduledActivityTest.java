package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DynamoScheduledActivityTest {

    @Test
    public void equalsHashCode() throws Exception {
        EqualsVerifier.forClass(DynamoScheduledActivity.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
                .withPrefabValues(JsonNode.class, TestUtils.getClientData(), TestUtils.getOtherClientData()).verify();
    }
    
    @Test
    public void testComparator() {
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setTimeZone(DateTimeZone.UTC);
        activity1.setLocalScheduledOn(LocalDateTime.parse("2010-10-10T01:01:01.000"));
        activity1.setActivity(TestUtils.getActivity3());
        
        // Definitely later
        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setTimeZone(DateTimeZone.UTC);
        activity2.setLocalScheduledOn(LocalDateTime.parse("2011-10-10T01:01:01.000"));
        activity2.setActivity(TestUtils.getActivity3());
        
        // The same as 2 in all respects but activity label comes earlier in alphabet
        DynamoScheduledActivity activity3 = new DynamoScheduledActivity();
        activity3.setTimeZone(DateTimeZone.UTC);
        activity3.setLocalScheduledOn(LocalDateTime.parse("2011-10-10T01:01:01.000"));
        activity3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2, activity3);
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        
        assertEquals(activities.get(0), activity1);
        assertEquals(activities.get(1), activity3);
        assertEquals(activities.get(2), activity2);
    }
    
    @Test
    public void handlesNullFieldsReasonably() {
        // No time zone
        DynamoScheduledActivity activity1 = new DynamoScheduledActivity();
        activity1.setLocalScheduledOn(LocalDateTime.parse("2010-10-10T01:01:01.000"));
        activity1.setActivity(TestUtils.getActivity3());
        
        // scheduledOn
        DynamoScheduledActivity activity2 = new DynamoScheduledActivity();
        activity2.setTimeZone(DateTimeZone.UTC);
        activity2.setActivity(TestUtils.getActivity3());
        
        // This one is okay
        DynamoScheduledActivity activity3 = new DynamoScheduledActivity();
        activity3.setTimeZone(DateTimeZone.UTC);
        activity3.setLocalScheduledOn(LocalDateTime.parse("2011-10-10T01:01:01.000"));
        activity3.setActivity(new Activity.Builder().withLabel("A Label").withTask("tapTest").build());
        
        List<ScheduledActivity> activities = Lists.newArrayList(activity1, activity2, activity3);
        Collections.sort(activities, ScheduledActivity.SCHEDULED_ACTIVITY_COMPARATOR);
        
        // Activity 3 comes first because it's complete, the others follow. This is arbitrary...
        // in reality they are broken activities, but the comparator will not fail.
        assertEquals(activities.get(0), activity3);
        assertEquals(activities.get(1), activity1);
        assertEquals(activities.get(2), activity2);
    }

    @Test
    public void canRoundtripSerialize() throws Exception {
        LocalDateTime scheduledOn = LocalDateTime.now().plusWeeks(1);
        LocalDateTime expiresOn = LocalDateTime.now().plusWeeks(1);
        
        String scheduledOnString = scheduledOn.toDateTime(DateTimeZone.UTC).toString();
        String expiresOnString = expiresOn.toDateTime(DateTimeZone.UTC).toString();
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setActivity(TestUtils.getActivity3());
        schActivity.setLocalScheduledOn(scheduledOn);
        schActivity.setLocalExpiresOn(expiresOn);
        schActivity.setGuid("AAA-BBB-CCC");
        schActivity.setHealthCode("FFF-GGG-HHH");
        schActivity.setPersistent(true);
        schActivity.setReferentGuid("referentGuid");
        schActivity.setClientData(TestUtils.getClientData());
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String output = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(schActivity);
        
        JsonNode node = mapper.readTree(output);
        assertEquals(node.get("guid").asText(), "AAA-BBB-CCC");
        assertEquals(node.get("scheduledOn").asText(), scheduledOnString);
        assertEquals(node.get("expiresOn").asText(), expiresOnString);
        assertEquals(node.get("status").asText(), "available");
        assertEquals(node.get("type").asText(), "ScheduledActivity");
        assertTrue(node.get("persistent").asBoolean());
        assertNull(node.get("schedule"));
        assertNull(node.get("referentGuid"));
        assertEquals(node.size(), 8);
        assertEquals(node.get("clientData"), TestUtils.getClientData());
        
        JsonNode activityNode = node.get("activity");
        assertEquals(activityNode.get("label").asText(), "Activity3");
        assertEquals(activityNode.get("task").get("identifier").asText(), "tapTest");
        assertEquals(activityNode.get("activityType").asText(), "task");
        assertEquals(activityNode.get("type").asText(), "Activity");
        
        // zero out the health code field, because that will not be serialized
        schActivity.setHealthCode(null);
        
        DynamoScheduledActivity newActivity = mapper.readValue(output, DynamoScheduledActivity.class);
        // The local schedule values are not serialized and the calculated values aren't deserialized, 
        // but they are verified above.
        newActivity.setTimeZone(DateTimeZone.UTC);
        newActivity.setLocalScheduledOn(scheduledOn);
        newActivity.setLocalExpiresOn(expiresOn);
        newActivity.setReferentGuid("referentGuid");
        
        // Also works without having to reset the timezone.
        assertEquals(newActivity, schActivity);
    }
    
    @Test
    public void hasValidStatusBasedOnTimestamps() throws Exception {
        LocalDateTime now = LocalDateTime.now(DateTimeZone.UTC);
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);

        schActivity.setLocalScheduledOn(now.plusHours(1));
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.SCHEDULED);
        
        schActivity.setLocalScheduledOn(now.minusHours(3));
        schActivity.setLocalExpiresOn(now.minusHours(1));
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.EXPIRED);
        
        schActivity.setLocalScheduledOn(null);
        schActivity.setLocalExpiresOn(null);
        
        schActivity.setStartedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.STARTED);
        
        schActivity.setFinishedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.FINISHED);
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setFinishedOn(DateTime.now().getMillis());
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.DELETED);
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setLocalScheduledOn(now.minusHours(1));
        schActivity.setLocalExpiresOn(now.plusHours(1));
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);
    }
    
    @Test
    public void hasValidStatusBasedOnTimestampsInPersistentActivity() {
        LocalDateTime now = LocalDateTime.now(DateTimeZone.UTC);
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setPersistent(true);
        schActivity.setTimeZone(DateTimeZone.UTC);
        
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);

        schActivity.setLocalScheduledOn(now.plusHours(1));
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);
        
        schActivity.setLocalScheduledOn(now.minusHours(3));
        schActivity.setLocalExpiresOn(now.minusHours(1));
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);
        
        schActivity.setLocalScheduledOn(null);
        schActivity.setLocalExpiresOn(null);
        
        schActivity.setStartedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.STARTED);
        
        schActivity.setFinishedOn(now.toDateTime(DateTimeZone.UTC).getMillis());
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.FINISHED);
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setFinishedOn(DateTime.now().getMillis());
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.DELETED);
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setLocalScheduledOn(now.minusHours(1));
        schActivity.setLocalExpiresOn(now.plusHours(1));
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);
    }
    
    /**
     * If a timestamp is not derived from a DateTime value passed into DynamoScheduledActivity, or set 
     * after construction, then the DateTime scheduledOn and expiresOn values are null.
     */
    @Test
    public void dealsTimeZoneAppropriately() {
        DateTime dateTime = DateTime.parse("2010-10-15T00:00:00.001+06:00");
        DateTime dateTimeInZone = DateTime.parse("2010-10-15T00:00:00.001Z");
        
        // Activity with datetime and zone (which is different)
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        // Without a time zone, getStatus() works
        assertEquals(schActivity.getStatus(), ScheduledActivityStatus.AVAILABLE);
        // Now set some values
        schActivity.setLocalScheduledOn(dateTime.toLocalDateTime());
        schActivity.setTimeZone(DateTimeZone.UTC);
        
        // Scheduled time should be in the time zone that is set
        assertEquals(schActivity.getScheduledOn().getZone(), DateTimeZone.UTC);
        // But the datetime does not itself change (this is one way to test this)
        assertEquals(schActivity.getScheduledOn().toLocalDateTime(), dateTimeInZone.toLocalDateTime());
        
        // setting new time zone everything shifts only in zone, not date or time
        DateTimeZone newZone = DateTimeZone.forOffsetHours(3);
        schActivity.setTimeZone(newZone);
        LocalDateTime copy = schActivity.getScheduledOn().toLocalDateTime();
        assertEquals(schActivity.getScheduledOn().getZone(), newZone);
        assertEquals(copy, dateTimeInZone.toLocalDateTime());
    }
    
    @Test
    public void dateTimesConvertedTest() {
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(3);
        DateTime now = DateTime.now();
        DateTime then = DateTime.now().minusDays(1);
        
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(timeZone);
        schActivity.setLocalScheduledOn(now.toLocalDateTime());
        schActivity.setLocalExpiresOn(then.toLocalDateTime());
        assertEquals(now.toLocalDateTime(), schActivity.getLocalScheduledOn());
        assertEquals(then.toLocalDateTime(), schActivity.getLocalExpiresOn());
        
        LocalDateTime local1 = LocalDateTime.parse("2010-01-01T10:10:10");
        LocalDateTime local2 = LocalDateTime.parse("2010-02-02T10:10:10");
        
        schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(timeZone);
        schActivity.setLocalScheduledOn(local1);
        schActivity.setLocalExpiresOn(local2);
        assertEquals(local1.toDateTime(timeZone), schActivity.getScheduledOn());
        assertEquals(local2.toDateTime(timeZone), schActivity.getExpiresOn());
    }
    
    @Test
    public void serializesCorrectlyToPublicAPI() throws Exception {
        DynamoScheduledActivity act = new DynamoScheduledActivity();
        act.setTimeZone(DateTimeZone.forOffsetHours(-6));
        act.setLocalScheduledOn(LocalDateTime.parse("2015-10-01T10:10:10.000"));
        act.setLocalExpiresOn(LocalDateTime.parse("2015-10-01T14:10:10.000"));
        act.setHealthCode("healthCode");
        act.setGuid("activityGuid");
        act.setSchedulePlanGuid("schedulePlanGuid");
        act.setActivity(TestUtils.getActivity1());
        act.setStartedOn(DateTime.parse("2015-10-10T08:08:08.000Z").getMillis());
        act.setFinishedOn(DateTime.parse("2015-12-05T08:08:08.000Z").getMillis());
        act.setReferentGuid(TestUtils.getActivity1().getSurvey().getGuid()+":survey:2015-10-01T10:10:10.000");
        act.setPersistent(true);
        
        String json = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(act);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("guid").textValue(), "activityGuid");
        assertEquals(node.get("startedOn").textValue(), "2015-10-10T08:08:08.000Z");
        assertEquals(node.get("finishedOn").textValue(), "2015-12-05T08:08:08.000Z");
        assertTrue(node.get("persistent").booleanValue());
        assertEquals(node.get("status").textValue(), "finished");
        assertEquals(node.get("schedulePlanGuid").textValue(), "schedulePlanGuid");
        assertEquals(node.get("type").textValue(), "ScheduledActivity");
        assertEquals(node.get("scheduledOn").textValue(), "2015-10-01T10:10:10.000-06:00");
        assertEquals(node.get("expiresOn").textValue(), "2015-10-01T14:10:10.000-06:00");
        assertNull(node.get("referentType"));
        // all the above, plus activity, and nothing else
        assertEquals(node.size(), 10);

        JsonNode activityNode = node.get("activity");
        assertEquals(activityNode.get("label").textValue(), "Activity1");
        assertNotNull(activityNode.get("guid").textValue());
        assertEquals(activityNode.get("activityType").textValue(), "survey");
        assertEquals(activityNode.get("type").textValue(), "Activity");
        // all the above, plus survey, and nothing else
        assertEquals(activityNode.size(), 5);
        
        JsonNode surveyNode = activityNode.get("survey");
        assertEquals(surveyNode.get("identifier").textValue(), "identifier1");
        assertEquals(surveyNode.get("guid").textValue(), "AAA");
        assertNotNull("href", surveyNode.get("href").textValue());
        assertEquals(surveyNode.get("type").textValue(), "SurveyReference");
        // all the above and nothing else
        assertEquals(surveyNode.size(), 4);
        
        // Were you to set scheduledOn/expiresOn directly, rather than time zone + local variants,
        // it would still preserve the timezone, that is, the time zone you set separately, not the 
        // time zone you specify.
        act.setLocalScheduledOn(LocalDateTime.parse("2015-10-01T10:10:10.000"));
        act.setLocalExpiresOn(LocalDateTime.parse("2015-10-01T14:10:10.000"));
        json = ScheduledActivity.SCHEDULED_ACTIVITY_WRITER.writeValueAsString(act);
        node = BridgeObjectMapper.get().readTree(json);
        // Still in time zone -6 hours.
        assertEquals(node.get("scheduledOn").asText(), "2015-10-01T10:10:10.000-06:00");
        assertEquals(node.get("expiresOn").asText(), "2015-10-01T14:10:10.000-06:00");
    }
}
