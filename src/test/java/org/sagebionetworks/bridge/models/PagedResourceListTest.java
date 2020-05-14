package org.sagebionetworks.bridge.models;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.SUMMARY1;
import static org.sagebionetworks.bridge.TestConstants.SUMMARY2;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class PagedResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(SUMMARY1);
        accounts.add(SUMMARY2);

        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(accounts, 2)
                .withRequestParam("offsetBy", 123)
                .withRequestParam("pageSize", 100)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime)
                .withRequestParam("emailFilter", "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(node.get("offsetBy").intValue(), 123);
        assertEquals(node.get("total").intValue(), 2);
        assertEquals(node.get("pageSize").intValue(), 100);
        assertEquals(node.get("emailFilter").asText(), "filterString");
        assertEquals(node.get("startTime").asText(), startTime.toString());
        assertEquals(node.get("endTime").asText(), endTime.toString());
        assertEquals(node.get("type").asText(), "PagedResourceList");
        
        JsonNode rp = node.get("requestParams");
        assertEquals(rp.get("offsetBy").intValue(), 123);
        assertEquals(rp.get("pageSize").intValue(), 100);
        assertEquals(rp.get("startTime").asText(), startTime.toString());
        assertEquals(rp.get("endTime").asText(), endTime.toString());
        assertEquals(rp.get("emailFilter").asText(), "filterString");
        assertEquals(rp.get(ResourceList.TYPE).textValue(), ResourceList.REQUEST_PARAMS);
                
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(items.size(), 2);
        
        JsonNode child1 = items.get(0);
        assertEquals(child1.get("firstName").asText(), "firstName1");
        assertEquals(child1.get("lastName").asText(), "lastName1");
        assertEquals(child1.get("email").asText(), EMAIL);
        assertEquals(child1.get("id").asText(), "id");
        assertEquals(child1.get("status").asText(), "disabled");
        
        PagedResourceList<AccountSummary> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<PagedResourceList<AccountSummary>>() {});

        assertEquals(serPage.getTotal(), page.getTotal());
        assertEquals(serPage.getPageSize(), 100);
        assertEquals(serPage.getOffsetBy(), (Integer)123);
        assertEquals(serPage.getStartTime(), startTime);
        assertEquals(serPage.getEndTime(), endTime);
        assertEquals(serPage.getEmailFilter(), "filterString");
        
        Map<String,Object> params = page.getRequestParams();
        Map<String,Object> serParams = serPage.getRequestParams();
        assertEquals(serParams, params);
        
        assertEquals(serPage.getItems(), page.getItems());
    }
    
    @Test(expectedExceptions = NullPointerException.class)
    public void totalCannotBeNull() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        new PagedResourceList<AccountSummary>(accounts, null);
    }
}
