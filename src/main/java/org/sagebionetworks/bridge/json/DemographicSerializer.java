package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.Demographic;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DemographicSerializer extends JsonSerializer<Demographic> {
    @Override
    public void serialize(Demographic value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    }
}
