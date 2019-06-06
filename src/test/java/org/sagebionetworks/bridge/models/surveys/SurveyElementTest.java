package org.sagebionetworks.bridge.models.surveys;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.Map;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyInfoScreen;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

@SuppressWarnings("unchecked")
public class SurveyElementTest {
    
    private static final SurveyRule BEFORE_RULE = new SurveyRule.Builder().withOperator(Operator.EQ).withValue(10)
            .withAssignDataGroup("foo").build();
    private static final SurveyRule AFTER_RULE = new SurveyRule.Builder().withEndSurvey(true)
            .withOperator(Operator.ALWAYS).build();
    
    @Test
    public void serializeSurveyQuestion() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'fireEvent':false," +
                "'guid':'test-guid'," +
                "'identifier':'test-survey-question'," +
                "'prompt':'Is this a survey question?'," +
                "'promptDetail':'Details about question'," +
                "'beforeRules':[{'operator':'eq','value':10,'assignDataGroup':'foo'}],"+
                "'afterRules':[{'operator':'always','endSurvey':true}],"+
                "'type':'SurveyQuestion'," +
                "'uiHint':'textfield'}");

        // convert to POJO
        DynamoSurveyQuestion question = (DynamoSurveyQuestion) BridgeObjectMapper.get().readValue(jsonText,
                SurveyElement.class);
        assertNull(question.getConstraints());
        assertNotNull(question.getData());
        assertFalse(question.getFireEvent());
        assertEquals(question.getGuid(), "test-guid");
        assertEquals(question.getIdentifier(), "test-survey-question");
        assertEquals(question.getOrder(), 0);
        assertEquals(question.getPrompt(), "Is this a survey question?");
        assertEquals(question.getPromptDetail(), "Details about question");
        assertNull(question.getSurveyCompoundKey());
        assertEquals(question.getType(), "SurveyQuestion");
        assertEquals(question.getUiHint(), UIHint.TEXTFIELD);
        
        assertEquals(question.getBeforeRules().get(0), BEFORE_RULE);
        assertEquals(question.getAfterRules().get(0), AFTER_RULE);

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(question);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(jsonMap.size(), 9);
        assertFalse((boolean) jsonMap.get("fireEvent"));
        assertEquals(jsonMap.get("guid"), "test-guid");
        assertEquals(jsonMap.get("identifier"), "test-survey-question");
        assertEquals(jsonMap.get("prompt"), "Is this a survey question?");
        assertEquals(jsonMap.get("promptDetail"), "Details about question");
        assertEquals(jsonMap.get("type"), "SurveyQuestion");
        assertEquals(jsonMap.get("uiHint"), "textfield");
        
        SurveyQuestion deserQuestion = BridgeObjectMapper.get().readValue(convertedJson, SurveyQuestion.class);
        
        assertFalse(deserQuestion.getFireEvent());
        assertEquals(deserQuestion.getGuid(), "test-guid");
        assertEquals(deserQuestion.getIdentifier(), "test-survey-question");
        assertEquals(deserQuestion.getPrompt(), "Is this a survey question?");
        assertEquals(deserQuestion.getPromptDetail(), "Details about question");
        assertEquals(deserQuestion.getType(), "SurveyQuestion");
        assertEquals(deserQuestion.getUiHint(), UIHint.TEXTFIELD);
        assertEquals(deserQuestion.getBeforeRules().get(0), BEFORE_RULE);
        assertEquals(deserQuestion.getAfterRules().get(0), AFTER_RULE);
    }

    @Test
    public void serializeSurveyInfoScreen() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'guid':'test-guid'," +
                "'identifier':'test-survey-info-screen'," +
                "'image':{" +
                "    'source':'http://www.example.com/test.png'," +
                "    'width':200," +
                "    'height':150" +
                "}," +
                "'prompt':'This is the survey info'," +
                "'beforeRules':[{'operator':'eq','value':10,'assignDataGroup':'foo'}],"+
                "'afterRules':[{'operator':'always','endSurvey':true}],"+
                "'promptDetail':'More info'," +
                "'title':'Survey Info'," +
                "'type':'SurveyInfoScreen'}");

        // convert to POJO
        DynamoSurveyInfoScreen infoScreen = (DynamoSurveyInfoScreen) BridgeObjectMapper.get().readValue(jsonText,
                SurveyElement.class);
        assertNotNull(infoScreen.getData());
        assertEquals(infoScreen.getGuid(), "test-guid");
        assertEquals(infoScreen.getIdentifier(), "test-survey-info-screen");
        assertEquals(infoScreen.getOrder(), 0);
        assertEquals(infoScreen.getPrompt(), "This is the survey info");
        assertEquals(infoScreen.getPromptDetail(), "More info");
        assertNull(infoScreen.getSurveyCompoundKey());
        assertEquals(infoScreen.getTitle(), "Survey Info");
        assertEquals(infoScreen.getType(), "SurveyInfoScreen");
        assertEquals(infoScreen.getBeforeRules().get(0), BEFORE_RULE);
        assertEquals(infoScreen.getAfterRules().get(0), AFTER_RULE);

        assertEquals(infoScreen.getImage().getSource(), "http://www.example.com/test.png");
        assertEquals(infoScreen.getImage().getWidth(), 200);
        assertEquals(infoScreen.getImage().getHeight(), 150);

        // convert back to JSON
        String convertedJson = BridgeObjectMapper.get().writeValueAsString(infoScreen);

        // then convert to a map so we can validate the raw JSON
        Map<String, Object> jsonMap = BridgeObjectMapper.get().readValue(convertedJson, JsonUtils.TYPE_REF_RAW_MAP);
        assertEquals(jsonMap.size(), 9);
        assertEquals(jsonMap.get("guid"), "test-guid");
        assertEquals(jsonMap.get("identifier"), "test-survey-info-screen");
        assertEquals(jsonMap.get("prompt"), "This is the survey info");
        assertEquals(jsonMap.get("promptDetail"), "More info");
        assertEquals(jsonMap.get("title"), "Survey Info");
        assertEquals(jsonMap.get("type"), "SurveyInfoScreen");

        Map<String, Object> imageMap = (Map<String, Object>) jsonMap.get("image");
        assertEquals(imageMap.size(), 4);
        assertEquals(imageMap.get("source"), "http://www.example.com/test.png");
        assertEquals(imageMap.get("width"), 200);
        assertEquals(imageMap.get("height"), 150);
        assertEquals(imageMap.get("type"), "Image");
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void serializeInvalidSurveyElementType() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'guid': 'bad-guid'," +
                "'identifier': 'bad-survey-element'," +
                "'prompt': 'Is this valid?'," +
                "'promptDetail': 'Details about validity'," +
                "'type': 'SurveyEggplant'}");

        // convert to POJO
        BridgeObjectMapper.get().readValue(jsonText, SurveyElement.class);
    }
}
