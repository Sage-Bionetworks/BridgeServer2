package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.UIHint;
import org.sagebionetworks.bridge.models.surveys.Unit;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSurveyQuestionTest {
    
    @Test
    public void copyConstructor() {
        SurveyQuestion question = SurveyQuestion.create();
        question.setPrompt("prompt");
        question.setPromptDetail("promptDetail");
        question.setFireEvent(true);
        question.setUiHint(UIHint.COMBOBOX);
        question.setConstraints(new IntegerConstraints());
        question.setSurveyCompoundKey("surveyCompoundKey");
        question.setGuid("guid");
        question.setIdentifier("identifier");
        question.setType("SurveyQuestion");
        SurveyRule beforeRule = new SurveyRule.Builder().withDisplayUnless(true).withDataGroups(Sets.newHashSet("foo")).build();
        SurveyRule afterRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        question.setBeforeRules(Lists.newArrayList(beforeRule));
        question.setAfterRules(Lists.newArrayList(afterRule));
    
        SurveyQuestion copy = new DynamoSurveyQuestion(question);
        assertEquals(copy.getPrompt(), "prompt");
        assertEquals(copy.getPromptDetail(), "promptDetail");
        assertTrue(copy.getFireEvent());
        assertEquals(copy.getUiHint(), UIHint.COMBOBOX);
        assertTrue(copy.getConstraints() instanceof IntegerConstraints);
        assertEquals(copy.getSurveyCompoundKey(), "surveyCompoundKey");
        assertEquals(copy.getGuid(), "guid");
        assertEquals(copy.getIdentifier(), "identifier");
        assertEquals(copy.getType(), "SurveyQuestion");
        assertEquals(copy.getBeforeRules().size(), 1);
        assertEquals(copy.getBeforeRules().get(0), question.getBeforeRules().get(0));
        assertEquals(copy.getAfterRules().size(), 1);
        assertEquals(copy.getAfterRules().get(0), question.getAfterRules().get(0));
    }
    
    @Test
    public void canSerialize() throws Exception {
        DateTime date = DateTime.parse("2015-10-10T10:10:10.000Z");
        
        IntegerConstraints c = new IntegerConstraints();
        c.setMinValue(2.0d);
        c.setMaxValue(6.0d);
        c.setStep(2.0d);
        c.setUnit(Unit.DAYS);
        
        DynamoSurveyQuestion question = new DynamoSurveyQuestion();
        question.setPromptDetail("Prompt Detail");
        question.setPrompt("Prompt");
        question.setIdentifier("identifier");
        question.setGuid("AAA");
        question.setOrder(3);
        question.setSurveyKeyComponents("AAA", date.getMillis());
        question.setType("type");
        question.setUiHint(UIHint.CHECKBOX);
        question.setConstraints(c);
        
        String string = BridgeObjectMapper.get().writeValueAsString(question);
        assertEquals(string, "{\"surveyCompoundKey\":\"AAA:1444471810000\",\"guid\":\"AAA\",\"identifier\":\"identifier\",\"type\":\"type\",\"prompt\":\"Prompt\",\"promptDetail\":\"Prompt Detail\",\"fireEvent\":false,\"constraints\":{\"rules\":[],\"dataType\":\"integer\",\"unit\":\"days\",\"minValue\":2.0,\"maxValue\":6.0,\"step\":2.0,\"type\":\"IntegerConstraints\"},\"uiHint\":\"checkbox\"}");

        SurveyQuestion question2 = (SurveyQuestion)SurveyQuestion.fromJson(BridgeObjectMapper.get().readTree(string));
        assertEquals(question2.getPromptDetail(), question.getPromptDetail());
        assertEquals(question2.getPrompt(), question.getPrompt());
        assertEquals(question2.getIdentifier(), question.getIdentifier());
        assertEquals(question2.getGuid(), question.getGuid());
        assertEquals(question2.getType(), question.getType());
        assertEquals(question2.getUiHint(), question.getUiHint());
        
        IntegerConstraints c2 = (IntegerConstraints)question2.getConstraints();
        assertEquals(c2.getMinValue(), c.getMinValue());
        assertEquals(c2.getMaxValue(), c.getMaxValue());
        assertEquals(c2.getStep(), c.getStep());
        assertEquals(c2.getUnit(), c.getUnit());
    }
    
}
