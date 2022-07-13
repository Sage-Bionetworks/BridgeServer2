package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicValue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DemographicSerializer extends JsonSerializer<Demographic> {
    @Override
    public void serialize(Demographic value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObjectFieldStart(value.getCategoryName());
        // value(s)
        if (value.isMultipleSelect()) {
            gen.writeArrayFieldStart("values");
            for (DemographicValue demographicValue : value.getValues()) {
                gen.writeString(demographicValue.getValue());
            }
            gen.writeEndArray();
        } else {
            gen.writeFieldName("value");
            gen.writeObject(value.getValues().get(0));
        }
        // units
        if (value.getUnits() != null) {
            gen.writeObjectField("units", value.getUnits());
        }
        // userId
        gen.writeObjectField("userId", value.getUserId());
        gen.writeEndObject();
    }
}
