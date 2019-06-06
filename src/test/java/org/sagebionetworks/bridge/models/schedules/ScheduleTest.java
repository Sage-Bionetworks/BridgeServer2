package org.sagebionetworks.bridge.models.schedules;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class ScheduleTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Schedule.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canRountripSerialize() throws Exception {
        Activity activity = new Activity.Builder().withLabel("label").withTask("ref").build();
        
        Schedule schedule = new Schedule();
        schedule.getActivities().add(activity);
        schedule.setCronTrigger("0 0 8 ? * TUE *");
        schedule.setDelay(Period.parse("P1D"));
        schedule.setExpires(Period.parse("P2D"));
        schedule.setStartsOn(DateTime.parse("2015-02-02T10:10:10.000Z"));
        schedule.setEndsOn(DateTime.parse("2015-01-01T10:10:10.000Z"));
        schedule.setEventId(Schedule.EVENT_ID_PROPERTY);
        schedule.setInterval(Period.parse("P3D"));
        schedule.setSequencePeriod(Period.parse("P3W"));
        schedule.setLabel(Schedule.LABEL_PROPERTY);
        schedule.setScheduleType(ScheduleType.RECURRING);
        schedule.setTimes(Lists.newArrayList(LocalTime.parse("10:10"), LocalTime.parse("14:00")));
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String string = mapper.writeValueAsString(schedule);

        JsonNode node = mapper.readTree(string);
        assertEquals(node.get(Schedule.LABEL_PROPERTY).textValue(), "label");
        assertEquals(node.get(Schedule.SCHEDULE_TYPE_PROPERTY).textValue(), "recurring");
        assertEquals(node.get(Schedule.EVENT_ID_PROPERTY).textValue(), "eventId");
        assertEquals(node.get(Schedule.CRON_TRIGGER_PROPERTY).textValue(), "0 0 8 ? * TUE *");
        assertEquals(node.get(Schedule.DELAY_PROPERTY).textValue(), "P1D");
        assertEquals(node.get(Schedule.INTERVAL_PROPERTY).textValue(), "P3D");
        assertEquals(node.get(Schedule.EXPIRES_PROPERTY).textValue(), "P2D");
        assertEquals(node.get(Schedule.SEQUENCE_PERIOD_PROPERTY).textValue(), "P3W");
        assertEquals(node.get(Schedule.STARTS_ON_PROPERTY).textValue(), "2015-02-02T10:10:10.000Z");
        assertEquals(node.get(Schedule.ENDS_ON_PROPERTY).textValue(), "2015-01-01T10:10:10.000Z");
        assertFalse(node.get(Schedule.PERSISTENT_PROPERTY).booleanValue());
        assertEquals(node.get("type").textValue(), "Schedule");

        ArrayNode times = (ArrayNode)node.get("times");
        assertEquals(times.get(0).textValue(), "10:10:00.000");
        assertEquals(times.get(1).textValue(), "14:00:00.000");
        
        JsonNode actNode = node.get("activities").get(0);
        assertEquals(actNode.get("label").textValue(), "label");
        assertEquals(actNode.get("activityType").textValue(), "task");
        assertEquals(actNode.get("type").textValue(), "Activity");

        JsonNode taskNode = actNode.get("task");
        assertEquals(taskNode.get("identifier").textValue(), "ref");
        assertEquals(taskNode.get("type").textValue(), "TaskReference");
        
        schedule = mapper.readValue(string, Schedule.class);
        assertEquals(schedule.getCronTrigger(), "0 0 8 ? * TUE *");
        assertEquals(schedule.getDelay().toString(), "P1D");
        assertEquals(schedule.getExpires().toString(), "P2D");
        assertEquals(schedule.getEventId(), "eventId");
        assertEquals(schedule.getLabel(), "label");
        assertEquals(schedule.getInterval().toString(), "P3D");
        assertEquals(schedule.getScheduleType(), ScheduleType.RECURRING);
        assertEquals(schedule.getStartsOn().toString(), "2015-02-02T10:10:10.000Z");
        assertEquals(schedule.getEndsOn().toString(), "2015-01-01T10:10:10.000Z");
        assertEquals(schedule.getTimes().get(0).toString(), "10:10:00.000");
        assertEquals(schedule.getTimes().get(1).toString(), "14:00:00.000");
        assertEquals(schedule.getSequencePeriod().toString(), "P3W");
        activity = schedule.getActivities().get(0);
        assertEquals(activity.getLabel(), "label");
        assertEquals(activity.getTask().getIdentifier(), "ref");
    }
    
    @Test
    public void testStringSetters() {
        DateTime date = DateTime.parse("2015-02-02T10:10:10.000Z");
        Period period = Period.parse("P1D");
        Schedule schedule = new Schedule();
        schedule.setDelay("P1D");
        schedule.setEndsOn("2015-02-02T10:10:10.000Z");
        schedule.setStartsOn("2015-02-02T10:10:10.000Z");
        schedule.setExpires("P1D");
        schedule.setInterval("P1D");
        schedule.setSequencePeriod("P1D");
        schedule.addTimes("10:10");
        schedule.addTimes("12:10");
        
        assertEquals(schedule.getDelay(), period);
        assertEquals(schedule.getEndsOn(), date);
        assertEquals(schedule.getStartsOn(), date);
        assertEquals(schedule.getExpires(), period);
        assertEquals(schedule.getInterval(), period);
        assertEquals(schedule.getSequencePeriod(), period);
        assertEquals(schedule.getTimes(), Lists.newArrayList(LocalTime.parse("10:10"), LocalTime.parse("12:10")));
    }
    
    @Test
    public void scheduleIdentifiesWhenItIsPersistent() {
        Schedule schedule = new Schedule();
        assertFalse(schedule.getPersistent()); // safe to do this before anything else.
        
        Survey survey = new TestSurvey(ScheduleTest.class, false);
        Activity activity = new Activity.Builder().withLabel("Test").withSurvey(survey.getIdentifier(),
                        survey.getGuid(), new DateTime(survey.getCreatedOn())).build();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("survey:"+survey.getGuid()+":finished,enrollment");
        assertTrue(schedule.getPersistent());
        
        schedule.setScheduleType(ScheduleType.RECURRING);
        assertFalse(schedule.getPersistent());
        
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId(null);
        assertFalse(schedule.getPersistent());
        
        schedule = new Schedule();
        activity = new Activity.Builder().withLabel("Test").withTask("BBB").build();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("enrollment,task:BBB:finished");
        assertTrue(schedule.getPersistent());
    }
    
    @Test
    public void scheduleWithDelayNotPersistent() {
        Schedule schedule = new Schedule();
        Survey survey = new TestSurvey(ScheduleTest.class, false);
        Activity activity = new Activity.Builder().withLabel("Test").withSurvey(survey.getIdentifier(),
                        survey.getGuid(), new DateTime(survey.getCreatedOn())).build();
        schedule.addActivity(activity);
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setEventId("survey:"+survey.getGuid()+":finished,enrollment");
        
        schedule.setDelay("P1D");
        assertFalse(schedule.getPersistent());
        
        schedule.setDelay("P1M");
        assertFalse(schedule.getPersistent());
        
        schedule.setDelay("P0D");
        assertTrue(schedule.getPersistent());
        
        schedule.setDelay((Period)null);
        assertTrue(schedule.getPersistent());
    }
}
