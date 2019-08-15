package org.sagebionetworks.bridge.hibernate;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.List;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
    private static final Logger LOG = LoggerFactory.getLogger(StringListConverter.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE_REF = new TypeReference<List<String>>() {};

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list != null) {
            try {
                return BridgeObjectMapper.get().writeValueAsString(list);
            } catch (JsonProcessingException e) {
                LOG.debug("Error serializing String list", e);
            }
        }
        return null;        
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (isNotBlank(value)) {
            try {
                return BridgeObjectMapper.get().readValue(value, STRING_LIST_TYPE_REF);
            } catch (IOException e) {
                LOG.debug("Error deserializing String set", e);
            }
        }
        return null;
    }
}
