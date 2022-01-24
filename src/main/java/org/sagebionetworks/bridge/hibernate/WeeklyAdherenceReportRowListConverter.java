package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;

import com.fasterxml.jackson.core.type.TypeReference;

public class WeeklyAdherenceReportRowListConverter extends BaseJsonAttributeConverter<List<WeeklyAdherenceReportRow>> {
    private static final TypeReference<List<WeeklyAdherenceReportRow>> TYPE_REFERENCE = 
            new TypeReference<List<WeeklyAdherenceReportRow>>() {};
    
    @Override
    public String convertToDatabaseColumn(List<WeeklyAdherenceReportRow> attribute) {
        return serialize(attribute);
    }

    @Override
    public List<WeeklyAdherenceReportRow> convertToEntityAttribute(String json) {
        return deserialize(json, TYPE_REFERENCE);
    }

}
