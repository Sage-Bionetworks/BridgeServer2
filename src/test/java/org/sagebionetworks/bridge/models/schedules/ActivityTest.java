package org.sagebionetworks.bridge.models.schedules;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class ActivityTest {
    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Activity.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void equalsForActivity() {
        EqualsVerifier.forClass(SurveyReference.class)
            .allFieldsShouldBeUsedExcept("identifier", "resolver").verify();
    }

    @Test
    public void canSerializeCompoundActivity() throws Exception {
        // Start with JSON. For simplicity, only have the taskIdentifier in the CompoundActivity, so we aren't
        // sensitive to changes in that class.
        String jsonText = "{\n" +
                "   \"label\":\"My Activity\",\n" +
                "   \"labelDetail\":\"Description of activity\",\n" +
                "   \"guid\":\"test-guid\"\n," +
                "   \"compoundActivity\":{\"taskIdentifier\":\"combo-activity\"}\n" +
                "}";

        // convert to POJO
        Activity activity = BridgeObjectMapper.get().readValue(jsonText, Activity.class);
        assertEquals(activity.getLabel(), "My Activity");
        assertEquals(activity.getLabelDetail(), "Description of activity");
        assertEquals(activity.getGuid(), "test-guid");
        assertEquals(activity.getCompoundActivity().getTaskIdentifier(), "combo-activity");

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(activity, JsonNode.class);
        assertEquals(jsonNode.get("label").textValue(), "My Activity");
        assertEquals(jsonNode.get("labelDetail").textValue(), "Description of activity");
        assertEquals(jsonNode.get("guid").textValue(), "test-guid");
        assertEquals(jsonNode.get("compoundActivity").get("taskIdentifier").textValue(), "combo-activity");
        assertEquals(jsonNode.get("activityType").textValue(), "compound");
        assertEquals(jsonNode.get("type").textValue(), "Activity");
    }

    @Test
    public void canSerializeTaskActivity() throws Exception {
        Activity activity = new Activity.Builder().withGuid("actGuid").withLabel("Label")
                .withLabelDetail("Label Detail").withTask("taskId").build();
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);
        
        JsonNode node = mapper.readTree(json);
        assertEquals(node.get("label").asText(), "Label");
        assertEquals(node.get("labelDetail").asText(), "Label Detail");
        assertEquals(node.get("activityType").asText(), "task");
        assertEquals(node.get("task").get("identifier").asText(), "taskId");
        assertEquals(node.get("guid").asText(), "actGuid");
        assertEquals(node.get("type").asText(), "Activity");
        
        JsonNode taskRef = node.get("task");
        assertEquals(taskRef.get("identifier").asText(), "taskId");
        assertEquals(taskRef.get("type").asText(), "TaskReference");
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals(activity.getLabel(), "Label");
        assertEquals(activity.getLabelDetail(), "Label Detail");
        assertEquals(activity.getActivityType(), ActivityType.TASK);
        assertEquals(activity.getTask().getIdentifier(), "taskId");
    }
    
    @Test
    public void canSerializeSurveyActivity() throws Exception {
        Activity activity = new Activity.Builder().withGuid("actGuid").withLabel("Label")
                .withLabelDetail("Label Detail")
                .withSurvey("identifier", "guid", DateTime.parse("2015-01-01T10:10:10Z")).build();
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);
        
        JsonNode node = mapper.readTree(json);
        assertEquals(node.get("label").asText(), "Label");
        assertEquals(node.get("labelDetail").asText(), "Label Detail");
        assertEquals(node.get("activityType").asText(), "survey");
        String hrefString = node.get("survey").get("href").asText();
        assertTrue(hrefString.matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
        assertEquals(node.get("guid").asText(), "actGuid");
        assertEquals(node.get("type").asText(), "Activity");
        
        JsonNode ref = node.get("survey");
        assertEquals(ref.get("identifier").asText(), "identifier");
        assertEquals(ref.get("guid").asText(), "guid");
        assertEquals(ref.get("createdOn").asText(), "2015-01-01T10:10:10.000Z");
        String href = ref.get("href").asText();
        assertTrue(href.matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
        assertEquals(ref.get("type").asText(), "SurveyReference");
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals(activity.getLabel(), "Label");
        assertEquals(activity.getLabelDetail(), "Label Detail");
        assertEquals(activity.getActivityType(), ActivityType.SURVEY);
        
        SurveyReference ref1 = activity.getSurvey();
        assertEquals(ref1.getIdentifier(), "identifier");
        assertEquals(ref1.getCreatedOn(), DateTime.parse("2015-01-01T10:10:10.000Z"));
        assertEquals(ref1.getGuid(), "guid");
        assertTrue(ref1.getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/2015-01-01T10:10:10.000Z"));
    }
    
    @Test
    public void canSerializePublishedSurveyActivity() throws Exception {
        Activity activity = new Activity.Builder().withGuid("actGuid").withLabel("Label")
                .withLabelDetail("Label Detail").withPublishedSurvey("identifier", "guid").build();

        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        String json = mapper.writeValueAsString(activity);

        JsonNode node = mapper.readTree(json);
        assertEquals(node.get("label").asText(), "Label");
        assertEquals(node.get("labelDetail").asText(), "Label Detail");
        assertEquals(node.get("activityType").asText(), "survey");
        String hrefString = node.get("survey").get("href").asText();
        assertTrue(hrefString.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        assertEquals(node.get("guid").asText(), "actGuid");
        assertEquals(node.get("type").asText(), "Activity");
        
        JsonNode ref = node.get("survey");
        assertEquals(ref.get("identifier").asText(), "identifier");
        assertEquals(ref.get("guid").asText(), "guid");
        String href = ref.get("href").asText();
        assertTrue(href.matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
        assertEquals(ref.get("type").asText(), "SurveyReference");
        
        activity = mapper.readValue(json, Activity.class);
        assertEquals(activity.getLabel(), "Label");
        assertEquals(activity.getLabelDetail(), "Label Detail");
        assertEquals(activity.getActivityType(), ActivityType.SURVEY);
        
        SurveyReference ref1 = activity.getSurvey();
        assertEquals(ref1.getIdentifier(), "identifier");
        assertNull(ref1.getCreatedOn(), "createdOn");
        assertEquals(ref1.getGuid(), "guid");
        assertTrue(ref1.getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
    }
    
    @Test
    public void olderPublishedActivitiesCanBeDeserialized() throws Exception {
        String oldJson = "{\"label\":\"Personal Health Survey\",\"ref\":\"https://webservices-staging.sagebridge.org/api/v2/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published\",\"activityType\":\"survey\",\"survey\":{\"guid\":\"ac1e57fd-5e8e-473f-b82f-bac7547b6783\",\"identifier\":\"identifier\",\"type\":\"GuidCreatedOnVersionHolder\"},\"type\":\"Activity\"}";
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        Activity activity = mapper.readValue(oldJson, Activity.class);
        
        assertEquals(activity.getLabel(), "Personal Health Survey");
        assertEquals(activity.getActivityType(), ActivityType.SURVEY);
        
        SurveyReference ref = activity.getSurvey();
        assertEquals(ref.getIdentifier(), "identifier");
        assertNull(ref.getCreatedOn(), "createdOn null");
        assertEquals(ref.getGuid(), "ac1e57fd-5e8e-473f-b82f-bac7547b6783", "guid set");
        assertTrue(ref.getHref().matches("http[s]?://.*/v3/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published"));
    }
    
    @Test
    public void submittingJsonWithHrefWillNotBreak() throws Exception {
        String oldJson = "{\"label\":\"Personal Health Survey\",\"ref\":\"https://webservices-staging.sagebridge.org/api/v2/surveys/ac1e57fd-5e8e-473f-b82f-bac7547b6783/revisions/published\",\"activityType\":\"survey\",\"survey\":{\"guid\":\"ac1e57fd-5e8e-473f-b82f-bac7547b6783\",\"href\":\"junk\",\"identifier\":\"identifier\",\"type\":\"SurveyReference\"},\"type\":\"Activity\"}";
        
        BridgeObjectMapper mapper = BridgeObjectMapper.get();
        Activity activity = mapper.readValue(oldJson, Activity.class);
        
        assertNotEquals(activity.getSurvey().getHref(), "junk");
    }
    
    @Test
    public void creatingSurveyWithoutCreatedOnIsExpressedAsPublished() throws Exception {
        Activity activity = new Activity.Builder().withSurvey("identifier", "guid", null).withLabel("Label").build();
        
        assertTrue(activity.getSurvey().getHref().matches("http[s]?://.*/v3/surveys/guid/revisions/published"));
    }
    
    @Test
    public void activityFieldsAreDeserialized() throws Exception {
        String activityJSON = "{\"label\":\"Label\",\"guid\":\"AAA\",\"task\":{\"identifier\":\"task\",\"type\":\"TaskReference\"},\"activityType\":\"task\",\"ref\":\"task\",\"type\":\"Activity\"}";
        
        Activity activity = BridgeObjectMapper.get().readValue(activityJSON, Activity.class);
        assertEquals(activity.getGuid(), "AAA");
    }
    
    /**
     * Many of these cases should go away. The only thing we'll be interested in is the completion of an activity.
     * But it all works during the transition. 
     * @throws Exception
     */
    @Test
    public void activityKnowsWhenItIsPersistentlyScheduled() throws Exception {
        // This is persistently scheduled due to an activity
        Schedule schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"activity:HHH:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"HHH\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertTrue(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // This is persistently schedule due to an activity completion. We actually never generate this event, and it will go away.
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"task:foo:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"HHH\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertTrue(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // This is persistently schedule due to a survey completion. This should not match (it's not a survey)
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"survey:HHH:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"HHH\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertFalse(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // Wrong activity, not persistent
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"once\",\"eventId\":\"survey:HHH:finished\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"III\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertFalse(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
        
        // Persistent schedule type, creates persistent activities
        schedule = BridgeObjectMapper.get().readValue("{\"scheduleType\":\"persistent\",\"activities\":[{\"label\":\"Label\",\"labelDetail\":\"Label Detail\",\"guid\":\"III\",\"task\":{\"identifier\":\"foo\"},\"activityType\":\"task\"}]}", Schedule.class);
        assertTrue(schedule.getActivities().get(0).isPersistentlyRescheduledBy(schedule));
    }

    @Test
    public void compoundActivity() {
        CompoundActivity compoundActivity = new CompoundActivity.Builder().withTaskIdentifier("combo-activity")
                .build();
        Activity activity = new Activity.Builder().withLabel("My Label").withLabelDetail("My Label Detail")
                .withGuid("AAA").withCompoundActivity(compoundActivity).build();
        assertEquals(activity.getLabel(), "My Label");
        assertEquals(activity.getLabelDetail(), "My Label Detail");
        assertEquals(activity.getGuid(), "AAA");
        assertEquals(activity.getCompoundActivity(), compoundActivity);
        assertEquals(activity.getActivityType(), ActivityType.COMPOUND);
        assertEquals(activity.getSelfFinishedEventId(), "compound:combo-activity:finished");

        // toString() gives a lot of stuff and depends on two other classes. To make the tests robust and resilient to
        // changes in encapsulated classes, just test a few keywords
        String activityString = activity.toString();
        assertTrue(activityString.contains("My Label"));
        assertTrue(activityString.contains("My Label Detail"));
        assertTrue(activityString.contains("AAA"));
        assertTrue(activityString.contains("combo-activity"));
        assertTrue(activityString.contains("COMPOUND"));

        // test copy constructor
        Activity copy = new Activity.Builder().withActivity(activity).build();
        assertEquals(copy, activity);
    }

    @Test
    public void taskActivityByRef() {
        TaskReference task = new TaskReference("my-task", null);
        Activity activity = new Activity.Builder().withTask(task).build();
        assertEquals(activity.getTask(), task);
        assertEquals(activity.getActivityType(), ActivityType.TASK);
        assertEquals(activity.getSelfFinishedEventId(), "task:my-task:finished");

        String activityString = activity.toString();
        assertTrue(activityString.contains("my-task"));
        assertTrue(activityString.contains("TASK"));

        // test copy constructor
        Activity copy = new Activity.Builder().withActivity(activity).build();
        assertEquals(copy, activity);
    }

    @Test
    public void taskActivityById() {
        // This is already mostly tested above. Just test passing in task ID sets the task correctly.
        Activity activity = new Activity.Builder().withTask("my-task").build();
        assertEquals(activity.getTask(), new TaskReference("my-task", null));
    }

    @Test
    public void surveyActivityByRef() {
        SurveyReference survey = new SurveyReference("my-survey", "BBB", null);
        Activity activity = new Activity.Builder().withSurvey(survey).build();
        assertEquals(activity.getSurvey(), survey);
        assertEquals(activity.getActivityType(), ActivityType.SURVEY);
        assertEquals(activity.getSelfFinishedEventId(), "survey:BBB:finished");

        String activityString = activity.toString();
        assertTrue(activityString.contains("my-survey"));
        assertTrue(activityString.contains("BBB"));
        assertTrue(activityString.contains("SURVEY"));

        // test copy constructor
        Activity copy = new Activity.Builder().withActivity(activity).build();
        assertEquals(copy, activity);
    }

    @Test
    public void surveyActivityByIdGuidCreatedOn() {
        // most of this tested above
        DateTime createdOn = DateTime.now();
        Activity activity = new Activity.Builder().withSurvey("my-survey", "BBB", createdOn).build();
        assertEquals(activity.getSurvey(), new SurveyReference("my-survey", "BBB", createdOn));
    }

    @Test
    public void publishedSurveyActivity() {
        // most of this tested above
        Activity activity = new Activity.Builder().withPublishedSurvey("my-published-survey", "CCC").build();
        assertEquals(activity.getSurvey(), new SurveyReference("my-published-survey", "CCC", null));
    }
}
