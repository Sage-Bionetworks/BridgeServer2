package org.sagebionetworks.bridge.models.surveys;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class PostalCodeConstraintsTest {

    @Test
    public void canSerialize() throws Exception {
        PostalCodeConstraints pcc = new PostalCodeConstraints();
        pcc.setCountryCode(CountryCode.US);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(pcc);
        assertEquals(node.get("dataType").textValue(), "postalcode");
        assertEquals(node.get("countryCode").textValue(), "us");
        assertEquals(node.get("type").textValue(), "PostalCodeConstraints");
        int len = BridgeConstants.SPARSE_ZIP_CODE_PREFIXES.size();
        assertEquals(node.get("sparseZipCodePrefixes").size(), len);
        for (int i=0; i < len; i++) {
            String zipCodePrefix = BridgeConstants.SPARSE_ZIP_CODE_PREFIXES.get(i);
            assertEquals(node.get("sparseZipCodePrefixes").get(i).textValue(), zipCodePrefix);
        }
        
        // Verify that the mapper correctly selects the subclass we want.
        PostalCodeConstraints deser = (PostalCodeConstraints) BridgeObjectMapper
                .get().readValue(node.toString(), Constraints.class);
        assertEquals(deser.getCountryCode(), CountryCode.US);
    }
}
