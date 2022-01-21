package org.sagebionetworks.bridge.models.apps;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.testng.annotations.Test;

public class Exporter3ConfigurationTest {
    @Test
    public void isConfigured() {
        // Make one that is configured.
        Exporter3Configuration config = new Exporter3Configuration();
        config.setDataAccessTeamId(1L);
        config.setParticipantVersionTableId("test-table-id");
        config.setProjectId("test-project-id");
        config.setRawDataFolderId("test-folder-id");
        config.setStorageLocationId(2L);
        assertTrue(config.isConfigured());

        // Null data access team.
        config.setDataAccessTeamId(null);
        assertFalse(config.isConfigured());
        config.setDataAccessTeamId(1L);
        assertTrue(config.isConfigured());

        // Null participant version table ID.
        config.setParticipantVersionTableId(null);
        assertFalse(config.isConfigured());
        config.setParticipantVersionTableId("test-table-id");
        assertTrue(config.isConfigured());

        // Null project ID.
        config.setProjectId(null);
        assertFalse(config.isConfigured());
        config.setProjectId("test-project-id");
        assertTrue(config.isConfigured());

        // Null raw data folder ID.
        config.setRawDataFolderId(null);
        assertFalse(config.isConfigured());
        config.setRawDataFolderId("test-folder-id");
        assertTrue(config.isConfigured());

        // Null storage location ID.
        config.setStorageLocationId(null);
        assertFalse(config.isConfigured());
        config.setStorageLocationId(2L);
        assertTrue(config.isConfigured());
    }

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Exporter3Configuration.class).allFieldsShouldBeUsed()
                .suppress(Warning.NONFINAL_FIELDS).verify();
    }
}
