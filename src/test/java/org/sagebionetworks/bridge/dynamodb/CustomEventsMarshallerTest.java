package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.collect.Maps.newHashMap;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoApp.CustomEventsMarshaller;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;

public class CustomEventsMarshallerTest extends Mockito {
    
    CustomEventsMarshaller marshaller;

    @BeforeMethod
    public void beforeMethod() {
        marshaller = new CustomEventsMarshaller();
    }
    
    @Test
    public void serializes() throws Exception {
        Map<String, ActivityEventUpdateType> map = newHashMap(
                ImmutableMap.of("event1", IMMUTABLE, 
                        "event2", MUTABLE, "event3", FUTURE_ONLY));
        
        String json = marshaller.convert(map);
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals(node.get("event1").textValue(), "immutable");
        assertEquals(node.get("event2").textValue(), "mutable");
        assertEquals(node.get("event3").textValue(), "future_only");
        
        Map<String, ActivityEventUpdateType> deser = marshaller.unconvert(json);
        assertEquals(deser.get("event1"), IMMUTABLE);
        assertEquals(deser.get("event2"), MUTABLE);
        assertEquals(deser.get("event3"), FUTURE_ONLY);
    }
    
    @Test
    public void handlesNulls() {
        assertNull( marshaller.convert(null) );
        assertNull( marshaller.unconvert(null) );
    }
}
