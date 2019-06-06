package org.sagebionetworks.bridge.models.surveys;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SurveyQuestionOptionTest {
    private static final Image DUMMY_IMAGE = new Image("dummy-source", 42, 42);

    @Test
    public void allValues() {
        SurveyQuestionOption option = new SurveyQuestionOption("test-label", "test-detail", "test-value", DUMMY_IMAGE);
        assertEquals(option.getLabel(), "test-label");
        assertEquals(option.getDetail(), "test-detail");
        assertEquals(option.getValue(), "test-value");
        assertEquals(option.getImage(), DUMMY_IMAGE);

        String optionString = option.toString();
        assertTrue(optionString.contains(option.getLabel()));
        assertTrue(optionString.contains(option.getDetail()));
        assertTrue(optionString.contains(option.getValue()));
        assertTrue(optionString.contains(option.getImage().toString()));
    }

    @Test
    public void blankValue() {
        String[] testCaseArr = { null, "", "   " };
        for (String oneTestCase : testCaseArr) {
            SurveyQuestionOption option = new SurveyQuestionOption("test-label", null, oneTestCase, null);
            assertEquals(option.getValue(), "test-label");
        }
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(SurveyQuestionOption.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void toStringAllNulls() {
        // Make sure toString() doesn't throw if all fields are null.
        SurveyQuestionOption option = new SurveyQuestionOption(null, null, null, null);
        assertNotNull(option.toString());
    }
}
