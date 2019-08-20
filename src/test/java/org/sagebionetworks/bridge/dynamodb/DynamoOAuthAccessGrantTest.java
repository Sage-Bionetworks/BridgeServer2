package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

public class DynamoOAuthAccessGrantTest {
    @Test
    public void scopesIsNeverNull() {
        // Initializes to empty.
        DynamoOAuthAccessGrant grant = new DynamoOAuthAccessGrant();
        assertTrue(grant.getScopes().isEmpty());

        // Temporarily set to non-empty.
        List<String> scopes = ImmutableList.of("test");
        grant.setScopes(scopes);
        assertEquals(grant.getScopes(), scopes);

        // Set to null, get empty list.
        grant.setScopes(null);
        assertTrue(grant.getScopes().isEmpty());
    }
}
