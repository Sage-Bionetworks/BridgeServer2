package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.assessments.ColorScheme;

public class ColorSchemeConverterTest extends Mockito {

    ColorSchemeConverter converter;
    
    @BeforeMethod
    public void beforeMethod() {
        converter = new ColorSchemeConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        ColorScheme scheme = new ColorScheme("#FFFFFF", null, "#eee", "#111111");
        
        String json = converter.convertToDatabaseColumn(scheme);
        
        assertEquals(json, "{\"background\":\"#FFFFFF\",\"activated\":\"#eee\","
                +"\"inactivated\":\"#111111\",\"type\":\"ColorScheme\"}");
    }
    
    @Test
    public void convertToEntityAttribute() {
        String json = "{\"background\":\"#FFFFFF\",\"activated\":\"#eee\","
                +"\"inactivated\":\"#111111\",\"type\":\"ColorScheme\"}";
        
        ColorScheme scheme = converter.convertToEntityAttribute(json);
        assertEquals(scheme.getBackground(), "#FFFFFF");
        assertNull(scheme.getForeground());
        assertEquals(scheme.getActivated(), "#eee");
        assertEquals(scheme.getInactivated(), "#111111");
    }
    
    @Test
    public void convertToDatabaseColumn_handlesNull() {
        assertNull( converter.convertToDatabaseColumn(null) );
    }
    
    @Test
    public void convertToEntityAttribute_handlesNull() {
        assertNull( converter.convertToEntityAttribute(null) );
    }
}
