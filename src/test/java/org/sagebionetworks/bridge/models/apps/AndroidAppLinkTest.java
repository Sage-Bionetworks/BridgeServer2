package org.sagebionetworks.bridge.models.apps;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.apps.AndroidAppLink;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AndroidAppLinkTest {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AndroidAppLink.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        AndroidAppLink link = new AndroidAppLink("namespace", "packageName", Lists.newArrayList("fingerprint"));

        JsonNode node = MAPPER.valueToTree(link);
        assertEquals(node.size(), 3);
        assertEquals(node.get("namespace").textValue(), "namespace");
        assertEquals(node.get("package_name").textValue(), "packageName");
        assertEquals(node.get("sha256_cert_fingerprints").get(0).textValue(), "fingerprint");
        
        AndroidAppLink deser = MAPPER.readValue(node.toString(), AndroidAppLink.class);
        assertEquals(deser.getNamespace(), "namespace");
        assertEquals(deser.getPackageName(), "packageName");
        assertEquals(deser.getFingerprints().size(), 1);
        assertEquals(deser.getFingerprints().get(0), "fingerprint");
    }
    
}
