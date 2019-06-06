package org.sagebionetworks.bridge.models;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.AppleAppLink;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class AppleAppSiteAssociationTest {

    @Test
    public void createsCorrectJson() throws Exception {
        AppleAppLink detail1 = new AppleAppLink("appId1", Lists.newArrayList("/appId1/","/appId1/*"));
        AppleAppLink detail2 = new AppleAppLink("appId2", Lists.newArrayList("/appId2/","/appId2/*"));
        
        AppleAppSiteAssociation assoc = new AppleAppSiteAssociation(Lists.newArrayList(detail1, detail2));
        
        JsonNode node = new ObjectMapper().valueToTree(assoc);
        
        JsonNode applinks = node.get("applinks"); // It's all under this property for some reason.
        
        assertEquals(((ArrayNode)applinks.get("apps")).size(), 0);
        
        JsonNode details = applinks.get("details");
        
        JsonNode node1 = details.get(0);
        assertEquals(node1.get("appID").textValue(), "appId1");
        assertEquals(node1.get("paths").get(0).textValue(), "/appId1/");
        assertEquals(node1.get("paths").get(1).textValue(), "/appId1/*");
        
        JsonNode node2 = details.get(1);
        assertEquals(node2.get("appID").textValue(), "appId2");
        assertEquals(node2.get("paths").get(0).textValue(), "/appId2/");
        assertEquals(node2.get("paths").get(1).textValue(), "/appId2/*");
    }

}
