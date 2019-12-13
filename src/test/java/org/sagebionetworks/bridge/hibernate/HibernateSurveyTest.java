package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyElement;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.testng.annotations.Test;

import com.newrelic.agent.deps.com.google.common.collect.ImmutableList;

public class HibernateSurveyTest {
    @Test
    public void elements() {
        HibernateSurvey survey = new HibernateSurvey();
        
        List<SurveyElement> elements = new ArrayList<>();
        elements.add(new HibernateSurveyElement());
        elements.add(new HibernateSurveyElement());
        survey.setElements(elements);
        
        List<SurveyElement> getElements = survey.getElements();
        assertEquals(getElements.size(), 2);
    }
    
//    @Test
//    public void canSerialize() {
//        HibernateSurvey survey = new HibernateSurvey();
//        survey.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
//        HibernateSurveyElement element = new HibernateSurveyElement();
//        survey.getElements().add(element);
//        
//        
//    }
}
