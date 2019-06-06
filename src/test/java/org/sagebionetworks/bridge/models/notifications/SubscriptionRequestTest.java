package org.sagebionetworks.bridge.models.notifications;

import static org.testng.Assert.assertEquals;

import java.util.Set;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

public class SubscriptionRequestTest {

    @Test
    public void canSerialize() throws Exception {
        Set<String> topicGuids = Sets.newHashSet("topicA", "topicB");
        
        SubscriptionRequest request = new SubscriptionRequest(topicGuids);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(request);
        Set<String> serializedTopicGuids = Sets.newHashSet(node.get("topicGuids").get(0).asText(), node.get("topicGuids").get(1).asText());
        
        assertEquals(serializedTopicGuids, topicGuids);
        assertEquals(node.get("type").asText(), "SubscriptionRequest");
        
        SubscriptionRequest deser = BridgeObjectMapper.get().readValue(node.toString(), SubscriptionRequest.class);
        assertEquals(deser.getTopicGuids(), topicGuids);
    }
}
