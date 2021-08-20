package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.models.studies.SignInType.EMAIL_MESSAGE;
import static org.sagebionetworks.bridge.models.studies.SignInType.PHONE_PASSWORD;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.SignInType;

public class SignInTypeListConverterTest {

    SignInTypeListConverter converter;
    
    @BeforeMethod
    public void beforeMethod() {
        converter = new SignInTypeListConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        List<SignInType> types = ImmutableList.of(EMAIL_MESSAGE, PHONE_PASSWORD);
        
        String json = converter.convertToDatabaseColumn(types);
        
        assertEquals(json, "[\"email_message\",\"phone_password\"]");
    }
    
    @Test
    public void convertToEntityAttribute() {
        String json = "[\"email_message\",\"phone_password\"]";
        
        List<SignInType> types = converter.convertToEntityAttribute(json);
        assertEquals(types.get(0), EMAIL_MESSAGE);
        assertEquals(types.get(1), PHONE_PASSWORD);
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
