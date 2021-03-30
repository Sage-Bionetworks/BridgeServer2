package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

public class AssessmentInfoTest extends Mockito {
    
    @Test
    public void canSerialize() throws Exception {
        AssessmentReference ref = new AssessmentReference();
        ref.setGuid("guid");
        ref.setAppId(TEST_APP_ID);
        ref.setIdentifier("identifier");
        ref.setTitle("title");
        ref.setLabels(ImmutableList.of(new Label("en", "English"), new Label("de", "German")));
        ref.setMinutesToComplete(10);
        ref.setColorScheme(new ColorScheme("#111111", "#222222", "#333333", "#444444"));

        AssessmentInfo info = AssessmentInfo.create(ref);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("guid").textValue(), "guid");
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("identifier").textValue(), "identifier");
        assertEquals(node.get("label").textValue(), "English");
        assertEquals(node.get("minutesToComplete").intValue(), 10);
        assertEquals(node.get("key").textValue(), "1984641878");
        assertEquals(node.get("type").textValue(), "AssessmentInfo");
        assertEquals(node.get("colorScheme").get("background").textValue(), "#111111");
        assertEquals(node.get("colorScheme").get("foreground").textValue(), "#222222");
        assertEquals(node.get("colorScheme").get("activated").textValue(), "#333333");
        assertEquals(node.get("colorScheme").get("inactivated").textValue(), "#444444");
        assertEquals(node.get("colorScheme").get("type").textValue(), "ColorScheme");
    }
    
    @Test
    public void serializationHandlesNulls() {
        AssessmentReference ref = new AssessmentReference();

        AssessmentInfo info = AssessmentInfo.create(ref);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.size(), 2);
        assertEquals(node.get("key").textValue(), "887503681");
        assertEquals(node.get("type").textValue(), "AssessmentInfo");
    }
}
