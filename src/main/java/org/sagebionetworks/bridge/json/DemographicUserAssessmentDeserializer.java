package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.sagebionetworks.bridge.models.demographics.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Class used to deserialize from the mobile client v2 assessment JSON schema
 * draft 7
 * (https://github.com/Sage-Bionetworks/mobile-client-json/blob/00320defcb5c67873c501b5d99201fed6fdcd0e6/schemas/v2/AssessmentResultObject.json)
 * and convert to the "normal" format.
 */
public class DemographicUserAssessmentDeserializer extends JsonDeserializer<DemographicUserAssessment> {
    @Override
    public DemographicUserAssessment deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        Map<String, Demographic> demographics = new ConcurrentHashMap<>();
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, demographics);
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);

        AssessmentResultObject assessmentResultObject = p.readValueAs(AssessmentResultObject.class);
        if (assessmentResultObject.getStepHistory() != null) {
            for (CollectionResultObject step : assessmentResultObject.getStepHistory()) {
                if (step != null && step.getChildren() != null) {
                    for (AnswerResultObject answer : step.getChildren()) {
                        if (answer != null && answer.getIdentifier() != null && answer.getValue() != null) {
                            JsonNode value = answer.getValue();
                            List<DemographicValue> demographicValues = new ArrayList<>();
                            String categoryName = answer.getIdentifier();
                            Demographic demographic = new Demographic(null, demographicUser, categoryName, true,
                                    demographicValues, null);
                            demographics.put(categoryName, demographic);

                            if (value.isObject()) {
                                Map<String, String> valueMap = BridgeObjectMapper.get()
                                        .readerFor(new TypeReference<Map<String, String>>() {
                                        }).readValue(value);
                                for (Map.Entry<String, String> entry : valueMap.entrySet()) {
                                    demographicValues.add(new DemographicValue(entry.getKey(), entry.getValue()));
                                }
                                demographic.setMultipleSelect(true);
                            } else if (value.isArray()) {
                                List<String> valueArray = BridgeObjectMapper.get()
                                        .readerFor(new TypeReference<List<String>>() {
                                        }).readValue(value);
                                for (String valueString : valueArray) {
                                    demographicValues.add(new DemographicValue(valueString));
                                }
                                demographic.setMultipleSelect(true);
                            } else {
                                DemographicValue singleValue = BridgeObjectMapper.get().treeToValue(value,
                                        DemographicValue.class);
                                if (singleValue == null) {
                                    // if it's null store the string null
                                    singleValue = new DemographicValue((String) null);
                                }
                                demographicValues.add(singleValue);
                                demographic.setMultipleSelect(false);
                            }
                            if (answer.getAnswerType() != null && answer.getAnswerType().getUnit() != null) {
                                demographic.setUnits(answer.getAnswerType().getUnit());
                            }
                        }
                    }
                }
            }
        }

        return demographicUserAssessment;
    }

    public static class AssessmentResultObject {
        private List<CollectionResultObject> stepHistory;

        public List<CollectionResultObject> getStepHistory() {
            return stepHistory;
        }

        public void setStepHistory(List<CollectionResultObject> stepHistory) {
            this.stepHistory = stepHistory;
        }
    }

    public static class CollectionResultObject {
        private List<AnswerResultObject> children;

        public List<AnswerResultObject> getChildren() {
            return children;
        }

        public void setChildren(List<AnswerResultObject> children) {
            this.children = children;
        }
    }

    public static class AnswerResultObject {
        private AnswerTypeMeasurement answerType;
        private String identifier;
        private JsonNode value;

        public AnswerTypeMeasurement getAnswerType() {
            return answerType;
        }

        public void setAnswerType(AnswerTypeMeasurement answerType) {
            this.answerType = answerType;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public JsonNode getValue() {
            return value;
        }

        public void setValue(JsonNode value) {
            this.value = value;
        }
    }

    public static class AnswerTypeMeasurement {
        private String unit;

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }
    }
}
