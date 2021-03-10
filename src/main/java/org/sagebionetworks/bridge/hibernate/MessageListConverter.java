package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.schedules2.Message;

public class MessageListConverter extends BaseJsonAttributeConverter<List<Message>> {

    private static final TypeReference<List<Message>> TYPE_REF = new TypeReference<List<Message>>() {};

    @Override
    public String convertToDatabaseColumn(List<Message> list) {
        return serialize(list);
    }
    @Override
    public List<Message> convertToEntityAttribute(String string) {
        return deserialize(string, TYPE_REF);
    }
}
