package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoSchedulePlanTest {
    
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(DynamoSchedulePlan.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerializeDynamoSchedulePlan() throws Exception {
        DateTime datetime = DateTime.now().withZone(DateTimeZone.UTC);
        
        ScheduleStrategy strategy = TestUtils.getStrategy("P1D", TestUtils.getActivity1());
        
        DynamoSchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("guid");
        plan.setModifiedOn(datetime.getMillis());
        plan.setAppId(TEST_APP_ID);
        plan.setVersion(2L);
        plan.setDeleted(true);
        plan.setStrategy(strategy);
        
        String json = BridgeObjectMapper.get().writeValueAsString(plan);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("type").textValue(), "SchedulePlan");
        assertEquals(node.get("version").intValue(), 2);
        assertEquals(node.get("guid").textValue(), "guid");
        assertEquals(node.get("label").textValue(), "Label");
        assertTrue(node.get("deleted").booleanValue());
        assertNull(node.get("studyKey"));
        assertNotNull(node.get("strategy"));
        assertEquals(DateTime.parse(node.get("modifiedOn").asText()), datetime);

        DynamoSchedulePlan plan2 = DynamoSchedulePlan.fromJson(node);
        assertEquals(plan2.getVersion(), plan.getVersion());
        assertEquals(plan2.getGuid(), plan.getGuid());
        assertEquals(plan2.getLabel(), plan.getLabel());
        assertEquals(plan2.getModifiedOn(), plan.getModifiedOn());
        assertEquals(plan2.isDeleted(), plan.isDeleted());
        
        ScheduleStrategy retrievedStrategy = plan.getStrategy();
        assertEquals(strategy, retrievedStrategy);
    }
    
    @Test
    public void jsonStudyKeyIsIgnored() throws Exception {
        String json = TestUtils.createJson("{'studyKey':'study-key'}");
        
        SchedulePlan plan = BridgeObjectMapper.get().readValue(json, SchedulePlan.class);
        assertNull(plan.getAppId());
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        plan = DynamoSchedulePlan.fromJson(node);
        assertNull(plan.getAppId());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void fromJson_NullStrategyType() throws Exception {
        String json = "{\n" +
                "   \"strategy\":{}\n" +
                "}";
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        DynamoSchedulePlan.fromJson(node);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void fromJson_NonExistentStrategyType() throws Exception {
        String json = "{\n" +
                "   \"strategy\":{\n" +
                "       \"type\":\"NonExistent\"\n" +
                "   }\n" +
                "}";
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        DynamoSchedulePlan.fromJson(node);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void fromJson_InvalidStrategyType() throws Exception {
        String json = "{\n" +
                "   \"strategy\":{\n" +
                "       \"type\":\"ScheduleCriteria\"\n" +
                "   }\n" +
                "}";
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        DynamoSchedulePlan.fromJson(node);
    }
}
