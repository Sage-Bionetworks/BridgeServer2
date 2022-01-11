package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.testng.annotations.Test;

public class AccountRefConverterTest {
    static final AccountRefConverter CONVERTER = new AccountRefConverter();
    static final AccountRef ACCOUNT_REF = new AccountRef("firstName", "lastName", "email", TestConstants.PHONE,
            "synapseUserId", "orgMembership", "identifier", "externalId");
    
    @Test
    public void convertToDatabaseColumn() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(ACCOUNT_REF);
        AccountRef deser = BridgeObjectMapper.get().readValue(json, AccountRef.class);
        
        assertEquals(deser.getIdentifier(), "identifier");
        assertEquals(deser.getPhone(), PHONE);
    }
    
    @Test
    public void convertToDatabaseColumnNull() throws Exception { 
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }

    @Test
    public void convertToEntityAttribute() throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(ACCOUNT_REF);
        
        AccountRef deser = CONVERTER.convertToEntityAttribute(json);
        
        assertEquals(deser.getIdentifier(), "identifier");
        assertEquals(deser.getPhone(), PHONE);
    }
    
    @Test
    public void convertToEntityAttributeNull() throws Exception {
        AccountRef deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void convertToEntityAttributeJsonErrorThrows() throws Exception {
        CONVERTER.convertToEntityAttribute("not json");
    }
}
