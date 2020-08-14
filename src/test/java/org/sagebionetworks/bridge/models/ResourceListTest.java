package org.sagebionetworks.bridge.models;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerializeWithDeprecated() throws Exception {
        ResourceList<String> list = new ResourceList<>(ImmutableList.of("A","B","C"), false)
                .withRequestParam("test", 13L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        
        assertEquals(node.size(), 4);
        assertEquals(node.get("total").intValue(), 3);
        assertEquals(node.get("requestParams").get("test").intValue(), 13);
        assertEquals(node.get("requestParams").get(ResourceList.TYPE).textValue(), ResourceList.REQUEST_PARAMS);
        assertEquals(node.get("items").size(), 3);
        assertEquals(node.get("type").asText(), "ResourceList");
        
        ResourceList<String> deser = BridgeObjectMapper.get().readValue(node.toString(), new TypeReference<ResourceList<String>>() {});
        
        assertEquals(deser.getItems().get(0), "A");
        assertEquals(deser.getItems().get(1), "B");
        assertEquals(deser.getItems().get(2), "C");
        // This is deserialized as an integer, not a long, that is a property of the library. Looks the same in JSON.
        assertEquals((Integer)deser.getRequestParams().get("test"), (Integer)13);
        assertEquals(deser.getTotal(), (Integer)3);
        assertEquals(deser.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);
    }
    
    @Test
    public void canSerializeWithoutDeprecated() throws Exception {
        ResourceList<String> list = new ResourceList<>(ImmutableList.of("A","B","C"), true)
                .withRequestParam("test", 13L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertNull(node.get("total"));
    }

    @Test
    public void noTotalPropertyWhenListEmpty() {
        ResourceList<String> list = new ResourceList<>(ImmutableList.of());
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertFalse(node.has("total"));
    }
    
    @Test
    public void requestParams() {
        ResourceList<String> list = makeResourceList();
        
        assertEquals(list.getRequestParams().get("foo"), "bar");
        assertNull(list.getRequestParams().get("baz"));
    }
    
    @Test
    public void getDateTime() {
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        DateTime dateTime = DateTime.now(MSK);
        
        ResourceList<String> list = makeResourceList();
        list.withRequestParam("dateTime1", dateTime);
        list.withRequestParam("dateTime2", dateTime.toString());
        
        TestUtils.assertDatesWithTimeZoneEqual(dateTime,  list.getDateTime("dateTime1"));
        TestUtils.assertDatesWithTimeZoneEqual(dateTime,  list.getDateTime("dateTime2"));
    }
    
    @Test
    public void getNullDateTime() {
        ResourceList<String> list = makeResourceList();
        
        assertNull(list.getDateTime("No value"));
    }
    
    @Test
    public void getLocalDate() {
        LocalDate localDate = LocalDate.parse("2017-04-15");
        
        ResourceList<String> list = makeResourceList();
        list.withRequestParam("localDate1", localDate);
        list.withRequestParam("localDate2", localDate.toString());
        
        assertEquals(list.getLocalDate("localDate1"), localDate);
        assertEquals(list.getLocalDate("localDate2"), localDate);
    }
    
    @Test
    public void getNullLocalDate() {
        ResourceList<String> list = makeResourceList();
        
        assertNull(list.getLocalDate("No value"));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getTotal() {
        ResourceList<String> list = makeResourceList();
        
        assertEquals(list.getTotal(), (Integer)3);
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void nullList() {
        new ResourceList<>(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void emptyList() {
        ResourceList<String> list = new ResourceList<>(Lists.newArrayList());

        assertTrue(list.getItems().isEmpty());
        assertNull(list.getTotal());
    }
    
    @Test
    public void blankOrNullRequestParamKeysNotAdded() {
        List<String> items = Lists.newArrayList("A","B","C");
        ResourceList<String> list = new ResourceList<>(items);
        
        list.withRequestParam(null, "a");
        list.withRequestParam("", "a");
        list.withRequestParam("  ", "a");
        
        assertEquals(list.getRequestParams().size(), 1);
        assertEquals(list.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);
    }

    @Test
    public void nullRequestParamValuesNotAdded() {
        List<String> items = Lists.newArrayList("A","B","C");
        ResourceList<String> list = new ResourceList<>(items);
        list.withRequestParam("a valid key", null);
        
        assertEquals(list.getRequestParams().size(), 1);
        assertEquals(list.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);
    }
    
    @Test
    public void cannnotChanngeRequestParamsType() {
        List<String> items = Lists.newArrayList("A","B","C");
        ResourceList<String> list = new ResourceList<>(items);
        list.withRequestParam("type", "not the right type");
        assertEquals(list.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);
    }
    
    private ResourceList<String> makeResourceList() {
        List<String> items = Lists.newArrayList("A","B","C");
        
        ResourceList<String> list = new ResourceList<>(items);
        list.withRequestParam("foo", "bar");
        return list;
    }
}
