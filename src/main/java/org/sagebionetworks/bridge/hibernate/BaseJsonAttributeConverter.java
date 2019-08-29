package org.sagebionetworks.bridge.hibernate;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;

import javax.persistence.AttributeConverter;
import javax.persistence.PersistenceException;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * Due to the way type erasure works with generics, we can't directly implement
 * AttributeConverter's methods with genericized types, so they are implemented in subclasses 
 * that defer implementation to this base class. All types are persisted as JSON in a string 
 * column of the database, with all the limitations of JSON.
 * 
 * This converter uses the BridgeObjectMapper, which has some desirable configured 
 * behavior (it ignores properties it doesn't recognize, does not include null properties 
 * in output, etc).
 * 
 * @param <X> the type of the field being persisted (should be the same as the Java type of the 
 *  field on the object being persisted).
 */
public abstract class BaseJsonAttributeConverter<X> implements AttributeConverter<X,String> {
    protected String serialize(X object) {
        if (object == null) {
            return null;
        }
        try {
            return BridgeObjectMapper.get().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new DynamoDBMappingException(e);
        }
    }
    public X deserialize(String ser, TypeReference<X> typeRef) {
        if (isBlank(ser)) {
            return null;
        }
        try {
            return BridgeObjectMapper.get().readValue(ser, typeRef);
        } catch (IOException e) {
            throw new PersistenceException(e);
        }
    }    
}
