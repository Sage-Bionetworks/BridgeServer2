package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.studies.DemographicValue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Class used to deserialize from the mobile client v2 assessment JSON format
 * and convert to the "normal" format. Throws JsonMappingException when the
 * types are incorrect, but generally allows empty values.
 */
public class DemographicUserAssessmentDeserializer extends JsonDeserializer<DemographicUserAssessment> {
    @Override
    public DemographicUserAssessment deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        Map<String, Demographic> demographics = new ConcurrentHashMap<>();
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, demographics);
        DemographicUserAssessment demographicUserAssessment = new DemographicUserAssessment(demographicUser);
        JsonNode tree = p.readValueAsTree();
        if (!tree.isObject()) {
            throw new JsonMappingException(p, "assessment should be an object");
        }
        if (!tree.hasNonNull("stepHistory")) {
            return demographicUserAssessment;
        }
        JsonNode stepHistory = tree.get("stepHistory");
        if (!stepHistory.isArray()) {
            throw new JsonMappingException(p, "stepHistory field value should be an array");
        }
        JsonNode resultCollection = null;
        for (JsonNode element : stepHistory) {
            if (element.hasNonNull("children")) {
                resultCollection = element.get("children");
            }
        }
        if (resultCollection == null) {
            return demographicUserAssessment;
        }
        if (!resultCollection.isArray()) {
            throw new JsonMappingException(p, "children field value should be an array");
        }
        for (JsonNode answer : resultCollection) {
            List<DemographicValue> demographicValues = new ArrayList<>();
            Demographic demographic = new Demographic(null, demographicUser, null, true, demographicValues, null);
            if (!answer.isObject()) {
                throw new JsonMappingException(p, "each element in array children should be an object");
            }

            // get units from answerType
            JsonNode answerType = answer.get("answerType");
            if (answerType != null && answerType.hasNonNull("unit") && answerType.get("unit").isTextual()) {
                demographic.setUnits(answerType.get("unit").textValue());
            }

            // get identifier (categoryName)
            if (!answer.hasNonNull("identifier")) {
                throw new JsonMappingException(p,
                        "each object in array children should have non-null field named identifier");
            }
            JsonNode identifier = answer.get("identifier");
            if (!identifier.isTextual()) {
                throw new JsonMappingException(p, "field identifier should have value of type string");
            }
            String categoryName = identifier.textValue();
            demographic.setCategoryName(categoryName);

            // get value
            if (!answer.hasNonNull("value")) {
                throw new JsonMappingException(p,
                        "each object in array children should have non-null field named value");
            }
            JsonNode value = answer.get("value");
            if (value.isObject()) {
                for (Iterator<Map.Entry<String, JsonNode>> iter = value.fields(); iter.hasNext();) {
                    Map.Entry<String, JsonNode> entry = iter.next();
                    if (!entry.getValue().isValueNode()) {
                        throw new JsonMappingException(p,
                                "when value is type object, none of its values should be container types");
                    }
                    demographicValues.add(new DemographicValue(entry.getKey(), valueNodeToString(entry.getValue())));
                }
                demographic.setMultipleSelect(true);
            } else if (value.isArray()) {
                for (JsonNode element : value) {
                    if (!element.isValueNode()) {
                        throw new JsonMappingException(p,
                                "when value is type array, none of its elements should be container types");
                    }
                    demographicValues.add(new DemographicValue(valueNodeToString(element)));
                }
                demographic.setMultipleSelect(true);
            } else {
                demographicValues.add(new DemographicValue(valueNodeToString(value)));
                demographic.setMultipleSelect(false);
            }

            demographics.put(categoryName, demographic);
        }
        return demographicUserAssessment;
    }

    private String valueNodeToString(JsonNode value) throws JsonProcessingException {
        if (value.isTextual()) {
            // no quotations around the string
            return value.textValue();
        } else {
            return BridgeObjectMapper.get().writeValueAsString(value);
        }
    }
}
