package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.ClientInfo;

@Converter
public class ClientInfoConverter extends BaseJsonAttributeConverter<ClientInfo> {
    private static final TypeReference<ClientInfo> TYPE_REFERENCE = new TypeReference<ClientInfo>() {};
    @Override
    public String convertToDatabaseColumn(ClientInfo info) {
        return serialize(info);
    }
    @Override
    public ClientInfo convertToEntityAttribute(String value) {
        return deserialize(value, TYPE_REFERENCE);
    }
}
