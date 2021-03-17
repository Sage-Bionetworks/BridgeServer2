package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public abstract class MapMarshaller<S,T> implements DynamoDBTypeConverter<String, Map<S, T>> {

    public abstract TypeReference<Map<S,T>> getTypeReference();

    /** {@inheritDoc} */
    @Override
    public String convert(Map<S,T> map) {
        try {
            return BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public Map<S,T> unconvert(String json) {
        try {
            return BridgeObjectMapper.get().readValue(json, getTypeReference());
        } catch (IOException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }
}