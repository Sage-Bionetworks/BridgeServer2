package org.sagebionetworks.bridge.models.apps;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.apps.SynapseProjectIdTeamIdHolder;

public class SynapseProjectIdTeamIdHolderTest {
    private static final String TEST_PROJECT_ID = "test-project-id";
    private static final Long TEST_TEAM_ID = Long.parseLong("1234");

    @Test
    public void serializesCorrectly() throws Exception {
        SynapseProjectIdTeamIdHolder holder = new SynapseProjectIdTeamIdHolder(TEST_PROJECT_ID, TEST_TEAM_ID);

        String synapseIds = BridgeObjectMapper.get().writeValueAsString(holder);
        JsonNode synapse = BridgeObjectMapper.get().readTree(synapseIds);

        assertEquals(synapse.get("projectId").asText(), TEST_PROJECT_ID);
        assertEquals(synapse.get("teamId").asLong(), TEST_TEAM_ID.longValue());
    }
}
