package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class ForwardCursorPagedResourceListTest {
    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", SYNAPSE_USER_ID, PHONE,
                ImmutableMap.of("substudy1", "externalId1"), "id", DateTime.now(), DISABLED, TEST_STUDY,
                ImmutableSet.of()));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", SYNAPSE_USER_ID, PHONE,
                ImmutableMap.of("substudy2", "externalId2"), "id2", DateTime.now(), ENABLED, TEST_STUDY,
                ImmutableSet.of()));
        
        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        
        ForwardCursorPagedResourceList<AccountSummary> page = new ForwardCursorPagedResourceList<AccountSummary>(
                accounts, "nextOffsetKey")
                .withRequestParam(ResourceList.OFFSET_KEY, "offsetKey")
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime)
                .withRequestParam(ResourceList.SCHEDULED_ON_START, startTime)
                .withRequestParam(ResourceList.SCHEDULED_ON_END, endTime)
                .withRequestParam(ResourceList.PAGE_SIZE, 100)
                .withRequestParam(ResourceList.EMAIL_FILTER, "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        
        assertEquals(node.get("offsetKey").asText(), "nextOffsetKey");
        assertEquals(node.get("nextPageOffsetKey").asText(), "nextOffsetKey");
        assertEquals(node.get("startTime").asText(), startTime.toString());
        assertEquals(node.get("endTime").asText(), endTime.toString());
        assertEquals(node.get("scheduledOnStart").asText(), startTime.toString());
        assertEquals(node.get("scheduledOnEnd").asText(), endTime.toString());
        assertEquals(node.get("pageSize").intValue(), 100);
        assertEquals(node.get("total").intValue(), 2);
        assertEquals(node.get("type").asText(), "ForwardCursorPagedResourceList");
        
        JsonNode rp = node.get("requestParams");
        assertEquals(rp.get("startTime").asText(), startTime.toString());
        assertEquals(rp.get("endTime").asText(), endTime.toString());
        assertEquals(rp.get("scheduledOnStart").asText(), startTime.toString());
        assertEquals(rp.get("scheduledOnEnd").asText(), endTime.toString());
        assertEquals(rp.get("emailFilter").asText(), "filterString");
        assertEquals(rp.get("pageSize").intValue(), 100);
        assertEquals(rp.get("offsetKey").asText(), "offsetKey");
        assertEquals(rp.get(ResourceList.TYPE).textValue(), ResourceList.REQUEST_PARAMS);
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(items.size(), 2);
        
        JsonNode child1 = items.get(0);
        assertEquals(child1.get("firstName").asText(), "firstName1");
        assertEquals(child1.get("lastName").asText(), "lastName1");
        assertEquals(child1.get("email").asText(), "email1@email.com");
        assertEquals(child1.get("id").asText(), "id");
        assertEquals(child1.get("status").asText(), "disabled");
        
        ForwardCursorPagedResourceList<AccountSummary> serPage = BridgeObjectMapper.get().readValue(node.toString(),
                new TypeReference<ForwardCursorPagedResourceList<AccountSummary>>() {
                });
        
        assertEquals(serPage.getNextPageOffsetKey(), "nextOffsetKey");
        assertEquals(serPage.getOffsetKey(), "nextOffsetKey");
        assertEquals(serPage.getStartTime(), startTime);
        assertEquals(serPage.getEndTime(), endTime);
        assertEquals(serPage.getScheduledOnStart(), startTime);
        assertEquals(serPage.getScheduledOnEnd(), endTime);
        assertEquals(serPage.getPageSize(), (Integer)100);
        assertEquals(serPage.getTotal(), (Integer)2);
        
        Map<String,Object> params = serPage.getRequestParams();
        assertEquals(params.get("startTime"), startTime.toString());
        assertEquals(params.get("endTime"), endTime.toString());
        assertEquals(params.get("scheduledOnStart"), startTime.toString());
        assertEquals(params.get("scheduledOnEnd"), endTime.toString());
        assertEquals(params.get("pageSize"), 100);
        assertEquals(params.get("emailFilter"), "filterString");
        assertEquals(params.get("offsetKey"), "offsetKey");
        
        assertEquals(serPage.getItems(), page.getItems());
    }
    
    @Test
    public void hasNext() {
        ForwardCursorPagedResourceList<AccountSummary> list;
        JsonNode node;
        
        list = new ForwardCursorPagedResourceList<>(Lists.newArrayList(), null);
        assertFalse(list.hasNext());
        node = BridgeObjectMapper.get().valueToTree(list);
        assertFalse(node.get("hasNext").booleanValue());
        assertNull(node.get("offsetKey"));
        assertNull(node.get("nextPageOffsetKey"));
        
        list = new ForwardCursorPagedResourceList<>(Lists.newArrayList(), "nextPageKey");
        assertTrue(list.hasNext());
        node = BridgeObjectMapper.get().valueToTree(list);
        assertTrue(node.get("hasNext").booleanValue());
        assertEquals(node.get("offsetKey").asText(), "nextPageKey");
        assertEquals(node.get("nextPageOffsetKey").asText(), "nextPageKey");
    }
    
    // This test was moved from another class that implemented PagedResourceList for
    // DynamoDB, that was easily incorporated into this implementation. This test verifies
    // that the results are the same as before.
    @Test
    public void canSerializeWithDynamoOffsetKey() throws Exception {
        List<String> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add("value1");
        accounts.add("value2");
        
        ForwardCursorPagedResourceList<String> page = new ForwardCursorPagedResourceList<>(accounts, null)
                .withRequestParam(ResourceList.PAGE_SIZE, 100)
                .withRequestParam(ResourceList.ID_FILTER, "foo")
                .withRequestParam(ResourceList.ASSIGNMENT_FILTER, "bar");
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(node.get("pageSize").intValue(), 100);
        assertEquals(node.get("requestParams").get("idFilter").asText(), "foo");
        assertEquals(node.get("requestParams").get("assignmentFilter").asText(), "bar");
        assertEquals(node.get("type").asText(), "ForwardCursorPagedResourceList");
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(items.size(), 2);
        assertEquals(items.get(0).asText(), "value1");
        assertEquals(items.get(1).asText(), "value2");
        
        // We don't deserialize this, but let's verify for the SDK
        ForwardCursorPagedResourceList<String> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<ForwardCursorPagedResourceList<String>>() {});
        
        assertEquals(serPage.getNextPageOffsetKey(), page.getNextPageOffsetKey());
        assertEquals(serPage.getRequestParams().get("pageSize"), page.getRequestParams().get("pageSize"));
        assertEquals(serPage.getRequestParams().get("idFilter"), page.getRequestParams().get("idFilter"));
        assertEquals(serPage.getRequestParams().get("assignmentFilter"), page.getRequestParams().get("assignmentFilter"));
        assertEquals(serPage.getItems(), page.getItems());
    }
}
