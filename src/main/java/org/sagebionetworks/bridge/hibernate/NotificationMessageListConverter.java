package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class NotificationMessageListConverter extends BaseJsonAttributeConverter<List<NotificationMessage>> {

    private static final TypeReference<List<NotificationMessage>> TYPE_REF = 
            new TypeReference<List<NotificationMessage>>() {};

    @Override
    public String convertToDatabaseColumn(List<NotificationMessage> list) {
        return serialize(list);
    }
    @Override
    public List<NotificationMessage> convertToEntityAttribute(String string) {
        return deserialize(string, TYPE_REF);
    }
}
