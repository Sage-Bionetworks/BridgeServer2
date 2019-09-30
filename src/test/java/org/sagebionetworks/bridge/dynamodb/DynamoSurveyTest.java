package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.DateConstraints;
import org.sagebionetworks.bridge.models.surveys.DateTimeConstraints;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.IntegerConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyRule;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

public class DynamoSurveyTest {
    private static final String MODULE_ID = "test-survey-module";
    private static final int MODULE_VERSION = 3;
    private static final long TEST_CREATED_ON_MILLIS = DateTime.parse("2015-05-22T18:34-0700").getMillis();
    private static final long TEST_MODIFIED_ON_MILLIS = DateTime.parse("2015-05-22T18:57-0700").getMillis();
    private static final String TEST_COPYRIGHT_NOTICE = "© 2017 Sage";

    /**
     * Tests of the serialization and deserialization of all the data
     * to/from JSON. This is complicated for surveys as we change their
     * representation from the public API to the way they are stored in
     * Dynamo.
     */
    @Test
    public void jsonSerialization() throws Exception {
        Survey survey = makeTestSurvey();
        
        SurveyRule rule = new SurveyRule.Builder().withOperator(Operator.ALWAYS).withEndSurvey(true).build();

        // add an info screen for completeness
        SurveyInfoScreen screen = SurveyInfoScreen.create();
        screen.setGuid("test-info-screen-guid");
        screen.setIdentifier("screenA");
        screen.setTitle("The title of the screen");
        screen.setPrompt("This is the prompt");
        screen.setPromptDetail("This is further explanation of the prompt.");
        screen.setImage(new Image("http://foo.bar", 100, 100));
        screen.setAfterRules(Lists.newArrayList(rule));
        survey.getElements().add(screen);
        
        // and add a rule as well to a survey question
        SurveyElement dateQuestion = (SurveyElement)TestSurvey.selectBy(survey, DataType.DATE);
        dateQuestion.setAfterRules(Lists.newArrayList(rule));

        // Convert to JSON.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(survey, JsonNode.class);

        // Convert JSON to map to validate JSON. Note that study ID is intentionally omitted, but type is added.
        assertEquals(jsonNode.size(), 14);
        assertEquals(jsonNode.get("guid").textValue(), "test-survey-guid");
        assertEquals(jsonNode.get("version").intValue(), 2);
        assertEquals(jsonNode.get("copyrightNotice").textValue(), TEST_COPYRIGHT_NOTICE);
        assertEquals(jsonNode.get("moduleId").textValue(), MODULE_ID);
        assertEquals(jsonNode.get("moduleVersion").intValue(), MODULE_VERSION);
        assertEquals(jsonNode.get("name").textValue(), survey.getName());
        assertEquals(jsonNode.get("identifier").textValue(), survey.getIdentifier());
        assertTrue(jsonNode.get("published").booleanValue());
        assertTrue(jsonNode.get("deleted").booleanValue());
        assertEquals(jsonNode.get("schemaRevision").intValue(), 42);
        assertEquals(jsonNode.get("type").textValue(), "Survey");

        // Timestamps are stored as long, but serialized as ISO timestamps. Convert them back to long millis so we
        // don't have to deal with timezones and formatting issues.
        assertEquals(DateTime.parse(jsonNode.get("createdOn").textValue()).getMillis(), TEST_CREATED_ON_MILLIS);
        assertEquals(DateTime.parse(jsonNode.get("modifiedOn").textValue()).getMillis(), TEST_MODIFIED_ON_MILLIS);

        // Just test that we have the right number of elements. In-depth serialization testing is done by
        // SurveyElementTest
        JsonNode jsonElementList = jsonNode.get("elements");
        assertEquals(jsonElementList.size(), 13);

        // Convert back to POJO and validate. Note that study ID is still missing, since it was removed from the JSON.
        Survey convertedSurvey = BridgeObjectMapper.get().convertValue(jsonNode, Survey.class);
        assertNull(convertedSurvey.getStudyIdentifier());
        assertEquals(convertedSurvey.getGuid(), "test-survey-guid");
        assertEquals(convertedSurvey.getCreatedOn(), TEST_CREATED_ON_MILLIS);
        assertEquals(convertedSurvey.getModifiedOn(), TEST_MODIFIED_ON_MILLIS);
        assertEquals(convertedSurvey.getCopyrightNotice(), TEST_COPYRIGHT_NOTICE);
        assertEquals(convertedSurvey.getModuleId(), MODULE_ID);
        assertEquals(convertedSurvey.getModuleVersion().intValue(), MODULE_VERSION);
        assertEquals(convertedSurvey.getVersion().longValue(), 2);
        assertEquals(convertedSurvey.getName(), survey.getName());
        assertEquals(convertedSurvey.getIdentifier(), survey.getIdentifier());
        assertTrue(convertedSurvey.isPublished());
        assertEquals(convertedSurvey.getSchemaRevision().longValue(), 42);
        assertEquals(convertedSurvey.getElements().size(), 13);
        for (int i = 0; i < 13; i++) {
            assertEqualsSurveyElement(survey.getElements().get(i), convertedSurvey.getElements().get(i));
        }

        // There are 11 survey elements, but only the first 10 are questions.
        assertEquals(convertedSurvey.getUnmodifiableQuestionList().size(), 12);
        for (int i = 0; i < 12; i++) {
            assertEqualsSurveyElement(convertedSurvey.getElements().get(i),
                    convertedSurvey.getUnmodifiableQuestionList().get(i));
        }

        // validate that date constraints are persisted
        SurveyQuestion convertedDateQuestion = (SurveyQuestion)TestSurvey.selectBy(convertedSurvey, DataType.DATE);
        DateConstraints dc = (DateConstraints)convertedDateQuestion.getConstraints();
        assertNotNull(dc.getEarliestValue(), "Earliest date exists");
        assertNotNull(dc.getLatestValue(), "Latest date exists");
        assertEquals(convertedDateQuestion.getAfterRules().get(0), rule);

        DateTimeConstraints dtc = (DateTimeConstraints) TestSurvey.selectBy(convertedSurvey, DataType.DATETIME).getConstraints();
        assertNotNull(dtc.getEarliestValue(), "Earliest date exists");
        assertNotNull(dtc.getLatestValue(), "Latest date exists");
        
        IntegerConstraints ic = (IntegerConstraints) TestSurvey.selectBy(convertedSurvey, DataType.INTEGER).getConstraints();
        assertEquals(ic.getRules().get(0).getOperator(), SurveyRule.Operator.LE);
        assertEquals(ic.getRules().get(0).getValue(), 2);
        assertEquals(ic.getRules().get(0).getSkipToTarget(), "name");
        
        assertEquals(ic.getRules().get(1).getOperator(), SurveyRule.Operator.DE);
        assertEquals(ic.getRules().get(1).getSkipToTarget(), "name");
        
        SurveyInfoScreen retrievedScreen = (SurveyInfoScreen)convertedSurvey.getElements().get(convertedSurvey.getElements().size()-1);
        assertEquals(retrievedScreen.getAfterRules().get(0), rule);
    }

    @Test
    public void copyConstructor() {
        // copy
        DynamoSurvey survey = makeTestSurvey();
        Survey copy = new DynamoSurvey(survey);

        // validate
        assertEquals(copy.getStudyIdentifier(), TEST_STUDY_IDENTIFIER);
        assertEquals(copy.getGuid(), "test-survey-guid");
        assertEquals(copy.getCreatedOn(), TEST_CREATED_ON_MILLIS);
        assertEquals(copy.getModifiedOn(), TEST_MODIFIED_ON_MILLIS);
        assertEquals(copy.getCopyrightNotice(), TEST_COPYRIGHT_NOTICE);
        assertEquals(copy.getModuleId(), MODULE_ID);
        assertEquals(copy.getModuleVersion().intValue(), MODULE_VERSION);
        assertEquals(copy.getVersion().longValue(), 2);
        assertEquals(copy.getName(), survey.getName());
        assertEquals(copy.getIdentifier(), survey.getIdentifier());
        assertTrue(copy.isPublished());
        assertEquals(copy.getSchemaRevision().longValue(), 42);
        assertEquals(copy.getElements().size(), 12);
        for (int i = 0; i < 12; i++) {
            assertEqualsSurveyElement(survey.getElements().get(i), copy.getElements().get(i));
        }
    }
    
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(DynamoSurvey.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void testToString() {
        Survey survey = makeTestSurvey();
        String surveyString = survey.toString();
        assertTrue(surveyString.contains("createdOn=" + TEST_CREATED_ON_MILLIS));
        assertTrue(surveyString.contains("guid=test-survey-guid"));
        assertTrue(surveyString.contains(TEST_COPYRIGHT_NOTICE));
        assertTrue(surveyString.contains("moduleId=" + MODULE_ID));
        assertTrue(surveyString.contains("moduleVersion=" + MODULE_VERSION));
    }

    private static void assertEqualsSurveyElement(SurveyElement expected, SurveyElement actual) {
        // Test Survey has anonymous subclasses, so we can't use .equals(). SurveyElementTest already tests survey
        // elements, so here, just make sure they both derive from the same class (SurveyInfoScreen vs SurveyQuestion)
        // and they have the same ID.
        assertTrue((expected instanceof DynamoSurveyQuestion && actual instanceof DynamoSurveyQuestion)
                || (expected instanceof DynamoSurveyInfoScreen && actual instanceof DynamoSurveyInfoScreen));
        assertEquals(actual.getIdentifier(), expected.getIdentifier());
    }

    private static DynamoSurvey makeTestSurvey() {
        // Make survey. Modify a few fields to make testing easier.
        DynamoSurvey survey = new TestSurvey(DynamoSurveyTest.class, false);
        survey.setGuid("test-survey-guid");
        survey.setCreatedOn(TEST_CREATED_ON_MILLIS);
        survey.setModifiedOn(TEST_MODIFIED_ON_MILLIS);
        survey.setCopyrightNotice(TEST_COPYRIGHT_NOTICE);
        survey.setModuleId(MODULE_ID);
        survey.setModuleVersion(MODULE_VERSION);
        survey.setPublished(true);
        survey.setDeleted(true);
        return survey;
    }
}
