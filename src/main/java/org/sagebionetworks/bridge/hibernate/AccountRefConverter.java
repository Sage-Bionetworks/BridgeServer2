package org.sagebionetworks.bridge.hibernate;

import org.sagebionetworks.bridge.models.accounts.AccountRef;

import com.fasterxml.jackson.core.type.TypeReference;

public class AccountRefConverter extends BaseJsonAttributeConverter<AccountRef> {
    private static final TypeReference<AccountRef> TYPE_REF = new TypeReference<AccountRef>() {};
    
    @Override
    public String convertToDatabaseColumn(AccountRef attribute) {
        return serialize(attribute);
    }
    @Override
    public AccountRef convertToEntityAttribute(String json) {
        return deserialize(json, TYPE_REF);
    }
    
}
