package org.sagebionetworks.bridge.models.accounts;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SharingOptionTest {

    @Test
    public void sharingOptionUsesCorrectDefaults() {

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        // These JSON strings are the expected API to the client
        // A test failure wound indicate broken APIs
        node.put("scope", "sponsors_and_partners");

        SharingOption option = SharingOption.fromJson(node, 1);
        assertEquals(option.getSharingScope(), SharingScope.NO_SHARING);

        option = SharingOption.fromJson(node, 2);
        assertEquals(option.getSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);

        try {
            node = JsonNodeFactory.instance.objectNode();
            option = SharingOption.fromJson(node, 2);
            fail("Should have thrown an invalid entity exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("scope is required"));
        }

        node = JsonNodeFactory.instance.objectNode();
        // The following JSON strings are the expected API to the client
        // A test failure wound indicate broken APIs
        node.put("scope", "all_qualified_researchers");
        option = SharingOption.fromJson(node, 2);
        assertEquals(option.getSharingScope(), SharingScope.ALL_QUALIFIED_RESEARCHERS);
    }

    @Test
    public void sharingOptionFailsGracefully() {
        SharingOption option = SharingOption.fromJson(null, 1);
        assertEquals(option.getSharingScope(), SharingScope.NO_SHARING);
        
        try {
            option = SharingOption.fromJson(null, 11);
            fail("Should have thrown an invalid entity exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("scope is required"));
        }        
    }
    
}
