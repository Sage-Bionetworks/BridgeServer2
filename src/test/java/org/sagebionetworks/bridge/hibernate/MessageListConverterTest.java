package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.MESSAGES;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.Message;

public class MessageListConverterTest {

    private String messagesJson;
    
    @BeforeMethod
    public void before() throws Exception {
        messagesJson = BridgeObjectMapper.get().writeValueAsString(MESSAGES);
    }
    
    @Test
    public void convertToDatabaseColumn() {
        MessageListConverter converter = new MessageListConverter();
        
        String retValue = converter.convertToDatabaseColumn(MESSAGES);
        assertEquals(retValue, messagesJson);
    }
    
    @Test
    public void convertToEntityAttribute() {
        MessageListConverter converter = new MessageListConverter();

        List<Message> retValue = converter.convertToEntityAttribute(messagesJson);
        assertEquals(retValue.size(), MESSAGES.size());
    }
    
    @Test
    public void handlesBlanks() {
        MessageListConverter converter = new MessageListConverter();
        assertEquals(converter.convertToDatabaseColumn(ImmutableList.of()), "[]");
        assertEquals(converter.convertToEntityAttribute(""), null);
        assertEquals(converter.convertToEntityAttribute("[]"), ImmutableList.of());
    }
    
    @Test
    public void handlesNulls() {
        MessageListConverter converter = new MessageListConverter();
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
