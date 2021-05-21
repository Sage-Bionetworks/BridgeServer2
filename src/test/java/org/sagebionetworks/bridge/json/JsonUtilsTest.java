package org.sagebionetworks.bridge.json;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.UIHint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

/**
 * By and large, these are null-safe accessors of values in the Jackson JSON object model.
 *
 */
public class JsonUtilsTest {
    
    public ObjectMapper mapper = BridgeObjectMapper.get();
    
    private String esc(String string) {
        return string.replaceAll("'", "\"");
    }

    @Test
    public void asDateTime() throws Exception {
        String expectedDateTimeStr = "2018-02-16T17:14:05.520-08:00";

        // Set up test cases
        String jsonText = "{\n" +
                "   \"json-null\":null,\n" +
                "   \"empty-string\":\"\",\n" +
                "   \"blank-string\":\"   \",\n" +
                "   \"bad-format\":\"February 16, 2018 2 5:14pm\",\n" +
                "   \"success-with-time-zone\":\"" + expectedDateTimeStr + "\"\n" +
                "}";
        JsonNode node = mapper.readTree(jsonText);

        // Null cases
        assertNull(JsonUtils.asDateTime(node, "no-value"));
        assertNull(JsonUtils.asDateTime(node, "json-null"));
        assertNull(JsonUtils.asDateTime(node, "empty-string"));
        assertNull(JsonUtils.asDateTime(node, "blank-string"));
        assertNull(JsonUtils.asDateTime(node, "bad-format"));

        // Success case
        DateTime dateTime = JsonUtils.asDateTime(node, "success-with-time-zone");
        assertNotNull(dateTime);
        assertEquals(dateTime.toString(), expectedDateTimeStr);
    }

    @Test
    public void asText() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'value'}"));
        
        assertNull(JsonUtils.asText(node, (String)null));
        assertNull(JsonUtils.asText(node, "badProp"));
        assertEquals(JsonUtils.asText(node, "key"), "value");
        assertEquals(JsonUtils.asText(node, "wrongKey", "key"), "value");
    }

    @Test
    public void asLong() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertNull(JsonUtils.asLong(node, null));
        assertNull(JsonUtils.asLong(node, "badProp"));
        assertEquals(JsonUtils.asLong(node, "key"), new Long(3));
    }

    @Test
    public void asLongPrimitive() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertEquals(JsonUtils.asLongPrimitive(node, null), 0L);
        assertEquals(JsonUtils.asLongPrimitive(node, "badProp"), 0L);
        assertEquals(JsonUtils.asLongPrimitive(node, "key"), 3L);
    }

    @Test
    public void asInt() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertNull(JsonUtils.asInt(node, null));
        assertNull(JsonUtils.asInt(node, "badProp"));
        assertEquals(JsonUtils.asInt(node, "key"), new Integer(3));
    }

    @Test
    public void asIntPrimitive() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':3}"));
        
        assertEquals(JsonUtils.asIntPrimitive(node, null), 0);
        assertEquals(JsonUtils.asIntPrimitive(node, "badProp"), 0);
        assertEquals(JsonUtils.asIntPrimitive(node, "key"), 3);
    }

    @Test
    public void asMillisDuration() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'PT1H'}"));
        
        assertEquals(JsonUtils.asMillisDuration(node, null), 0L);
        assertEquals(JsonUtils.asMillisDuration(node, "badProp"), 0L);
        assertEquals(JsonUtils.asMillisDuration(node, "key"), 1*60*60*1000);
    }

    @Test
    public void asMillisSinceEpoch() throws Exception {
        DateTime time = DateTime.parse("2015-03-23T10:00:00.000-07:00");
        
        JsonNode node = mapper.readTree(esc("{'key':'2015-03-23T10:00:00.000-07:00'}"));
        
        assertEquals(JsonUtils.asMillisSinceEpoch(node, null), 0L);
        assertEquals(JsonUtils.asMillisSinceEpoch(node, "badProp"), 0L);
        assertEquals(JsonUtils.asMillisSinceEpoch(node, "key"), time.getMillis());
    }

    @Test
    public void asJsonNode() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':{'subKey':'value'}}"));
        
        JsonNode node2 = mapper.readTree(esc("{'subKey':'value'}"));
        
        assertNull(JsonUtils.asJsonNode(node, null));
        assertNull(JsonUtils.asJsonNode(node, "badProp"));
        assertEquals(JsonUtils.asJsonNode(node, "key"), node2);
    }

    @Test
    public void asConstraints() throws Exception {
        IntegerConstraints c = new IntegerConstraints();
        c.setMinValue(1d);
        c.setMaxValue(5d);
        
        JsonNode node = mapper.readTree(esc("{'key':"+mapper.writeValueAsString(c)+"}"));
        assertNull(JsonUtils.asConstraints(node, null));
        assertNull(JsonUtils.asConstraints(node, "badProp"));
        
        IntegerConstraints copy = (IntegerConstraints)JsonUtils.asConstraints(node, "key");
        assertEquals(copy.getType(), c.getType());
        assertEquals(copy.getMinValue(), c.getMinValue());
        assertEquals(copy.getMaxValue(), c.getMaxValue());
    }

    @Test
    public void asObjectNode() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':{'subKey':'value'}}"));
        JsonNode subNode = mapper.readTree(esc("{'subKey':'value'}"));
        
        assertNull(JsonUtils.asObjectNode(node, null));
        assertNull(JsonUtils.asObjectNode(node, "badProp"));
        assertEquals(JsonUtils.asObjectNode(node, "key"), subNode);
    }

    @Test
    public void asBoolean() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':true}"));
        
        assertFalse(JsonUtils.asBoolean(node, null));
        assertFalse(JsonUtils.asBoolean(node, "badProp"));
        assertTrue(JsonUtils.asBoolean(node, "key"));
    }

    @Test
    public void asUIHint() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'list'}"));
        
        assertNull(JsonUtils.asEntity(node, null, UIHint.class));
        assertNull(JsonUtils.asEntity(node, "badProp", UIHint.class));
        assertEquals(JsonUtils.asEntity(node, "key", UIHint.class), UIHint.LIST);
    }
    
    @Test
    public void asActivityType() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'survey'}"));
        
        assertNull(JsonUtils.asEntity(node, null, ActivityType.class));
        assertNull(JsonUtils.asEntity(node, "badProp", ActivityType.class));
        assertEquals(JsonUtils.asEntity(node, "key", ActivityType.class), ActivityType.SURVEY);
    }

    @Test
    public void asScheduleType() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':'once'}"));
        
        assertNull(JsonUtils.asEntity(node, null, ScheduleType.class));
        assertNull(JsonUtils.asEntity(node, "badProp", ScheduleType.class));
        assertEquals(JsonUtils.asEntity(node, "key", ScheduleType.class), ScheduleType.ONCE);
    }
    
    @Test
    public void asImage() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':{'source':'sourceValue','width':50,'height':50}}"));
        Image image = new Image("sourceValue", 50, 50);
        
        assertNull(JsonUtils.asEntity(node, null, Image.class));
        assertNull(JsonUtils.asEntity(node, "badProp", Image.class));
        assertEquals(JsonUtils.asEntity(node, "key", Image.class), image);
    }
    
    @Test
    public void asArrayNode() throws Exception {
        JsonNode node = mapper.readTree(esc("{'key':[1,2,3,4]}"));
        JsonNode subNode = mapper.readTree(esc("[1,2,3,4]"));
        
        assertNull(JsonUtils.asArrayNode(node, null));
        assertNull(JsonUtils.asArrayNode(node, "badProp"));
        assertEquals(JsonUtils.asArrayNode(node, "key"), subNode);
    }

    @Test
    public void asStringSet() throws Exception {
        Set<String> set = Sets.newHashSet("A", "B", "C");
        
        JsonNode node = mapper.readTree(esc("{'key':['A','B','C']}"));
        
        assertEquals(JsonUtils.asStringSet(node, null), Sets.newHashSet());
        assertEquals(JsonUtils.asStringSet(node, "badProp"), Sets.newHashSet());
        assertEquals(JsonUtils.asStringSet(node, "key"), set);
    }

    @Test
    public void asRolesSet() throws Exception {
        Set<Roles> set = Sets.newHashSet(ADMIN, RESEARCHER);
        
        JsonNode node = mapper.readTree(esc("{'key':['admin','researcher']}"));

        assertEquals(JsonUtils.asRolesSet(node, null), Sets.newHashSet());
        assertEquals(JsonUtils.asRolesSet(node, "badProp"), Sets.newHashSet());
        assertEquals(JsonUtils.asRolesSet(node, "key"), set);
    }

    @Test
    public void mergeObjectNodes_zeroNodes() {
        ObjectNode result = JsonUtils.mergeObjectNodes();
        assertTrue(result.isEmpty());
    }

    @Test
    public void mergeObjectNodes_oneNode() throws Exception {
        JsonNode input = mapper.readTree("{\"a\":1,\"b\":2}");
        ObjectNode result = JsonUtils.mergeObjectNodes(input);
        assertEquals(result, input);
    }

    @Test
    public void mergeObjectNodes_twoNodes() throws Exception {
        JsonNode input1 = mapper.readTree("{\"a\":1,\"b\":2}");
        JsonNode input2 = mapper.readTree("{\"c\":3,\"d\":4}");

        ObjectNode result = JsonUtils.mergeObjectNodes(input1, input2);
        assertEquals(result.size(), 4);
        assertEquals(result.get("a").intValue(), 1);
        assertEquals(result.get("b").intValue(), 2);
        assertEquals(result.get("c").intValue(), 3);
        assertEquals(result.get("d").intValue(), 4);
    }

    @Test
    public void mergeObjectNodes_threeNodes() throws Exception {
        JsonNode input1 = mapper.readTree("{\"a\":1}");
        JsonNode input2 = mapper.readTree("{\"b\":2}");
        JsonNode input3 = mapper.readTree("{\"c\":3}");

        ObjectNode result = JsonUtils.mergeObjectNodes(input1, input2, input3);
        assertEquals(result.size(), 3);
        assertEquals(result.get("a").intValue(), 1);
        assertEquals(result.get("b").intValue(), 2);
        assertEquals(result.get("c").intValue(), 3);
    }

    @Test
    public void mergeObjectNodes_mergeWithNullObject() throws Exception {
        JsonNode input = mapper.readTree("{\"a\":1,\"b\":2}");
        ObjectNode result = JsonUtils.mergeObjectNodes(input, null);
        assertEquals(result, input);
    }

    @Test
    public void mergeObjectNodes_mergeWithNullNode() throws Exception {
        JsonNode input = mapper.readTree("{\"a\":1,\"b\":2}");
        ObjectNode result = JsonUtils.mergeObjectNodes(input, NullNode.instance);
        assertEquals(result, input);
    }

    @Test
    public void mergeObjectNodes_mergeNonObject() throws Exception {
        JsonNode input1 = mapper.readTree("{\"a\":1,\"b\":2}");
        JsonNode input2 = mapper.readTree("[\"not\", \"an\", \"object\"]");
        ObjectNode result = JsonUtils.mergeObjectNodes(input1, input2);
        assertEquals(result, input1);
    }
}
