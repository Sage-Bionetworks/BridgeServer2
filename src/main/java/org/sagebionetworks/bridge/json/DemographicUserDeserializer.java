package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicId;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

// deserializes from assessment format
public class DemographicUserDeserializer extends JsonDeserializer<DemographicUser> {
    private final String MULTIPLE_SELECT_STEP_TYPE = "array";

    @Override
    public DemographicUser deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        DemographicAssessmentResults results = p.readValueAs(DemographicAssessmentResults.class);
        Map<String, Demographic> demographics = new HashMap<>();
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, demographics);
        for (DemographicAssessmentResultStep resultStep : results.getStepHistory()) {
            demographics.put(resultStep.getIdentifier(),
                    new Demographic(new DemographicId(null, resultStep.getIdentifier()), demographicUser,
                            resultStep.getAnswerType().getType().toLowerCase().equals(MULTIPLE_SELECT_STEP_TYPE),
                            resultStep.getValue(), null));
        }
        return demographicUser;
    }

    private class DemographicAssessmentResults {
        private List<DemographicAssessmentResultStep> stepHistory;

        public DemographicAssessmentResults() {
        }

        public DemographicAssessmentResults(List<DemographicAssessmentResultStep> stepHistory) {
            this.stepHistory = stepHistory;
        }

        public List<DemographicAssessmentResultStep> getStepHistory() {
            return stepHistory;
        }

        public void setStepHistory(List<DemographicAssessmentResultStep> stepHistory) {
            this.stepHistory = stepHistory;
        }
    }

    private class DemographicAssessmentResultStep {
        private String identifier;
        private DemographicAssessmentResultStepAnswerType answerType;
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<DemographicValue> value;

        public DemographicAssessmentResultStep() {
        }

        public DemographicAssessmentResultStep(String identifier, DemographicAssessmentResultStepAnswerType answerType,
                List<DemographicValue> value) {
            this.identifier = identifier;
            this.answerType = answerType;
            this.value = value;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public DemographicAssessmentResultStepAnswerType getAnswerType() {
            return answerType;
        }

        public void setAnswerType(DemographicAssessmentResultStepAnswerType answerType) {
            this.answerType = answerType;
        }

        public List<DemographicValue> getValue() {
            return value;
        }

        public void setValue(List<DemographicValue> value) {
            this.value = value;
        }

    }

    private class DemographicAssessmentResultStepAnswerType {
        private String type;
        private String baseType;

        public DemographicAssessmentResultStepAnswerType() {
        }

        public DemographicAssessmentResultStepAnswerType(String type, String baseType) {
            this.type = type;
            this.baseType = baseType;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBaseType() {
            return baseType;
        }

        public void setBaseType(String baseType) {
            this.baseType = baseType;
        }
    }
}
