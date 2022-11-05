package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Converter;

import org.sagebionetworks.bridge.models.Label;

import com.fasterxml.jackson.core.type.TypeReference;

@Converter
public class LabelConverter extends BaseJsonAttributeConverter<Label> {

    private static final TypeReference<Label> TYPE_REF = new TypeReference<Label>() {
    };

    @Override
    public String convertToDatabaseColumn(Label list) {
        return super.serialize(list);
    }

    @Override
    public Label convertToEntityAttribute(String string) {
        return super.deserialize(string, TYPE_REF);
    }
}
