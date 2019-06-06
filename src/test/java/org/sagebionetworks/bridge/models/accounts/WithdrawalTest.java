package org.sagebionetworks.bridge.models.accounts;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class WithdrawalTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Withdrawal.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        String json = "{\"reason\":\"reasons\"}";
        Withdrawal withdrawal = BridgeObjectMapper.get().readValue(json, Withdrawal.class);
        assertEquals(withdrawal.getReason(), "reasons");
        
        json = "{\"reason\":null}";
        withdrawal = BridgeObjectMapper.get().readValue(json, Withdrawal.class);
        assertNull(withdrawal.getReason());
        
        json = "{}";
        withdrawal = BridgeObjectMapper.get().readValue(json, Withdrawal.class);
        assertNull(withdrawal.getReason());
    }
}
