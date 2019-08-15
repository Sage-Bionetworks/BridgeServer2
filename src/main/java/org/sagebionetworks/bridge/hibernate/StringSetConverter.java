package org.sagebionetworks.bridge.hibernate;

import java.io.IOException;
import java.util.Set;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

@Converter
public class StringSetConverter implements AttributeConverter<Set<String>, String> {
    private static final Logger LOG = LoggerFactory.getLogger(StringSetConverter.class);
    private static final TypeReference<Set<String>> STRING_SET_TYPE_REF = new TypeReference<Set<String>>() {};

    @Override
    public String convertToDatabaseColumn(Set<String> set) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(set);
        } catch (JsonProcessingException e) {
            LOG.debug("Error serializing String set", e);
        }
        return null;        
    }

    @Override
    public Set<String> convertToEntityAttribute(String value) {
        try {
            return BridgeObjectMapper.get().readValue(value, STRING_SET_TYPE_REF);
        } catch (IOException e) {
            LOG.debug("Error deserializing String set", e);
        }
        return null;
    }
}
