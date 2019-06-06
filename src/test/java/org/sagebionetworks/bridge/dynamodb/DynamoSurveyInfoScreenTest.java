package org.sagebionetworks.bridge.dynamodb;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import static org.testng.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class DynamoSurveyInfoScreenTest {
    
    @Test
    public void copyConstructor() {
        SurveyInfoScreen screen = SurveyInfoScreen.create();
        screen.setPrompt("prompt");
        screen.setPromptDetail("promptDetail");
        screen.setTitle("title");
        screen.setImage(new Image("sourceUrl", 100, 100));
        screen.setSurveyCompoundKey("surveyCompoundKey");
        screen.setGuid("guid");
        screen.setIdentifier("identifier");
        screen.setType("SurveyInfoScreen");
        SurveyRule beforeRule = new SurveyRule.Builder().withDisplayUnless(true).withDataGroups(Sets.newHashSet("foo")).build();
        SurveyRule afterRule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();
        screen.setBeforeRules(Lists.newArrayList(beforeRule));
        screen.setAfterRules(Lists.newArrayList(afterRule));

        SurveyInfoScreen copy = new DynamoSurveyInfoScreen(screen);
        assertEquals(copy.getPrompt(), "prompt");
        assertEquals(copy.getPromptDetail(), "promptDetail");
        assertEquals(copy.getTitle(), "title");
        assertEquals(copy.getImage(), screen.getImage());
        assertEquals(copy.getSurveyCompoundKey(), "surveyCompoundKey");
        assertEquals(copy.getGuid(), "guid");
        assertEquals(copy.getIdentifier(), "identifier");
        assertEquals(copy.getType(), "SurveyInfoScreen");
        assertEquals(copy.getBeforeRules().size(), 1);
        assertEquals(copy.getBeforeRules().get(0), beforeRule);
        assertEquals(copy.getAfterRules().size(), 1);
        assertEquals(copy.getAfterRules().get(0), afterRule);
    }
}
