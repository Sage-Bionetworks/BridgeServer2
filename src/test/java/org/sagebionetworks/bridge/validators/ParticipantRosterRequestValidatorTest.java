package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ParticipantRosterRequest;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

public class ParticipantRosterRequestValidatorTest {
    public static final String PASSWORD = "P@ssword1";
    public static final String BLANK_PASSWORD = "";
    public static final String TOO_SHORT_PASSWORD = "short";
    public static final String NO_NUM_PASSWORD = "P@ssword";
    public static final String NO_SYMBOL_PASSWORD = "Password1";
    public static final String NO_LOWER_CASE_PASSWORD = "P@SSWORD1";
    public static final String NO_UPPER_CASE_PASSWORD = "p@ssword1";
    public static final String STUDY_ID = "test-study-id";

    @Test
    public void validPasswordNoStudyId() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + PASSWORD + "\",\n" +
                "   \"studyId\":\"\"\n" +
                "}");
        Validate.entityThrowingException(ParticipantRosterRequestValidator.INSTANCE, request);
    }

    @Test
    public void validPasswordAndStudyId() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        Validate.entityThrowingException(ParticipantRosterRequestValidator.INSTANCE, request);
    }

    @Test
    public void validNoSymbolPassword() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + NO_SYMBOL_PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        Validate.entityThrowingException(ParticipantRosterRequestValidator.INSTANCE, request);
    }

    @Test
    public void blankPassword() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + BLANK_PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        assertValidatorMessage(ParticipantRosterRequestValidator.INSTANCE, request, "password", "cannot be null or blank");
    }

    @Test
    public void tooShortPassword() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + TOO_SHORT_PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        assertValidatorMessage(ParticipantRosterRequestValidator.INSTANCE, request, "password", "must be at least 8 characters");
    }

    @Test
    public void nonNumericPassword() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + NO_NUM_PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        assertValidatorMessage(ParticipantRosterRequestValidator.INSTANCE, request, "password", "must contain at least one number (0-9)");
    }

    @Test
    public void noLowerCasePassword() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + NO_LOWER_CASE_PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        assertValidatorMessage(ParticipantRosterRequestValidator.INSTANCE, request, "password", "must contain at least one lowercase letter (a-z)");
    }

    @Test
    public void noUpperCasePassword() throws Exception {
        ParticipantRosterRequest request = createRequest("{\n" +
                "   \"password\":\"" + NO_UPPER_CASE_PASSWORD + "\",\n" +
                "   \"studyId\":\"" + STUDY_ID + "\"\n" +
                "}");
        assertValidatorMessage(ParticipantRosterRequestValidator.INSTANCE, request, "password", "must contain at least one uppercase letter (A-Z)");
    }

    private ParticipantRosterRequest createRequest(String json) throws Exception {
        return BridgeObjectMapper.get().readValue(TestUtils.createJson(json), ParticipantRosterRequest.class);
    }
}