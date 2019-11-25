package org.sagebionetworks.bridge.models.surveys;

import static java.lang.Boolean.TRUE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SurveyQuestionOptionTest {
    private static final Image DUMMY_IMAGE = new Image("dummy-source", 42, 42);

    @Test
    public void allValues() {
        SurveyQuestionOption option = new SurveyQuestionOption("test-label", "test-detail", "test-value", DUMMY_IMAGE, true);
        assertEquals(option.getLabel(), "test-label");
        assertEquals(option.getDetail(), "test-detail");
        assertEquals(option.getValue(), "test-value");
        assertEquals(option.getImage(), DUMMY_IMAGE);
        assertTrue(option.isExclusive());
        
        String optionString = option.toString();
        assertTrue(optionString.contains(option.getLabel()));
        assertTrue(optionString.contains(option.getDetail()));
        assertTrue(optionString.contains(option.getValue()));
        assertTrue(optionString.contains(option.getImage().toString()));
        assertTrue(optionString.contains("exclusive=true"));
    }

    @Test
    public void blankValue() {
        String[] testCaseArr = { null, "", "   " };
        for (String oneTestCase : testCaseArr) {
            SurveyQuestionOption option = new SurveyQuestionOption("test-label", null, oneTestCase, null, null);
            assertEquals(option.getValue(), "test-label");
        }
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(SurveyQuestionOption.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void toStringAllNulls() throws Exception {
        // Make sure toString() doesn't throw if all fields are null.
        SurveyQuestionOption option = new SurveyQuestionOption(null, null, null, null, null);
        assertNotNull(option.toString());
    }
    
    @Test
    public void canSerialize() throws Exception {
        SurveyQuestionOption option = new SurveyQuestionOption("oneLabel", "oneDetail",
                "oneValue", new Image("oneSource", 100, 200), TRUE);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(option);
        
        assertEquals(node.get("label").textValue(), "oneLabel");
        assertEquals(node.get("detail").textValue(), "oneDetail");
        assertEquals(node.get("value").textValue(), "oneValue");
        assertTrue(node.get("exclusive").booleanValue());
        assertEquals(node.get("type").textValue(), "SurveyQuestionOption");
        
        JsonNode image = node.get("image");
        assertEquals(image.get("source").textValue(), "oneSource");
        assertEquals(image.get("width").intValue(), 100);
        assertEquals(image.get("height").intValue(), 200);
        assertEquals(image.get("type").textValue(), "Image");
        
        SurveyQuestionOption deser = BridgeObjectMapper.get().readValue(node.toString(), SurveyQuestionOption.class);
        assertEquals(deser, option);
    }
}
