package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * Generic JSON marshaller for DynamoDB. This converts the object into JSON for marshalling to DynamoDB. Because
 * DynamoDB annotations don't work with generics, you'll need to subclass this and fill in getConvertedClass().
 */
public abstract class JsonMarshaller<T> implements DynamoDBTypeConverter<String, T> {
    /**
     * Returns the class for Jackson to deserialize the value from DynamoDB, because Java can't infer generic types at
     * runtime.
     */
    public abstract Class<T> getConvertedClass();

    @Override
    public String convert(T t) {
        if (t == null) {
            return null;
        }
        try {
            return BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(t);
        } catch (JsonProcessingException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }

    @Override
    public T unconvert(String json) {
        if (json == null) {
            return null;
        }
        try {
            return BridgeObjectMapper.get().readValue(json, getConvertedClass());
        } catch (IOException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }
}
