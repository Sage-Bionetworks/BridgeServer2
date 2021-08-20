package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.studies.SignInType;

public class SignInTypeListConverter extends BaseJsonAttributeConverter<List<SignInType>> {

    private static final TypeReference<List<SignInType>> TYPE_REF = new TypeReference<List<SignInType>>() {};
    
    @Override
    public String convertToDatabaseColumn(List<SignInType> list) {
        return serialize(list);
    }
    @Override
    public List<SignInType> convertToEntityAttribute(String string) {
        return deserialize(string, TYPE_REF);
    }
}
