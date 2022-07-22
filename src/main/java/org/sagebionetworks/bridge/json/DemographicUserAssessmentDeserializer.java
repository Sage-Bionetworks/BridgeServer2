package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.studies.DemographicValue;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

// deserializes from assessment format
public class DemographicUserAssessmentDeserializer extends JsonDeserializer<DemographicUserAssessment> {
    private final String MULTIPLE_SELECT_STEP_TYPE = "array";

    @Override
    public DemographicUserAssessment deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        DemographicAssessmentResults results = p.readValueAs(DemographicAssessmentResults.class);
        Map<String, Demographic> demographics = new ConcurrentHashMap<>();
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, demographics);
        if (results.getStepHistory() != null) {
            for (DemographicAssessmentResultStep resultStep : results.getStepHistory()) {
                if (resultStep == null) {
                    continue;
                }
                if (resultStep.getValue() != null) {
                    // replace null with DemographicValue(null)
                    for (ListIterator<DemographicValue> iter = resultStep.getValue().listIterator(); iter.hasNext();) {
                        if (iter.next() == null) {
                            iter.set(new DemographicValue(null));
                        }
                    }
                }
                if (resultStep.getAnswerType() == null) {
                    throw new JsonMappingException(p, "answerType containing type must be included");
                }
                demographics.put(resultStep.getIdentifier(),
                        new Demographic(null, demographicUser, resultStep.getIdentifier(),
                                resultStep.getAnswerType().equalsIgnoreCase(MULTIPLE_SELECT_STEP_TYPE),
                                resultStep.getValue(), null));
            }
        }
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        return demographicUserAssessment;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DemographicAssessmentResults {
        private List<DemographicAssessmentResultStep> stepHistory;

        public List<DemographicAssessmentResultStep> getStepHistory() {
            return stepHistory;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DemographicAssessmentResultStep {
        private String identifier;
        private String answerType;
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<DemographicValue> value;

        public String getIdentifier() {
            return identifier;
        }

        public String getAnswerType() {
            return answerType;
        }

        @JsonProperty("answerType")
        public void setAnswerType(Map<String, String> answerType) {
            this.answerType = answerType.get("type");
        }

        public List<DemographicValue> getValue() {
            return value;
        }
    }
}
