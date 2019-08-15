package org.sagebionetworks.bridge.hibernate;

import java.io.IOException;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;

@Converter
public class ClientInfoConverter implements AttributeConverter<ClientInfo, String> {
    private static final Logger LOG = LoggerFactory.getLogger(ClientInfoConverter.class);

    @Override
    public String convertToDatabaseColumn(ClientInfo clientInfo) {
        if (clientInfo != null) {
            try {
                return BridgeObjectMapper.get().writeValueAsString(clientInfo);
            } catch (JsonProcessingException e) {
                LOG.debug("Error serializing clientInfo", e);
            }
        }
        return null;
    }

    @Override
    public ClientInfo convertToEntityAttribute(String value) {
        if (StringUtils.isNotBlank(value)) {
            try {
                return BridgeObjectMapper.get().readValue(value, ClientInfo.class);
            } catch (IOException e) {
                LOG.debug("Error deserializing clientInfo", e);
            }
        }
        return null;
    }

}
