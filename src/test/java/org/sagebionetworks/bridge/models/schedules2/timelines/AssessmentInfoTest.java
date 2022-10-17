package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.assessments.ImageResource;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AssessmentInfoTest extends Mockito {
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);;
    }
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AssessmentInfo.class)
            .allFieldsShouldBeUsedExcept("key").verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        ConfigResolver resolver = ConfigResolver.INSTANCE;
        
        String url = resolver.url("ws", "/v1/assessments/guid/config");
        
        AssessmentReference ref = new AssessmentReference();
        ref.setGuid("guid");
        ref.setAppId(TEST_APP_ID);
        ref.setIdentifier("identifier");
        ref.setRevision(5);
        ref.setTitle("title");
        ref.setLabels(ImmutableList.of(new Label("en", "English"), new Label("de", "German")));
        ref.setMinutesToComplete(10);
        ref.setColorScheme(new ColorScheme("#111111", "#222222", "#333333", "#444444"));
        ImageResource imageResource = new ImageResource();
        imageResource.setName("default");
        imageResource.setModule("sage_survey");
        imageResource.setLabels(LABELS);
        ref.setImageResource(imageResource);

        AssessmentInfo info = AssessmentInfo.create(ref);

        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("guid").textValue(), "guid");
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("identifier").textValue(), "identifier");
        assertEquals(node.get("revision").intValue(), 5);
        assertEquals(node.get("label").textValue(), "English");
        assertEquals(node.get("minutesToComplete").intValue(), 10);
        assertEquals(node.get("key").textValue(), "020a8323020a8323");
        assertEquals(node.get("configUrl").textValue(), url);
        assertEquals(node.get("type").textValue(), "AssessmentInfo");
        assertEquals(node.get("colorScheme").get("background").textValue(), "#111111");
        assertEquals(node.get("colorScheme").get("foreground").textValue(), "#222222");
        assertEquals(node.get("colorScheme").get("activated").textValue(), "#333333");
        assertEquals(node.get("colorScheme").get("inactivated").textValue(), "#444444");
        assertEquals(node.get("colorScheme").get("type").textValue(), "ColorScheme");
        assertEquals(node.get("imageResource").get("name").textValue(), "default");
        assertEquals(node.get("imageResource").get("module").textValue(), "sage_survey");
        assertEquals(node.get("imageResource").get("labels").get(0).get("lang").textValue(), LABELS.get(0).getLang());
        assertEquals(node.get("imageResource").get("labels").get(0).get("value").textValue(), LABELS.get(0).getValue());
        assertEquals(node.get("imageResource").get("labels").get(1).get("lang").textValue(), LABELS.get(1).getLang());
        assertEquals(node.get("imageResource").get("labels").get(1).get("value").textValue(), LABELS.get(1).getValue());
        assertEquals(node.get("imageResource").get("type").textValue(), "ImageResource");
        
        // shared ID also generates the correct URL
        url = resolver.url("ws", "/v1/sharedassessments/guid/config");
        ref.setAppId(SHARED_APP_ID);
        info = AssessmentInfo.create(ref);
        node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.get("configUrl").textValue(), url);
    }
    
    @Test
    public void serializationHandlesNulls() {
        AssessmentReference ref = new AssessmentReference();

        AssessmentInfo info = AssessmentInfo.create(ref);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.size(), 2);
        assertEquals(node.get("key").textValue(), "0000000000000000");
        assertEquals(node.get("type").textValue(), "AssessmentInfo");
    }
}
