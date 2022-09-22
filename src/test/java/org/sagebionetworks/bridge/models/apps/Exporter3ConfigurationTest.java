package org.sagebionetworks.bridge.models.apps;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class Exporter3ConfigurationTest {
    @Test
    public void isConfigured() {
        // Make one that is configured.
        Exporter3Configuration config = new Exporter3Configuration();
        config.setDataAccessTeamId(1L);
        config.setParticipantVersionTableId("test-table-id");
        config.setParticipantVersionDemographicsTableId("test-table-id");
        config.setParticipantVersionDemographicsViewId("test-view-id");
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

        // Null participant version demographics table ID.
        config.setParticipantVersionDemographicsTableId(null);
        assertFalse(config.isConfigured());
        config.setParticipantVersionDemographicsTableId("test-table-id");
        assertTrue(config.isConfigured());

        // Null participant version demographics view ID.
        config.setParticipantVersionDemographicsViewId(null);
        assertFalse(config.isConfigured());
        config.setParticipantVersionDemographicsViewId("test-view-id");
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
    public void serialize() throws Exception {
        // Make POJO.
        Exporter3Configuration config = new Exporter3Configuration();
        config.setCreateStudyNotificationTopicArn("arn:1111:test-topic");
        config.setDataAccessTeamId(1L);
        config.setParticipantVersionTableId("test-table-id");
        config.setParticipantVersionDemographicsTableId("test-table-id");
        config.setParticipantVersionDemographicsViewId("test-view-id");
        config.setProjectId("test-project-id");
        config.setRawDataFolderId("test-folder-id");
        config.setStorageLocationId(2L);

        // Convert to JsonNode.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(config, JsonNode.class);
        assertEquals(jsonNode.size(), 10);
        assertTrue(jsonNode.get("configured").booleanValue());
        assertEquals(jsonNode.get("createStudyNotificationTopicArn").textValue(), "arn:1111:test-topic");
        assertEquals(jsonNode.get("dataAccessTeamId").intValue(), 1);
        assertEquals(jsonNode.get("participantVersionTableId").textValue(), "test-table-id");
        assertEquals(jsonNode.get("participantVersionDemographicsTableId").textValue(), "test-table-id");
        assertEquals(jsonNode.get("participantVersionDemographicsViewId").textValue(), "test-view-id");
        assertEquals(jsonNode.get("projectId").textValue(), "test-project-id");
        assertEquals(jsonNode.get("rawDataFolderId").textValue(), "test-folder-id");
        assertEquals(jsonNode.get("storageLocationId").intValue(), 2);
        assertEquals(jsonNode.get("type").textValue(), "Exporter3Configuration");

        // Convert back to POJO.
        Exporter3Configuration converted = BridgeObjectMapper.get().treeToValue(jsonNode,
                Exporter3Configuration.class);
        assertEquals(converted, config);
    }

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Exporter3Configuration.class).allFieldsShouldBeUsed()
                .suppress(Warning.NONFINAL_FIELDS).verify();
    }
}
