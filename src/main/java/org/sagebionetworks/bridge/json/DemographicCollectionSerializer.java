package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicCollection;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DemographicCollectionSerializer extends JsonSerializer<DemographicCollection> {

    @Override
    public void serialize(DemographicCollection value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        for (Demographic demographic : value.getDemographics()) {
            gen.writeObjectField(demographic.getCategoryName(), demographic);
        }
        gen.writeEndObject();
    }
}
