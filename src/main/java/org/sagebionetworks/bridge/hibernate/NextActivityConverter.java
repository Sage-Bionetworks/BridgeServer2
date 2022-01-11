package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Converter;

import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.NextActivity;

import com.fasterxml.jackson.core.type.TypeReference;

@Converter
public class NextActivityConverter extends BaseJsonAttributeConverter<NextActivity> {

    private static final TypeReference<NextActivity> CONVERTER = new TypeReference<NextActivity>() {};
    
    @Override
    public String convertToDatabaseColumn(NextActivity attribute) {
        return super.serialize(attribute);
    }

    @Override
    public NextActivity convertToEntityAttribute(String json) {
        return super.deserialize(json, CONVERTER);
    }


}
