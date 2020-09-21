package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.models.ResourceList.START_DATE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.ResourceList.END_DATE;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DateRangeResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"))
                .withRequestParam(START_DATE, LocalDate.parse("2016-02-03"))
                .withRequestParam(END_DATE, LocalDate.parse("2016-02-23"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals(node.get("startDate").textValue(), "2016-02-03");
        assertEquals(node.get("endDate").textValue(), "2016-02-23");
        assertEquals(node.get("total").intValue(), 3);
        assertEquals(node.get("requestParams").get("startDate").textValue(), "2016-02-03");
        assertEquals(node.get("requestParams").get("endDate").textValue(), "2016-02-23");
        assertEquals(node.get("requestParams").get(ResourceList.TYPE).textValue(), ResourceList.REQUEST_PARAMS);
        assertEquals(node.get("type").textValue(), "DateRangeResourceList");
        assertEquals(node.get("items").size(), 3);
        assertEquals(node.get("items").get(0).asText(), "1");
        assertEquals(node.get("items").get(1).asText(), "2");
        assertEquals(node.get("items").get(2).asText(), "3");
        assertEquals(node.size(), 6);
        
        list = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<DateRangeResourceList<String>>() {});
        assertEquals(list.getStartDate(), LocalDate.parse("2016-02-03"));
        assertEquals(list.getEndDate(), LocalDate.parse("2016-02-23"));
        assertEquals(list.getItems().size(), 3);
        assertEquals(list.getItems().get(0), "1");
        assertEquals(list.getItems().get(1), "2");
        assertEquals(list.getItems().get(2), "3");
        assertEquals(list.getRequestParams().get("startDate"), "2016-02-03");
        assertEquals(list.getRequestParams().get("endDate"), "2016-02-23");
        assertEquals(list.getRequestParams().get(ResourceList.TYPE), ResourceList.REQUEST_PARAMS);
    }
    
    @Test
    public void canSerializeWithoutDeprecated() {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"), true)
                .withRequestParam(START_DATE, LocalDate.parse("2016-02-03"))
                .withRequestParam(END_DATE, LocalDate.parse("2016-02-23"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertNull(node.get("startDate"));
        assertNull(node.get("endDate"));
        assertNull(node.get("total"));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getTotal() throws Exception {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"));
        
        assertEquals(list.getTotal(), (Integer)3);
        assertNull(list.getRequestParams().get("total")); // not a request parameter
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals(node.get("total").intValue(), 3);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void nullList() {
        new DateRangeResourceList<>(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void emptyList() {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(Lists.newArrayList());

        assertTrue(list.getItems().isEmpty());
        // We are carrying over an exceptional behavior into this deprecated method where this 
        // list returns 0 instead of null, to keep integration tests and any potential clients
        // working. This value is going away as it is entirely redundent with the list size.
        assertEquals(list.getTotal(), (Integer)0);
    }
}
