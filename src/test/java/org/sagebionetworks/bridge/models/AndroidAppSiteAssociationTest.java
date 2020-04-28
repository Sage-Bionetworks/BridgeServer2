package org.sagebionetworks.bridge.models;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.apps.AndroidAppLink;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

public class AndroidAppSiteAssociationTest {
    
    @Test
    public void producesCorrectJson() throws Exception {
        AndroidAppLink link = new AndroidAppLink("namespace", "package", Lists.newArrayList("fingerprint"));
        
        AndroidAppSiteAssociation assoc = new AndroidAppSiteAssociation(link);
        
        JsonNode node = new ObjectMapper().valueToTree(assoc);
        assertEquals(node.get("relation").get(0).textValue(), AndroidAppSiteAssociation.RELATION);
        JsonNode target = node.get("target");
        assertEquals(target.get("namespace").textValue(), "namespace");
        assertEquals(target.get("package_name").textValue(), "package");
        assertEquals(target.get("sha256_cert_fingerprints").get(0).textValue(), "fingerprint");
    }
    
}
