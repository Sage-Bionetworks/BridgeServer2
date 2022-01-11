package org.sagebionetworks.bridge.hibernate;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;

import com.fasterxml.jackson.core.type.TypeReference;

public class EventStreamConverter extends BaseJsonAttributeConverter<Map<Integer, List<EventStreamDay>>> {

    private static final TypeReference<Map<Integer, List<EventStreamDay>>> CONVERTER = 
            new TypeReference<Map<Integer, List<EventStreamDay>>>() {};

    @Override
    public String convertToDatabaseColumn(Map<Integer, List<EventStreamDay>> attribute) {
        return super.serialize(attribute);
    }

    @Override
    public Map<Integer, List<EventStreamDay>> convertToEntityAttribute(String json) {
        return super.deserialize(json, CONVERTER);
    }
}
