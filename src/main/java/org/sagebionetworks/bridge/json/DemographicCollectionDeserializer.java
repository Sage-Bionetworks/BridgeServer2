package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicCollection;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class DemographicCollectionDeserializer extends JsonDeserializer<DemographicCollection> {

    @Override
    public DemographicCollection deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        Map<String, Demographic> demographics = p.readValueAs(new TypeReference<Map<String, Demographic>>() {
        });
        for (Map.Entry<String, Demographic> entry : demographics.entrySet()) {
            entry.getValue().setCategoryName(entry.getKey());
        }
        return new DemographicCollection(new ArrayList<>(demographics.values()));
    }
}
