package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Label;

public class LabelListConverterTest extends Mockito {

    private String labelsJson;
    
    @BeforeMethod
    public void before() throws Exception {
        labelsJson = BridgeObjectMapper.get().writeValueAsString(LABELS);
    }
    
    @Test
    public void convertToDatabaseColumn() {
        LabelListConverter converter = new LabelListConverter();
        
        String retValue = converter.convertToDatabaseColumn(LABELS);
        assertEquals(retValue, labelsJson);
    }
    
    @Test
    public void convertToEntityAttribute() {
        LabelListConverter converter = new LabelListConverter();

        List<Label> retValue = converter.convertToEntityAttribute(labelsJson);
        assertEquals(retValue.size(), LABELS.size());
    }
    
    @Test
    public void handlesBlanks() {
        LabelListConverter converter = new LabelListConverter();
        assertEquals(converter.convertToDatabaseColumn(ImmutableList.of()), "[]");
        assertEquals(converter.convertToEntityAttribute(""), null);
        assertEquals(converter.convertToEntityAttribute("[]"), ImmutableList.of());
    }
    
    @Test
    public void handlesNulls() {
        LabelListConverter converter = new LabelListConverter();
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
